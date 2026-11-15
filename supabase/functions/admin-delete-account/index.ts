import { createClient } from "jsr:@supabase/supabase-js@2";

// The owner deletes a BANNED account for good, with everything (admin panel → user → Delete).
// POST { user_id, block_email: boolean, confirm: true }
// Removes the member's files (profile photos, selfies, admin message photos) and every chat file
// of their matches; deleting the Auth user then removes the profile, photos, answers, Signature
// Card, likes, passes, matches, chats, reports about them and consents (ON DELETE CASCADE).
// With block_email the address cannot sign up again (only its SHA-256 fingerprint is kept).
// The audit log keeps only that an account was deleted and by whom, no personal data.
// Only the owner may do this, only for banned accounts, never for admins.

const OWN_BUCKETS = ["profile-photos", "selfies", "support-media"];
const CHAT_BUCKET = "chat-media";

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
const fail = (error: string, status = 400) => json({ error }, status);

type DeleteBody = { user_id?: string; block_email?: boolean; confirm?: boolean };

// deno-lint-ignore no-explicit-any
async function removeFolder(admin: any, bucket: string, folder: string, depth = 0): Promise<boolean> {
  // Lists a folder page by page; sub-folders (chat files are match/message/file) are opened too.
  for (let round = 0; round < 50; round++) {
    const { data: entries, error } = await admin.storage.from(bucket).list(folder, { limit: 1000 });
    if (error) return false;
    if (!entries || entries.length === 0) return true;
    // deno-lint-ignore no-explicit-any
    const files = entries.filter((e: any) => e.id).map((e: any) => `${folder}/${e.name}`);
    // deno-lint-ignore no-explicit-any
    const folders = entries.filter((e: any) => !e.id).map((e: any) => `${folder}/${e.name}`);
    if (files.length) {
      const { error: removeError } = await admin.storage.from(bucket).remove(files);
      if (removeError) return false;
    }
    if (depth < 3) {
      for (const sub of folders) {
        if (!(await removeFolder(admin, bucket, sub, depth + 1))) return false;
      }
    }
    if (files.length === 0) return true;  // only empty sub-folders were left
  }
  return true;
}

async function sha256Hex(text: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, "0")).join("");
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return fail("method_not_allowed", 405);

  const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
    auth: { persistSession: false },
  });

  const token = (req.headers.get("authorization") ?? "").replace(/^Bearer\s+/i, "");
  const { data: auth } = await admin.auth.getUser(token);
  const actor = auth?.user;
  if (!actor) return fail("unauthorized", 401);
  const { data: me } = await admin.from("user_status").select("is_owner, is_banned").eq("user_id", actor.id).single();
  if (!me?.is_owner || me.is_banned) return fail("owner_only", 403);

  let body: DeleteBody;
  try { body = await req.json(); } catch { return fail("bad_request"); }
  if (body.confirm !== true) return fail("bad_request");
  const userId = String(body.user_id ?? "");
  if (!/^[0-9a-f-]{36}$/.test(userId)) return fail("bad_request");
  if (userId === actor.id) return fail("cannot_delete_self");

  const { data: target } = await admin.from("user_status").select("is_banned, is_admin, is_owner").eq("user_id", userId).single();
  if (!target) return fail("user_not_found", 404);
  if (target.is_admin || target.is_owner) return fail("cannot_delete_admin");
  if (!target.is_banned) return fail("not_banned");

  const { data: found, error: userError } = await admin.auth.admin.getUserById(userId);
  if (userError || !found?.user) return fail("user_not_found", 404);
  const email = (found.user.email ?? "").trim().toLowerCase();

  // Files first: once the user is gone, nothing points to them any more.
  for (const bucket of OWN_BUCKETS) {
    if (!(await removeFolder(admin, bucket, userId))) return fail("storage_failed", 500);
  }
  const { data: matches, error: matchError } = await admin.from("matches").select("id").or(`user_a.eq.${userId},user_b.eq.${userId}`);
  if (matchError) return fail("storage_failed", 500);
  for (const m of matches ?? []) {
    if (!(await removeFolder(admin, CHAT_BUCKET, m.id))) return fail("storage_failed", 500);
  }

  if (body.block_email === true && email) {
    const { error: blockError } = await admin.from("blocked_emails")
      .upsert({ email_hash: await sha256Hex(email), created_by: actor.id }, { onConflict: "email_hash" });
    if (blockError) return fail("block_failed", 500);
  }

  const { error: deleteError } = await admin.auth.admin.deleteUser(userId);
  if (deleteError) {
    console.error("admin-delete-account", deleteError.message);
    return fail("delete_failed", 500);
  }

  // No personal data in the log: only that an account was deleted, by whom, and whether blocked.
  await admin.from("audit_log").insert({
    actor_id: actor.id, action: "account_deleted_by_owner", target_user: userId,
    details: { email_blocked: body.block_email === true, matches: (matches ?? []).length },
  });
  return json({ ok: true });
});
