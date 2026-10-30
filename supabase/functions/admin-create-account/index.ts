import { createClient } from "jsr:@supabase/supabase-js@2";

// An admin creates a complete account for someone who asked for it (admin panel → Create).
// POST { email, password, lang, terms_version, privacy_version, conduct_version, sensitive_version?,
//        profile: { display_name, birth_date, gender, city, region, ... }, photos: [{ jpeg, thumb?, blurred?, focus_x, focus_y, zoom }] }
// (photos as base64). Only admins may call it. The login works at once: the e-mail counts as
// confirmed and no e-mail is sent. The sign-up checks of handle_new_user are skipped (gate_bypass),
// so the pause for new men does not apply. The admin confirms the person lives in Germany, so the
// account counts as located (country_ok): the profile is in Discover at once and the person is
// not asked for the location check.
// Terms and privacy are recorded as accepted on the person's behalf by the admin, and the whole
// creation is in the audit log. If any step fails, the half-made account is removed again.

const BUCKET = "profile-photos";
const MAX_PER_DAY = 20;
const MIN_PHOTOS = 3; // like the normal sign-up: a finished profile needs 3 photos (trg_onboarding_done)
const MAX_PHOTOS = 6;
const MAX_PHOTO_BYTES = 3 * 1024 * 1024;
const GENDERS = ["woman", "man"];
const TEXT_FIELDS: Record<string, number> = { religion: 60, education: 80, occupation: 80, bio: 500 };
const CHOICES: Record<string, string[]> = {
  marriage_timeline: ["within_1y", "1_2y", "2_5y", "open"],
  wants_children: ["yes", "no", "open"],
  smoking: ["non_smoker", "occasionally", "smoker"],
  drinking: ["never", "socially", "regularly"],
  exercise: ["rarely", "sometimes", "often", "daily"],
  diet: ["anything", "vegetarian", "vegan", "pescatarian"],
  pets: ["have_pets", "like_pets", "no_pets", "allergic"],
  sleep: ["early_bird", "night_owl", "flexible"],
  personality: ["introvert", "ambivert", "extrovert"],
  family_closeness: ["very_close", "close", "independent"],
  household: ["traditional", "shared", "flexible"],
  home_after_marriage: ["city", "countryside", "flexible"],
  relocate: ["yes", "maybe", "no"],
};

type Photo = { jpeg?: string; thumb?: string; blurred?: string; focus_x?: number; focus_y?: number; zoom?: number };
type CreateBody = {
  email?: string; password?: string; lang?: string;
  terms_version?: string; privacy_version?: string; conduct_version?: string; sensitive_version?: string;
  profile?: Record<string, unknown>; photos?: Photo[];
};

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

const fail = (error: string, status = 400) => json({ error }, status);

function bytes(b64: string | undefined): Uint8Array | null {
  if (!b64) return null;
  try {
    const bin = atob(b64);
    const out = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
    return out;
  } catch {
    return null;
  }
}
const isJpeg = (b: Uint8Array | null) => !!b && b.length > 3 && b[0] === 0xff && b[1] === 0xd8;

function adult(birth: string): boolean | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(birth)) return null;
  const d = new Date(birth + "T00:00:00Z");
  if (isNaN(d.getTime())) return null;
  const now = new Date();
  const eighteen = new Date(Date.UTC(now.getUTCFullYear() - 18, now.getUTCMonth(), now.getUTCDate()));
  const hundred = new Date(Date.UTC(now.getUTCFullYear() - 100, now.getUTCMonth(), now.getUTCDate()));
  if (d < hundred) return null;
  return d <= eighteen;
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
  const { data: status } = await admin.from("user_status").select("is_admin, is_banned").eq("user_id", actor.id).single();
  if (!status?.is_admin || status.is_banned) return fail("not_allowed", 403);

  const since = new Date(Date.now() - 24 * 3600 * 1000).toISOString();
  const { count } = await admin.from("audit_log").select("id", { count: "exact", head: true })
    .eq("actor_id", actor.id).eq("action", "account_created").gte("created_at", since);
  if ((count ?? 0) >= MAX_PER_DAY) return fail("too_many", 429);

  let body: CreateBody;
  try { body = await req.json(); } catch { return fail("bad_request"); }

  // --- Check everything before anything is created ---
  const email = String(body.email ?? "").trim().toLowerCase();
  const password = String(body.password ?? "");
  const p = body.profile ?? {};
  const name = String(p.display_name ?? "").trim();
  const gender = String(p.gender ?? "");
  const birth = String(p.birth_date ?? "");
  const city = String(p.city ?? "").trim();
  const region = String(p.region ?? "").trim();
  if (!/^[a-z0-9._%+\-]+@[a-z0-9.\-]+\.[a-z]{2,}$/.test(email)) return fail("invalid_email");
  if (password.length < 8 || password.length > 72) return fail("weak_password");
  if (name.length < 1 || name.length > 40) return fail("invalid_name");
  if (!GENDERS.includes(gender)) return fail("invalid_gender");
  const isAdult = adult(birth);
  if (isAdult === null) return fail("invalid_birth_date");
  if (!isAdult) return fail("underage");
  if (!city || !region) return fail("invalid_city");
  if (!body.terms_version || !body.privacy_version || !body.conduct_version) return fail("consent_missing");

  const row: Record<string, unknown> = {
    display_name: name, gender, looking_for: gender === "woman" ? "man" : "woman", birth_date: birth, city, region,
    // Marked finished after the photos are in (the database checks there are 3).
    onboarding_done: false, onboarding_step: 13,
  };
  if (p.height_cm != null) {
    const h = Number(p.height_cm);
    if (!Number.isInteger(h) || h < 120 || h > 230) return fail("invalid_height");
    row.height_cm = h;
  }
  for (const [key, max] of Object.entries(TEXT_FIELDS)) {
    const v = String(p[key] ?? "").trim();
    if (v.length > max) return fail("too_long_" + key);
    if (v) row[key] = v;
  }
  for (const [key, options] of Object.entries(CHOICES)) {
    const v = p[key];
    if (v == null || v === "") continue;
    if (!options.includes(String(v))) return fail("invalid_" + key);
    row[key] = v;
  }
  for (const key of ["languages", "interests"]) {
    const v = p[key];
    if (v == null) continue;
    if (!Array.isArray(v) || v.some((x) => typeof x !== "string") || v.length > 10) return fail("invalid_" + key);
    row[key] = v;
  }

  const photos = (body.photos ?? []).map((ph) => ({
    jpeg: bytes(ph.jpeg), thumb: bytes(ph.thumb), blurred: bytes(ph.blurred),
    focus_x: Number(ph.focus_x ?? 0.5), focus_y: Number(ph.focus_y ?? 0.5), zoom: Number(ph.zoom ?? 1),
  }));
  if (photos.length < MIN_PHOTOS || photos.length > MAX_PHOTOS) return fail("photos_count");
  for (const ph of photos) {
    if (!isJpeg(ph.jpeg) || ph.jpeg!.length > MAX_PHOTO_BYTES) return fail("invalid_photo");
    if (ph.thumb && !isJpeg(ph.thumb)) ph.thumb = null;
    if (ph.blurred && !isJpeg(ph.blurred)) ph.blurred = null;
  }

  // --- Create ---
  const { data: created, error: createError } = await admin.auth.admin.createUser({
    email,
    password,
    email_confirm: true,
    user_metadata: { lang: body.lang === "en" ? "en" : "de", display_name: name },
    app_metadata: { gate_bypass: true, created_by_admin: actor.id },
  });
  if (createError || !created?.user) {
    const msg = (createError?.message ?? "").toLowerCase();
    if (msg.includes("already") || (createError as { code?: string })?.code === "email_exists") return fail("email_exists", 409);
    if (msg.includes("password")) return fail("weak_password");
    return fail("create_failed", 500);
  }
  const userId = created.user.id;
  const uploaded: string[] = [];

  try {
    const { error: profileError } = await admin.from("profiles").insert({ id: userId, ...row });
    if (profileError) throw new Error("profile: " + profileError.message);

    for (let i = 0; i < photos.length; i++) {
      const ph = photos[i];
      const path = `${userId}/${crypto.randomUUID()}.jpg`;
      const up = await admin.storage.from(BUCKET).upload(path, ph.jpeg!, { contentType: "image/jpeg", upsert: false });
      if (up.error) throw new Error("photo: " + up.error.message);
      uploaded.push(path);
      for (const [copy, data] of [[path.replace(/\.jpg$/, "_t.jpg"), ph.thumb], [path.replace(/\.jpg$/, "_b.jpg"), ph.blurred]] as const) {
        if (!data) continue;
        const c = await admin.storage.from(BUCKET).upload(copy, data, { contentType: "image/jpeg", upsert: false });
        if (!c.error) uploaded.push(copy);
      }
      const { error: rowError } = await admin.from("profile_photos").insert({
        user_id: userId, storage_path: path, position: i,
        focus_x: Math.min(1, Math.max(0, ph.focus_x)), focus_y: Math.min(1, Math.max(0, ph.focus_y)),
        zoom: Math.min(3, Math.max(1, ph.zoom)),
      });
      if (rowError) throw new Error("photo row: " + rowError.message);
    }

    const { error: doneError } = await admin.from("profiles").update({ onboarding_done: true }).eq("id", userId);
    if (doneError) throw new Error("finish: " + doneError.message);

    // Located by the admin's confirmation: Discover only shows located accounts.
    const { error: statusError } = await admin.from("user_status")
      .update({ country_ok: true, geo_checked_at: new Date().toISOString() }).eq("user_id", userId);
    if (statusError) throw new Error("status: " + statusError.message);

    // Accepted on the person's behalf when the admin tapped Create.
    const consents = [
      { user_id: userId, consent_type: "terms", version: body.terms_version },
      { user_id: userId, consent_type: "privacy", version: body.privacy_version },
      { user_id: userId, consent_type: "conduct", version: body.conduct_version },
    ];
    if (row.religion && body.sensitive_version) {
      consents.push({ user_id: userId, consent_type: "sensitive_profile", version: body.sensitive_version });
    }
    const { error: consentError } = await admin.from("consents").insert(consents);
    if (consentError) throw new Error("consents: " + consentError.message);

    await admin.from("audit_log").insert({
      actor_id: actor.id, action: "account_created", target_user: userId,
      details: { email, photos: photos.length, consent: "accepted_by_admin_on_request", located_by: "admin" },
    });
  } catch (e) {
    // Remove the half-made account so the same e-mail can be used again.
    if (uploaded.length) await admin.storage.from(BUCKET).remove(uploaded);
    await admin.auth.admin.deleteUser(userId);
    console.error("admin-create-account rolled back", String((e as Error).message ?? e));
    return fail("create_failed", 500);
  }

  return json({ ok: true, user_id: userId, email });
});
