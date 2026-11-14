-- Signature Card: a selfie-verified woman can add a designed card to her profile with three short
-- parts: "I'm looking for" (free text), "Why I'm here" (1-3 tags) and "My vibe" (emoji + phrase),
-- shown in one of four templates. It is shown only while she is verified and not banned: when the
-- verification is reset (new main photo), the card is hidden, not removed. Men can like the card
-- (with a comment), like a photo or an answer.
-- Writes only go through the card functions (part 2: 043b).

create table if not exists public.profile_cards (
  id uuid not null default gen_random_uuid() unique,
  user_id uuid primary key references auth.users(id) on delete cascade,
  template text not null check (template in ('midnight_glass','champagne_pearl','rose_velvet','emerald_noir')),
  looking_for text not null check (char_length(looking_for) between 1 and 120),
  reasons text[] not null check (cardinality(reasons) between 1 and 3),
  vibe_emoji text,
  vibe text check (char_length(vibe) <= 40),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
alter table public.profile_cards enable row level security;
-- Owners read their own card; everyone else gets it only through get_daily_set.
create policy "profile_cards: read own" on public.profile_cards for select to authenticated
  using (user_id = auth.uid());

-- Phone numbers, e-mail addresses, links and messenger names are not allowed on the card.
create or replace function private.has_contact_info(p text) returns boolean
language sql immutable set search_path = public as $$
  select coalesce(p, '') ~* '(https?://|www\.|[a-z0-9._%+-]+@[a-z0-9-]+\.[a-z]{2,}|\m[a-z0-9-]{2,}\.(com|de|net|org|io|me|info|eu|app)\M|whats ?app|telegram|insta(gram)?\M|snap(chat)?\M|tik ?tok|facebook|signal\M|@[a-z0-9_.]{3,})'
      -- seven or more digits in a row, spaces, dots, dashes and brackets allowed in between
      or coalesce(p, '') ~ '([0-9][ ()./-]?){7,}';
$$;

create or replace function private.card_reasons() returns text[]
language sql immutable as $$
  select array['marriage_soon','shared_faith','family_first','serious_only','friendship_first','partner_for_life',
               'start_family','shared_values','new_chapter','home_in_germany','family_blessing','honest_talk'];
$$;

create or replace function private.card_emojis() returns text[]
language sql immutable as $$
  select array['☕','🌙','🌸','📚','🌿','✨','🎨','🍲','🏔️','🌊','🎶','🕯️'];
$$;

-- Whether this member may have a card shown: a woman, selfie-verified, not banned.
create or replace function private.card_unlocked(p_user uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.profiles p join public.user_status s on s.user_id = p.id
     where p.id = p_user and p.gender = 'woman' and s.selfie_verified and not s.is_banned);
$$;

-- The card as others see it, or null.
create or replace function private.signature_card(p_user uuid) returns jsonb
language sql stable security definer set search_path = public as $$
  select case when private.card_unlocked(p_user) then (
    select jsonb_build_object('id', c.id, 'template', c.template, 'looking_for', c.looking_for,
                              'reasons', to_jsonb(c.reasons), 'vibe_emoji', c.vibe_emoji, 'vibe', c.vibe)
      from public.profile_cards c where c.user_id = p_user) end;
$$;

create or replace function public.get_my_profile_card() returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare
  uid uuid := auth.uid();
begin
  if uid is null then
    raise exception 'not_signed_in';
  end if;
  return jsonb_build_object(
    'woman', exists (select 1 from public.profiles where id = uid and gender = 'woman'),
    'unlocked', private.card_unlocked(uid),
    'card', (select jsonb_build_object('id', c.id, 'template', c.template, 'looking_for', c.looking_for,
                                       'reasons', to_jsonb(c.reasons), 'vibe_emoji', c.vibe_emoji, 'vibe', c.vibe)
               from public.profile_cards c where c.user_id = uid));
end $$;

create or replace function public.save_profile_card(p_template text, p_looking_for text, p_reasons text[],
                                                    p_vibe_emoji text default null, p_vibe text default null)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  uid uuid := auth.uid();
  looking text := btrim(coalesce(p_looking_for, ''));
  vibe_text text := nullif(btrim(coalesce(p_vibe, '')), '');
  emoji text := nullif(btrim(coalesce(p_vibe_emoji, '')), '');
  tags text[];
begin
  if uid is null then
    raise exception 'not_signed_in';
  end if;
  if not exists (select 1 from public.profiles where id = uid and gender = 'woman') then
    raise exception 'card_women_only';
  end if;
  if not private.card_unlocked(uid) then
    raise exception 'card_needs_verification';
  end if;
  if p_template is null or p_template not in ('midnight_glass','champagne_pearl','rose_velvet','emerald_noir') then
    raise exception 'card_bad_template';
  end if;
  if char_length(looking) < 1 or char_length(looking) > 120 then
    raise exception 'card_looking_for_length';
  end if;
  select coalesce(array_agg(distinct t), '{}') into tags from unnest(coalesce(p_reasons, '{}')) t;
  if cardinality(tags) < 1 or cardinality(tags) > 3 or not tags <@ private.card_reasons() then
    raise exception 'card_bad_reasons';
  end if;
  if vibe_text is not null and char_length(vibe_text) > 40 then
    raise exception 'card_vibe_length';
  end if;
  if emoji is not null and not emoji = any(private.card_emojis()) then
    raise exception 'card_bad_emoji';
  end if;
  if vibe_text is not null and emoji is null then
    emoji := '✨';
  end if;
  if private.has_contact_info(looking) or private.has_contact_info(vibe_text) then
    raise exception 'card_contact_info';
  end if;

  insert into public.profile_cards (user_id, template, looking_for, reasons, vibe_emoji, vibe)
  values (uid, p_template, looking, tags, emoji, vibe_text)
  on conflict (user_id) do update set template = excluded.template, looking_for = excluded.looking_for,
    reasons = excluded.reasons, vibe_emoji = excluded.vibe_emoji, vibe = excluded.vibe, updated_at = now();
  return public.get_my_profile_card();
end $$;


grant execute on function public.get_my_profile_card() to authenticated;
grant execute on function public.save_profile_card(text, text, text[], text, text) to authenticated;

-- The card travels with the daily Discover set, and a like may point at it.
do $$
declare
  def text;
  changed text;
begin
  def := pg_get_functiondef('public.get_daily_set()'::regprocedure);
  if position('signature_card' in def) = 0 then
    changed := replace(def, '''phone_verified'', s.phone_verified,',
      '''phone_verified'', s.phone_verified,
      ''signature'', private.signature_card(p.id),');
    if changed = def then
      raise exception 'signature card not added to get_daily_set';
    end if;
    execute changed;
  end if;

  def := pg_get_functiondef('public.send_like(uuid,text,uuid,text)'::regprocedure);
  if position('''card''' in def) = 0 then
    changed := replace(def,
      'or (p_target_type = ''prompt'' and exists (select 1 from public.profile_prompts where id = p_target_id and user_id = p_to))',
      'or (p_target_type = ''prompt'' and exists (select 1 from public.profile_prompts where id = p_target_id and user_id = p_to))
    or (p_target_type = ''card'' and coalesce(private.signature_card(p_to) ->> ''id'' = p_target_id::text, false))');
    if changed = def then
      raise exception 'card target not added to send_like';
    end if;
    execute changed;
  end if;

  def := pg_get_functiondef('public.export_my_data()'::regprocedure);
  if position('profile_cards' in def) = 0 then
    changed := replace(def, '''prompts'', coalesce(',
      '''signature_card'', (select to_jsonb(c) - ''user_id'' from public.profile_cards c where c.user_id = uid),
    ''prompts'', coalesce(');
    if changed = def then
      raise exception 'signature card not added to export_my_data';
    end if;
    execute changed;
  end if;
end $$;
