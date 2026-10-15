import { createClient } from "jsr:@supabase/supabase-js@2";

// Moderator decision on a selfie verification (blueprint Phase 4).
// Only accounts with user_status.is_admin may call it. On approve the user gets the blue tick;
// in both cases the selfie file is deleted and only the result is kept. Every decision is
// written to audit_log.

const REJECT_REASONS = ["face_not_clear", "pose_mismatch", "not_same_person", "other"];

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

  const { data: status } = await admin.from("user_status").select("is_admin").eq("user_id", moderator.id).single();
  if (!status?.is_admin) return json({ error: "not_allowed" }, 403);

  let body: { request_id?: string; approve?: boolean; reason?: string };
  try { body = await req.json(); } catch { return json({ error: "bad_request" }, 400); }
  const approve = body.approve === true;
  const reason = approve ? null : body.reason;
  if (!body.request_id || (!approve && !REJECT_REASONS.includes(reason ?? ""))) {
    return json({ error: "bad_request" }, 400);
  }

  // Decide only once: the status must still be pending.
  const { data: request, error } = await admin
    .from("verification_requests")
    .update({
      status: approve ? "approved" : "rejected",
      reviewed_by: moderator.id,
      reviewed_at: new Date().toISOString(),
      reject_reason: reason,
      storage_path: null,
    })
    .eq("id", body.request_id)
    .eq("status", "pending")
    .not("submitted_at", "is", null)
    .select("user_id")
    .single();
  if (error || !request) return json({ error: "not_found_or_already_reviewed" }, 409);

  const selfiePath = `${request.user_id}/${body.request_id}.jpg`;

  if (approve) {
    await admin.from("user_status").update({ selfie_verified: true }).eq("user_id", request.user_id);
  }

  // The selfie is only kept until the decision (privacy promise and GDPR Art. 9).
  await admin.storage.from("selfies").remove([selfiePath]);

  await admin.from("audit_log").insert({
    actor_id: moderator.id,
    action: approve ? "selfie_approved" : "selfie_rejected",
    target_user: request.user_id,
    details: { request_id: body.request_id, reason },
  });

  return json({ ok: true });
});
