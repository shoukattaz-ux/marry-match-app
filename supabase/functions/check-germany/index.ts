import { createClient } from "jsr:@supabase/supabase-js@2";
import { inGermany } from "./geo.ts";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });

// A sign-up pass is valid for 15 minutes and can be used for exactly one new account.
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

  let body: { lat?: unknown; lng?: unknown; mock?: unknown };
  try { body = await req.json(); } catch { return json({ error: "bad_request" }, 400); }
  const lat = Number(body.lat), lng = Number(body.lng);
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return json({ error: "bad_request" }, 400);

  // Only a status comes back. Coordinates are never stored and never returned.
  const mock = body.mock === true;
  const inside = !mock && inGermany(lat, lng);
  const ipCountry = (req.headers.get("cf-ipcountry") ?? "").toUpperCase();
  const ipMismatch = inside && ipCountry !== "" && !["DE", "XX", "T1"].includes(ipCountry);

  const result: Record<string, unknown> = inside
    ? { allowed: true }
    : { allowed: false, message: "MarryMatch is not available in your area yet." };

  const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
    auth: { persistSession: false },
  });

  const token = (req.headers.get("authorization") ?? "").replace(/^Bearer\s+/i, "");
  const { data: authed } = token ? await admin.auth.getUser(token) : { data: { user: null } };

  if (authed?.user) {
    // Signed-in callers get their server-side flag updated.
    const patch: Record<string, unknown> = { country_ok: inside, geo_checked_at: new Date().toISOString() };
    if (ipMismatch || mock) patch.needs_review = true;
    await admin.from("user_status").update(patch).eq("user_id", authed.user.id);
  } else if (inside) {
    // Anonymous callers (before sign-up) get a one-time pass. The sign-up trigger
    // refuses any new account without a valid, unused pass.
    const pass = newPassToken();
    const { error } = await admin.from("signup_passes").insert({
      token_hash: await sha256Hex(pass),
      needs_review: ipMismatch,
      expires_at: new Date(Date.now() + PASS_TTL_MS).toISOString(),
    });
    if (error) return json({ error: "server_error" }, 500);
    result.signup_pass = pass;
    await admin.from("signup_passes").delete().lt("expires_at", new Date(Date.now() - 24 * 3600 * 1000).toISOString());
  }
  return json(result);
});
