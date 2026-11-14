-- Likes You, free: members see who liked them (photo, name, the full profile as on Discover,
-- what was liked and the comment) and can like back (instant match) or pass.
--   get_received_likes()   the list, newest first
--   like_back(p_from, p_comment)  likes someone who liked me: the match is made at once
-- A like back answers a like, so it does not use up the free likes per day. Passed likes leave
-- the list (and the counts). Photos of someone who liked me may be viewed like Discover photos.

-- The card of one member as others see it (the same fields as get_daily_set).
create or replace function private.member_card(p_id uuid) returns jsonb
language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
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
    'signature', private.signature_card(p.id),
    'is_admin', s.is_admin,
    'active', private.activity_label(p.last_active_at, p.hide_activity),
    'photos_blurred', p.blur_photos,
    'chaperoned', private.has_guardian(p.id),
    'photos', coalesce((select jsonb_agg(jsonb_build_object('id', ph.id, 'path', ph.storage_path,
                                                            'focus', jsonb_build_object('x', ph.focus_x, 'y', ph.focus_y, 'zoom', ph.zoom))
                                         order by ph.position)
                          from public.profile_photos ph where ph.user_id = p.id), '[]'::jsonb),
    'prompts', coalesce((select jsonb_agg(jsonb_build_object('id', pr.id, 'key', pr.prompt_key, 'answer', pr.answer))
                           from public.profile_prompts pr where pr.user_id = p.id), '[]'::jsonb))
  from public.profiles p join public.user_status s on s.user_id = p.id
  where p.id = p_id;
$$;

-- A like from p_from to p_to that is still open: not answered by a match, not passed, not blocked.
create or replace function private.open_like(p_from uuid, p_to uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.likes l where l.from_user = p_from and l.to_user = p_to)
     and not private.is_blocked_between(p_from, p_to)
     and not exists (select 1 from public.passes x where x.from_user = p_to and x.to_user = p_from)
     and not exists (select 1 from public.matches m
                      where m.user_a = least(p_from, p_to) and m.user_b = greatest(p_from, p_to));
$$;

-- Whether likes from this member are shown at all: a finished profile that is not banned.
create or replace function private.like_listed(p_from uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.profiles p join public.user_status s on s.user_id = p.id
                  where p.id = p_from and p.onboarding_done and not s.is_banned);
$$;

-- The counts on the tab and the badge: exactly the likes the list shows.
create or replace function public.received_likes_count() returns integer
language sql stable security definer set search_path = public as $$
  select count(*)::int from public.likes l
   where l.to_user = auth.uid() and private.like_listed(l.from_user) and private.open_like(l.from_user, l.to_user);
$$;

create or replace function public.received_likes_summary() returns jsonb
language sql stable security definer set search_path = public as $$
  with mine as (
    select l.created_at from public.likes l
     where l.to_user = auth.uid() and private.like_listed(l.from_user) and private.open_like(l.from_user, l.to_user)
  ), seen as (
    select coalesce((select p.likes_seen_at from public.profiles p where p.id = auth.uid()), '-infinity'::timestamptz) as at
  )
  select jsonb_build_object(
    'total', (select count(*) from mine),
    'new', (select count(*) from mine, seen where mine.created_at > seen.at));
$$;

create or replace function public.get_received_likes() returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare
  uid uuid := auth.uid();
  seen timestamptz;
  result jsonb;
begin
  if uid is null then
    raise exception 'not_signed_in';
  end if;
  seen := coalesce((select likes_seen_at from public.profiles where id = uid), '-infinity'::timestamptz);
  select coalesce(jsonb_agg(item order by created desc), '[]'::jsonb) into result
  from (
    select l.created_at as created, jsonb_build_object(
      'like_id', l.id,
      'liked_at', l.created_at,
      'new', l.created_at > seen,
      'target_type', l.target_type,
      'target_id', l.target_id,
      -- what was liked, so the app can name it: the answer's question key
      'prompt_key', (select pr.prompt_key from public.profile_prompts pr where pr.id = l.target_id and l.target_type = 'prompt'),
      'comment', l.comment,
      'profile', private.member_card(l.from_user)
    ) as item
    from public.likes l
    where l.to_user = uid
      and private.like_listed(l.from_user)
      and private.open_like(l.from_user, uid)
    limit 200
  ) items;
  return result;
end $$;

create or replace function public.like_back(p_from uuid, p_comment text default null) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  uid uuid := auth.uid();
  mid uuid;
begin
  if uid is null then
    raise exception 'not_signed_in';
  end if;
  if not private.country_ok() or not private.like_listed(p_from) then
    raise exception 'not_allowed';
  end if;
  if not private.open_like(p_from, uid) then
    raise exception 'no_open_like';
  end if;
  insert into public.likes (from_user, to_user, comment)
  values (uid, p_from, nullif(left(btrim(coalesce(p_comment, '')), 200), ''))
  on conflict (from_user, to_user) do nothing;
  select id into mid from public.matches
   where user_a = least(uid, p_from) and user_b = greatest(uid, p_from);
  return jsonb_build_object('matched', mid is not null, 'match_id', mid);
end $$;

grant execute on function public.get_received_likes() to authenticated;
grant execute on function public.like_back(uuid, text) to authenticated;

-- The free-likes limit and photo access follow the list.
do $$
declare
  def text;
  changed text;
begin
  -- A like back (answering an earlier like) does not count against the free likes per day.
  def := pg_get_functiondef('private.likes_sent_today(uuid)'::regprocedure);
  if position('answer' in def) = 0 then
    changed := replace(def, '     and created_at > now() - interval ''2 days''',
      '     and created_at > now() - interval ''2 days''
     -- a like back answers an earlier like and is free
     and not exists (select 1 from public.likes r
                      where r.from_user = likes.to_user and r.to_user = p_user and r.created_at < likes.created_at)');
    if changed = def then raise exception 'likes_sent_today not changed'; end if;
    execute changed;
  end if;

  -- Photos of someone with an open like to me may be viewed (blurred copies stay blurred).
  def := pg_get_functiondef('private.can_view_photos(text)'::regprocedure);
  if position('open_like' in def) = 0 then
    changed := replace(def, '      or exists (select 1 from public.matches m
                  where m.user_a = least(me, owner) and m.user_b = greatest(me, owner));',
      '      or exists (select 1 from public.matches m
                  where m.user_a = least(me, owner) and m.user_b = greatest(me, owner))
      or private.open_like(owner, me);');
    if changed = def then raise exception 'can_view_photos not changed'; end if;
    execute changed;
  end if;
end $$;
