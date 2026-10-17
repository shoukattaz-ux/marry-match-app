import { createClient } from "jsr:@supabase/supabase-js@2";

// The admin switch for the Germany location check (migration 024, Admin > Overview).
//   POST { probe: true }  -> { required: boolean }   is the location check on?
//   POST { skip: true }   -> { allowed: true, signup_pass? }   only while the check is OFF
// The setting is read here on every call and the check is treated as ON whenever it cannot be
// read, so the app cannot skip the check by itself. While the check is ON, "skip" is refused and
// the app runs the normal GPS check (check-germany). Accounts and passes created while the check
// is off are marked for moderator review. Coordinates are never involved.

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });

// Same rules as check-germany: valid 15 minutes, exactly one new account.
const PASS_TTL_MS = 15 * 60 * 1000;

async function sha256Hex(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest)).map((b) => b.toString(16).padStart(2, "0")).join("");
}

function newPassToken(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(32));
  return btoa(String.fromCharCode(...bytes)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  let body: { probe?: unknown; skip?: unknown };
  try { body = await req.json(); } catch { return json({ error: "bad_request" }, 400); }

  const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
    auth: { persistSession: false },
  });

  // Fail closed: only an explicit JSON false switches the check off.
  const { data: setting, error } = await admin.from("app_settings")
    .select("value").eq("key", "location_check_enabled").maybeSingle();
  const required = error ? true : setting?.value !== false;

  if (body.probe === true) return json({ required });
  if (body.skip !== true) return json({ error: "bad_request" }, 400);
  if (required) return json({ allowed: false, required: true });

  const token = (req.headers.get("authorization") ?? "").replace(/^Bearer\s+/i, "");
  const { data: authed } = token ? await admin.auth.getUser(token) : { data: { user: null } };

  if (authed?.user) {
    // Signed-in user (first login after sign-up, or an older account): mark the country as checked.
    const { data: status } = await admin.from("user_status").select("country_ok").eq("user_id", authed.user.id).maybeSingle();
    const patch: Record<string, unknown> = { country_ok: true, geo_checked_at: new Date().toISOString() };
    if (!status?.country_ok) patch.needs_review = true;
    await admin.from("user_status").update(patch).eq("user_id", authed.user.id);
    return json({ allowed: true });
  }

  // Before sign-up: a one-time pass, marked for review so these accounts can be found later.
  const pass = newPassToken();
  const { error: insertError } = await admin.from("signup_passes").insert({
    token_hash: await sha256Hex(pass),
    needs_review: true,
    expires_at: new Date(Date.now() + PASS_TTL_MS).toISOString(),
  });
  if (insertError) return json({ error: "server_error" }, 500);
  return json({ allowed: true, signup_pass: pass });
});
