-- Daily set ranking now also counts habits, interests and values (private.compat_score).
create or replace function private.build_daily_set(p_user uuid, p_date date)
 returns integer
 language plpgsql
 security definer
 set search_path to 'public'
as $function$
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
      -- same wish for children
      + (case when p.wants_children = me.wants_children then 10
              when p.wants_children = 'open' or me.wants_children = 'open' then 3 else -10 end)
      -- habits, interests and values
      + private.compat_score(me, p)
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
      -- the other person's privacy settings
      and not p.incognito
      and (not p.verified_viewers_only or ms.selfie_verified)
      -- age within both people's ranges
      and extract(year from age(p.birth_date)) between me.pref_min_age and me.pref_max_age
      and extract(year from age(me.birth_date)) between p.pref_min_age and p.pref_max_age
      -- gender matches both preferences
      and p.gender = me.looking_for
      and me.gender = p.looking_for
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
end $function$;

-- Cards also carry interests and the habit/value answers.
create or replace function public.get_daily_set()
 returns jsonb
 language plpgsql
 security definer
 set search_path to 'public'
as $function$
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
      'city', case when p.hide_city then null else p.city end,
      'region', p.region,
      'bio', p.bio,
      'marriage_timeline', p.marriage_timeline,
      'wants_children', p.wants_children,
      'religion', case when p.hide_religion then null else p.religion end,
      'smoking', p.smoking,
      'education', p.education,
      'occupation', case when p.hide_occupation then null else p.occupation end,
      'languages', to_jsonb(p.languages),
      'interests', to_jsonb(p.interests),
      'traits', jsonb_strip_nulls(jsonb_build_object(
        'smoking', p.smoking, 'drinking', p.drinking, 'exercise', p.exercise, 'diet', p.diet,
        'pets', p.pets, 'sleep', p.sleep, 'personality', p.personality,
        'family_closeness', p.family_closeness, 'household', p.household,
        'home_after_marriage', p.home_after_marriage, 'relocate', p.relocate)),
      'height_cm', p.height_cm,
      'selfie_verified', s.selfie_verified,
      'phone_verified', s.phone_verified,
      'active', private.activity_label(p.last_active_at, p.hide_activity),
      'photos_blurred', p.blur_photos,
      'chaperoned', private.has_guardian(p.id),
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
end $function$;
