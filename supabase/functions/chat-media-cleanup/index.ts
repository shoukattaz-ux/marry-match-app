import { createClient } from "jsr:@supabase/supabase-js@2";

// Daily chat media cleanup (migration 023), called by pg_cron with the x-push-secret header.
//   expire:   never downloaded within 14 days -> remove the files, mark the message 'expired'
//   leftover: downloaded or expired, but the file is still on the server -> remove it
//   orphan:   a file without a message after a day (cancelled or failed send) -> remove it
// Files of messages with an open report are never in the plan. Paths are not logged.

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

const BUCKET = "chat-media";
const BATCH = 100;

type Plan = { expire: { id: string; paths: string[] }[]; leftover: string[]; orphan: string[] };

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
    auth: { persistSession: false },
  });
  const { data: ok } = await admin.rpc("push_secret_ok", { p_secret: req.headers.get("x-push-secret") ?? "" });
  if (ok !== true) return json({ error: "unauthorized" }, 401);

  const { data, error } = await admin.rpc("chat_media_cleanup_plan", { p_limit: 500 });
  if (error) return json({ error: "plan_failed" }, 500);
  const plan = data as Plan;

  let removed = 0;
  let failed = 0;
  const remove = async (paths: string[]): Promise<boolean> => {
    let allOk = true;
    for (let i = 0; i < paths.length; i += BATCH) {
      const part = paths.slice(i, i + BATCH);
      const { error: removeError } = await admin.storage.from(BUCKET).remove(part);
      if (removeError) {
        failed += part.length;
        allOk = false;
      } else {
        removed += part.length;
      }
    }
    return allOk;
  };

  // Expire only messages whose files were really removed; the rest is tried again tomorrow.
  const expiredIds: string[] = [];
  for (const item of plan.expire ?? []) {
    if (await remove(item.paths)) expiredIds.push(item.id);
  }
  let expired = 0;
  if (expiredIds.length > 0) {
    const { data: count } = await admin.rpc("chat_media_mark_expired", { p_ids: expiredIds });
    expired = (count as number) ?? 0;
  }
  await remove(plan.leftover ?? []);
  await remove(plan.orphan ?? []);

  return json({ ok: true, expired, removed, failed });
});
