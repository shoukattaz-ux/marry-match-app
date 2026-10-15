-- Phase 6: daily curated sets, likes and matches.

-- Discovery preferences (the Discover filters). Defaults mean "anyone 18+ in Germany".
alter table public.profiles
  add column pref_min_age int not null default 18 check (pref_min_age between 18 and 99),
  add column pref_max_age int not null default 99 check (pref_max_age between 18 and 99),
  add column pref_region text,
  add column pref_verified_only boolean not null default false,
  add column pref_timelines text[] not null default '{}',
  add column pref_languages text[] not null default '{}',
  add constraint profiles_pref_age_order check (pref_min_age <= pref_max_age);

-- A daily set runs from 06:00 to 06:00 Berlin time.
create or replace function private.current_set_date() returns date
language sql stable as $$
  select ((now() at time zone 'Europe/Berlin') - interval '6 hours')::date;
$$;

-- Picks up to 10 people for one user and day (blueprint Phase 6 rules).
create or replace function private.build_daily_set(p_user uuid, p_date date) returns int
language plpgsql security definer set search_path = public as $$
declare
  n int;
begin
  insert into public.daily_sets (user_id, set_date, candidate_id, position)
  select p_user, p_date, c.id, row_number() over (order by c.score desc)
  from (
    select p.id,
      -- same marriage timeline first
        (case when p.marriage_timeline = me.marriage_timeline then 40 else 0 end)
      -- nearby: same city, then same Bundesland (no coordinates are stored)
      + (case when p.city = me.city then 30 when p.region = me.region then 20 else 0 end)
      -- shared languages
      + least(cardinality(array(select unnest(p.languages) intersect select unnest(me.languages))), 2) * 10
      -- verified people
      + (case when s.selfie_verified then 10 else 0 end)
      + (case when s.phone_verified then 5 else 0 end)
      -- recent activity
      + (case when p.last_active_at > now() - interval '7 days' then 10
              when p.last_active_at > now() - interval '30 days' then 5 else 0 end)
      -- people who already liked this user, so mutual likes can happen
      + (case when exists (select 1 from public.likes l where l.from_user = p.id and l.to_user = p_user) then 15 else 0 end)
      -- low respect score is shown less often
      - (case when s.respect_score < 60 then 30 else 0 end)
      -- small random factor so the set is not always identical
      + random() * 10 as score
    from public.profiles me
    join public.user_status ms on ms.user_id = me.id
    cross join public.profiles p
    join public.user_status s on s.user_id = p.id
    where me.id = p_user
      and me.onboarding_done and ms.country_ok and not ms.is_banned
      and p.id <> p_user
      and p.onboarding_done and s.country_ok and not s.is_banned and s.respect_score >= 30
      -- age within both people's ranges
      and extract(year from age(p.birth_date)) between me.pref_min_age and me.pref_max_age
      and extract(year from age(me.birth_date)) between p.pref_min_age and p.pref_max_age
      -- gender matches both preferences
      and (me.looking_for = 'any' or p.gender = me.looking_for)
      and (p.looking_for = 'any' or me.gender = p.looking_for)
      -- the user's own filters
      and (me.pref_region is null or p.region = me.pref_region)
      and (not me.pref_verified_only or s.selfie_verified)
      and (cardinality(me.pref_timelines) = 0 or p.marriage_timeline = any (me.pref_timelines))
      and (cardinality(me.pref_languages) = 0 or p.languages && me.pref_languages)
      -- never blocked either way, already liked, passed or matched
      and not private.is_blocked_between(p_user, p.id)
      and not exists (select 1 from public.likes l where l.from_user = p_user and l.to_user = p.id)
      and not exists (select 1 from public.passes x where x.from_user = p_user and x.to_user = p.id)
      and not exists (select 1 from public.matches m
                       where m.user_a = least(p_user, p.id) and m.user_b = greatest(p_user, p.id))
    order by score desc
    limit 10
  ) c
  on conflict do nothing;
  get diagnostics n = row_count;
  return n;
end $$;

revoke all on function private.current_set_date() from public, anon, authenticated;
revoke all on function private.build_daily_set(uuid, date) from public, anon, authenticated;

-- Today's remaining profiles for the signed-in user (builds the set if it is missing).
create or replace function public.get_daily_set() returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  uid uuid := auth.uid();
  d date := private.current_set_date();
  result jsonb;
begin
  if uid is null then
    raise exception 'not_signed_in';
  end if;
  update public.profiles set last_active_at = now() where id = uid;
  if not exists (select 1 from public.daily_sets where user_id = uid and set_date = d) then
    perform private.build_daily_set(uid, d);
  end if;

  select jsonb_build_object(
    'set_date', d,
    'total', (select count(*) from public.daily_sets where user_id = uid and set_date = d),
    'profiles', coalesce(jsonb_agg(card order by pos), '[]'::jsonb)
  ) into result
  from (
    select ds.position as pos, jsonb_build_object(
      'id', p.id,
      'display_name', p.display_name,
      'age', extract(year from age(p.birth_date))::int,
      'city', p.city,
      'region', p.region,
      'bio', p.bio,
      'marriage_timeline', p.marriage_timeline,
      'wants_children', p.wants_children,
      'religion', p.religion,
      'smoking', p.smoking,
      'education', p.education,
      'occupation', p.occupation,
      'languages', to_jsonb(p.languages),
      'height_cm', p.height_cm,
      'selfie_verified', s.selfie_verified,
      'phone_verified', s.phone_verified,
      'photos', coalesce((select jsonb_agg(jsonb_build_object('id', ph.id, 'path', ph.storage_path) order by ph.position)
                            from public.profile_photos ph where ph.user_id = p.id), '[]'::jsonb),
      'prompts', coalesce((select jsonb_agg(jsonb_build_object('id', pr.id, 'key', pr.prompt_key, 'answer', pr.answer))
                             from public.profile_prompts pr where pr.user_id = p.id), '[]'::jsonb)
    ) as card
    from public.daily_sets ds
    join public.profiles p on p.id = ds.candidate_id
    join public.user_status s on s.user_id = p.id
    where ds.user_id = uid and ds.set_date = d
      and p.onboarding_done and not s.is_banned
      and not private.is_blocked_between(uid, p.id)
      and not exists (select 1 from public.likes l where l.from_user = uid and l.to_user = p.id)
      and not exists (select 1 from public.passes x where x.from_user = uid and x.to_user = p.id)
      and not exists (select 1 from public.matches m
                       where m.user_a = least(uid, p.id) and m.user_b = greatest(uid, p.id))
  ) cards;
  return result;
end $$;

-- Likes a person from the user's own daily set, optionally on one photo or prompt with a comment.
-- The make_match trigger creates the match when the like is mutual.
create or replace function public.send_like(
  p_to uuid,
  p_target_type text default null,
  p_target_id uuid default null,
  p_comment text default null
) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  uid uuid := auth.uid();
  mid uuid;
begin
  if uid is null then
    raise exception 'not_signed_in';
  end if;
  if not private.country_ok() or private.is_blocked_between(uid, p_to) then
    raise exception 'not_allowed';
  end if;
  if not exists (select 1 from public.daily_sets where user_id = uid and candidate_id = p_to) then
    raise exception 'not_in_daily_set';
  end if;
  if p_target_type is not null and not (
       (p_target_type = 'photo' and exists (select 1 from public.profile_photos where id = p_target_id and user_id = p_to))
    or (p_target_type = 'prompt' and exists (select 1 from public.profile_prompts where id = p_target_id and user_id = p_to))
  ) then
    raise exception 'bad_target';
  end if;

  insert into public.likes (from_user, to_user, target_type, target_id, comment)
  values (uid, p_to, p_target_type,
          case when p_target_type is null then null else p_target_id end,
          nullif(left(btrim(coalesce(p_comment, '')), 200), ''))
  on conflict (from_user, to_user) do nothing;

  select id into mid from public.matches
   where user_a = least(uid, p_to) and user_b = greatest(uid, p_to);
  return jsonb_build_object('matched', mid is not null, 'match_id', mid);
end $$;

-- The signed-in user's matches, newest first.
create or replace function public.get_my_matches() returns jsonb
language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(jsonb_build_object(
      'match_id', m.id,
      'matched_at', m.created_at,
      'user_id', o.id,
      'display_name', o.display_name,
      'age', extract(year from age(o.birth_date))::int,
      'city', o.city,
      'photo', (select ph.storage_path from public.profile_photos ph where ph.user_id = o.id order by ph.position limit 1),
      'selfie_verified', s.selfie_verified,
      'phone_verified', s.phone_verified
    ) order by m.created_at desc), '[]'::jsonb)
  from public.matches m
  join public.profiles o on o.id = case when m.user_a = auth.uid() then m.user_b else m.user_a end
  join public.user_status s on s.user_id = o.id
  where auth.uid() in (m.user_a, m.user_b) and not s.is_banned;
$$;

revoke all on function public.get_daily_set() from public, anon;
revoke all on function public.send_like(uuid, text, uuid, text) from public, anon;
revoke all on function public.get_my_matches() from public, anon;
grant execute on function public.get_daily_set() to authenticated;
grant execute on function public.send_like(uuid, text, uuid, text) to authenticated;
grant execute on function public.get_my_matches() to authenticated;

-- Likes may only go to someone from the user's own daily sets (no liking arbitrary accounts).
alter policy "likes: send as yourself" on public.likes
  with check (
    auth.uid() = from_user
    and private.country_ok()
    and not private.is_blocked_between(from_user, to_user)
    and exists (select 1 from public.daily_sets ds where ds.user_id = auth.uid() and ds.candidate_id = to_user)
  );
