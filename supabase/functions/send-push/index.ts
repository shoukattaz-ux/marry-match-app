import { createClient } from "jsr:@supabase/supabase-js@2";

// Sends one push through Firebase Cloud Messaging (HTTP v1).
// Called only by database triggers (migration 013b) with the x-push-secret header, never by the
// app. POST { user_id, kind: "match" | "message" | "like" | "your_turn" | "expiring", data: { match_id?, name? } }.
// The Firebase key comes from the FIREBASE_SERVICE_ACCOUNT_JSON secret and never leaves here.
// Pushes never contain message text: only who wrote, so nothing private shows on a lock screen.

type Kind = "match" | "message" | "like" | "your_turn" | "expiring";
type Lang = "en" | "de";

const TEXTS: Record<Kind, Record<Lang, (name: string) => { title: string; body: string }>> = {
  match: {
    en: (n) => ({ title: "It's a match!", body: `You and ${n} like each other. Say hello!` }),
    de: (n) => ({ title: "Es ist ein Match!", body: `Sie und ${n} mögen sich. Sagen Sie Hallo!` }),
  },
  message: {
    en: (n) => ({ title: "New message", body: `${n} sent you a message.` }),
    de: (n) => ({ title: "Neue Nachricht", body: `${n} hat Ihnen geschrieben.` }),
  },
  like: {
    en: () => ({ title: "Someone likes you", body: "Open MarryMatch to see who." }),
    de: () => ({ title: "Jemand mag Sie", body: "Öffnen Sie MarryMatch, um zu sehen, wer." }),
  },
  // Daily reminders (migration 017).
  your_turn: {
    en: (n) => ({ title: "Your turn", body: `${n} is waiting for your answer.` }),
    de: (n) => ({ title: "Sie sind dran", body: `${n} wartet auf Ihre Antwort.` }),
  },
  expiring: {
    en: (n) => ({ title: "Your match expires tomorrow", body: `Say hello to ${n} before it's too late.` }),
    de: (n) => ({ title: "Ihr Match läuft morgen ab", body: `Sagen Sie ${n} Hallo, bevor es zu spät ist.` }),
  },
};
const SOMEONE: Record<Lang, string> = { en: "Someone", de: "Jemand" };

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

type ServiceAccount = { client_email: string; private_key: string; project_id: string; token_uri?: string };

// Google access tokens last one hour; keep one per warm function instance.
let cachedToken: { value: string; expiresAt: number } | null = null;

const b64url = (data: Uint8Array | string) => {
  const bytes = typeof data === "string" ? new TextEncoder().encode(data) : data;
  let s = "";
  bytes.forEach((b) => (s += String.fromCharCode(b)));
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
};

async function accessToken(sa: ServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (cachedToken && cachedToken.expiresAt - 60 > now) return cachedToken.value;

  const tokenUri = sa.token_uri ?? "https://oauth2.googleapis.com/token";
  const header = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = b64url(JSON.stringify({
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: tokenUri,
    iat: now,
    exp: now + 3600,
  }));
  const pem = sa.private_key.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const der = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey(
    "pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"],
  );
  const signature = new Uint8Array(
    await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(`${header}.${claims}`)),
  );
  const res = await fetch(tokenUri, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: `${header}.${claims}.${b64url(signature)}`,
    }),
  });
  if (!res.ok) throw new Error(`google_token_${res.status}`);
  const body = await res.json();
  cachedToken = { value: body.access_token, expiresAt: now + (body.expires_in ?? 3600) };
  return cachedToken.value;
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
    auth: { persistSession: false },
  });

  const secret = req.headers.get("x-push-secret") ?? "";
  const { data: ok } = await admin.rpc("push_secret_ok", { p_secret: secret });
  if (ok !== true) return json({ error: "unauthorized" }, 401);

  // Distinct errors so a wrong secret is easy to spot in net._http_response (never the key itself).
  const raw = Deno.env.get("FIREBASE_SERVICE_ACCOUNT_JSON");
  if (!raw) return json({ error: "push_secret_missing" }, 503);
  let sa: ServiceAccount;
  try { sa = JSON.parse(raw); } catch { return json({ error: "push_secret_not_json" }, 503); }
  if (!sa?.client_email || !sa?.private_key || !sa?.project_id) return json({ error: "push_secret_incomplete" }, 503);

  let body: { user_id?: string; kind?: Kind; data?: { match_id?: string; name?: string } };
  try { body = await req.json(); } catch { return json({ error: "bad_request" }, 400); }
  const kind = body.kind;
  if (!body.user_id || !kind || !(kind in TEXTS)) return json({ error: "bad_request" }, 400);

  const { data: tokens } = await admin.from("push_tokens").select("token, lang").eq("user_id", body.user_id);
  if (!tokens || tokens.length === 0) return json({ ok: true, sent: 0 });

  let bearer: string;
  try { bearer = await accessToken(sa); } catch (e) { return json({ error: String((e as Error).message ?? e) }, 502); }
  const matchId = body.data?.match_id ?? "";
  let sent = 0;
  const failed: number[] = [];  // FCM status codes, for net._http_response
  for (const t of tokens) {
    const lang: Lang = t.lang === "de" ? "de" : "en";
    const name = (body.data?.name ?? "").trim() || SOMEONE[lang];
    const { title, body: text } = TEXTS[kind][lang](name);
    const res = await fetch(`https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${bearer}` },
      body: JSON.stringify({
        message: {
          token: t.token,
          notification: { title, body: text },
          data: { kind, match_id: matchId },
          android: {
            priority: "high",
            // One notification per chat or kind: a new one replaces the old instead of piling up.
            notification: { channel_id: "marrymatch_default", tag: `${kind}_${matchId}` },
          },
        },
      }),
    });
    if (res.ok) {
      sent++;
      continue;
    }
    failed.push(res.status);
    if (res.status === 404 || res.status === 400) {
      // The app was uninstalled or the token is no longer valid: forget it.
      const err = await res.text();
      if (err.includes("UNREGISTERED") || err.includes("INVALID_ARGUMENT") || res.status === 404) {
        await admin.from("push_tokens").delete().eq("token", t.token);
      }
    }
  }
  return json({ ok: true, sent, failed });
});
