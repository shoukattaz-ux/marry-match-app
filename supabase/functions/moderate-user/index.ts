import { createClient } from "jsr:@supabase/supabase-js@2";

// Moderator actions on reports and accounts (blueprint Phase 9).
// POST { action: "dismiss" | "warn" | "ban" | "unban", user_id?, report_id?, note? }
// Only accounts with user_status.is_admin may call it. The database part (report status,
// respect score, is_banned, audit_log) runs in moderation_apply(); a ban also locks the login.

const ACTIONS = ["dismiss", "warn", "ban", "unban"];
// About 100 years: Supabase Auth has no permanent ban, only a duration.
const BAN_DURATION = "876000h";

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
    auth: { persistSession: false },
  });

  const token = (req.headers.get("authorization") ?? "").replace(/^Bearer\s+/i, "");
  const { data: auth } = await admin.auth.getUser(token);
  const moderator = auth?.user;
  if (!moderator) return json({ error: "unauthorized" }, 401);

  const { data: status } = await admin.from("user_status").select("is_admin, is_banned").eq("user_id", moderator.id).single();
  if (!status?.is_admin || status.is_banned) return json({ error: "not_allowed" }, 403);

  let body: { action?: string; user_id?: string; report_id?: string; note?: string };
  try { body = await req.json(); } catch { return json({ error: "bad_request" }, 400); }
  const action = body.action ?? "";
  if (!ACTIONS.includes(action)) return json({ error: "bad_request" }, 400);
  if ((action === "dismiss" || action === "warn") && !body.report_id) return json({ error: "bad_request" }, 400);
  if ((action === "ban" || action === "unban") && !body.user_id) return json({ error: "bad_request" }, 400);

  const { data: result, error } = await admin.rpc("moderation_apply", {
    p_actor: moderator.id,
    p_action: action,
    p_user: body.user_id ?? null,
    p_report: body.report_id ?? null,
    p_note: typeof body.note === "string" ? body.note.slice(0, 500) : null,
  });
  if (error) {
    const known = ["report_not_open", "user_not_found", "cannot_moderate_admin", "not_allowed"]
      .find((k) => error.message.includes(k));
    return json({ error: known ?? "failed" }, known ? 409 : 500);
  }

  const userId = (result as { user_id: string }).user_id;
  if (action === "ban" || action === "unban") {
    const { error: authError } = await admin.auth.admin.updateUserById(userId, {
      ban_duration: action === "ban" ? BAN_DURATION : "none",
    });
    // The database part already blocks a banned account; report the login lock separately.
    if (authError) return json({ ok: true, login_locked: false }, 200);
  }
  return json({ ok: true });
});
