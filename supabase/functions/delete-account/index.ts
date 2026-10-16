import { createClient } from "jsr:@supabase/supabase-js@2";

// Deletes the caller's own account for good (GDPR Art. 17).
// Photos and any selfie are removed from storage first; deleting the Auth user then removes the
// profile, matches, chats, likes and consents (ON DELETE CASCADE). Reports the user made stay
// without the reporter's id, so moderators can still act on them.

const BUCKETS = ["profile-photos", "selfies"];

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
    auth: { persistSession: false },
  });

  const token = (req.headers.get("authorization") ?? "").replace(/^Bearer\s+/i, "");
  const { data: auth } = await admin.auth.getUser(token);
  const user = auth?.user;
  if (!user) return json({ error: "unauthorized" }, 401);

  // The app asks the user to type a confirmation word; the request must say so explicitly.
  let body: { confirm?: boolean };
  try { body = await req.json(); } catch { return json({ error: "bad_request" }, 400); }
  if (body.confirm !== true) return json({ error: "bad_request" }, 400);

  for (const bucket of BUCKETS) {
    const { data: files, error } = await admin.storage.from(bucket).list(user.id, { limit: 1000 });
    if (error) return json({ error: "storage_failed" }, 500);
    if (files && files.length > 0) {
      const { error: removeError } = await admin.storage.from(bucket).remove(files.map((f) => `${user.id}/${f.name}`));
      if (removeError) return json({ error: "storage_failed" }, 500);
    }
  }

  // No personal data in the log entry: only that an account removed itself.
  await admin.from("audit_log").insert({ actor_id: user.id, action: "account_deleted", target_user: user.id, details: {} });

  const { error: deleteError } = await admin.auth.admin.deleteUser(user.id);
  if (deleteError) return json({ error: "delete_failed" }, 500);
  return json({ ok: true });
});
