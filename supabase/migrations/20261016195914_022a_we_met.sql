-- "We met" check-in. After meeting in person, each person can confirm it with how it went.
-- How the other person felt is never shown; the app only learns whether they confirmed too.
-- Moderators see totals (couples who met, meetings that felt unsafe), never single answers.
-- Rows keep the pair instead of a foreign key to matches, so the totals survive an unmatch;
-- they are removed with the accounts.

create table public.meetings (
  match_id uuid not null,
  user_id uuid not null references auth.users(id) on delete cascade,
  other_id uuid not null references auth.users(id) on delete cascade,
  feeling text not null check (feeling in ('great', 'okay', 'not_for_me', 'unsafe')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (match_id, user_id),
  check (user_id <> other_id)
);
create index meetings_other on public.meetings (other_id);
alter table public.meetings enable row level security;

-- Returns { both_met } so the app can celebrate when both confirmed.
create or replace function public.confirm_meeting(p_match_id uuid, p_feeling text) returns jsonb
language plpgsql volatile security definer set search_path = public as $$
declare
  me uuid := auth.uid();
  other uuid;
begin
  if me is null then raise exception 'not_signed_in'; end if;
  if p_feeling not in ('great', 'okay', 'not_for_me', 'unsafe') then raise exception 'bad_feeling'; end if;
  select case when m.user_a = me then m.user_b else m.user_a end into other
    from public.matches m where m.id = p_match_id and me in (m.user_a, m.user_b);
  if other is null then raise exception 'not_in_match'; end if;
  insert into public.meetings (match_id, user_id, other_id, feeling)
  values (p_match_id, me, other, p_feeling)
  on conflict (match_id, user_id) do update set feeling = excluded.feeling, updated_at = now();
  return jsonb_build_object('both_met',
    exists (select 1 from public.meetings where match_id = p_match_id and user_id = other));
end $$;
revoke all on function public.confirm_meeting(uuid, text) from public, anon;
grant execute on function public.confirm_meeting(uuid, text) to authenticated;

create or replace function public.get_my_matches()
 returns jsonb
 language sql
 stable security definer
 set search_path to 'public'
as $function$
  select coalesce(jsonb_agg(jsonb_build_object(
      'match_id', m.id,
      'matched_at', m.created_at,
      'user_id', o.id,
      'display_name', o.display_name,
      'age', extract(year from age(o.birth_date))::int,
      'city', case when o.hide_city then o.region else o.city end,
      'photo', (select ph.storage_path from public.profile_photos ph where ph.user_id = o.id order by ph.position limit 1),
      'selfie_verified', s.selfie_verified,
      'phone_verified', s.phone_verified,
      'active', private.activity_label(o.last_active_at, o.hide_activity),
      'expires_at', case when lm.created_at is null then m.created_at + interval '7 days' end,
      'chaperoned', private.has_guardian(m.user_a) or private.has_guardian(m.user_b),
      'i_met', exists (select 1 from public.meetings mt where mt.match_id = m.id and mt.user_id = auth.uid()),
      'they_met', exists (select 1 from public.meetings mt where mt.match_id = m.id and mt.user_id = o.id),
      'last_message', lm.body,
      'last_message_at', lm.created_at,
      'last_from_me', lm.sender_id = auth.uid(),
      'unread', (select count(*) from public.messages u
                  where u.match_id = m.id and u.sender_id <> auth.uid() and u.read_at is null)
    ) order by coalesce(lm.created_at, m.created_at) desc), '[]'::jsonb)
  from public.matches m
  join public.profiles o on o.id = case when m.user_a = auth.uid() then m.user_b else m.user_a end
  join public.user_status s on s.user_id = o.id
  left join lateral (
    select body, created_at, sender_id from public.messages
     where match_id = m.id order by created_at desc limit 1
  ) lm on true
  where auth.uid() in (m.user_a, m.user_b) and not s.is_banned
    and (lm.created_at is not null or m.created_at >= now() - interval '7 days');
$function$;

create or replace function public.admin_stats() returns jsonb
language plpgsql stable security definer set search_path = public as $$
begin
  if not private.is_admin() then
    raise exception 'not_allowed';
  end if;
  return jsonb_build_object(
    'users_total',     (select count(*) from public.user_status),
    'new_today',       (select count(*) from auth.users where created_at >= date_trunc('day', now())),
    'active_7d',       (select count(*) from public.profiles where last_active_at > now() - interval '7 days'),
    'profiles_done',   (select count(*) from public.profiles where onboarding_done),
    'verified',        (select count(*) from public.user_status where selfie_verified),
    'pending_selfies', (select count(*) from public.verification_requests where status = 'pending' and submitted_at is not null),
    'open_reports',    (select count(*) from public.reports where status = 'open'),
    'banned',          (select count(*) from public.user_status where is_banned),
    'needs_review',    (select count(*) from public.user_status where needs_review and not is_banned),
    'met_couples',     (select count(*) from public.meetings a
                         join public.meetings b on b.match_id = a.match_id and b.user_id = a.other_id
                        where a.user_id < a.other_id),
    'met_unsafe_30d',  (select count(*) from public.meetings where feeling = 'unsafe' and updated_at > now() - interval '30 days')
  );
end $$;

