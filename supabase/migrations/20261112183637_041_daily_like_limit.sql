-- Free likes per day: the same number for every account, set by admins in the admin panel
-- (0 = no limit). The day is the Discover day (starts 06:00 Berlin time, current_set_date), so the
-- count resets together with the new daily set. send_like refuses a new like over the limit with
-- 'daily_like_limit'; liking someone already liked again does not count. Starts at 10: each
-- member sees at most 10 new profiles a day, so nothing changes until an admin lowers it.

insert into public.app_settings (key, value, updated_at)
values ('daily_like_limit', '10'::jsonb, now())
on conflict (key) do nothing;

create or replace function private.daily_like_limit() returns int
language sql stable security definer set search_path = public as $$
  select coalesce((select (value #>> '{}')::int from public.app_settings where key = 'daily_like_limit'), 0);
$$;

-- New likes [p_user] sent in the current Discover day.
create or replace function private.likes_sent_today(p_user uuid) returns int
language sql stable security definer set search_path = public as $$
  select count(*)::int from public.likes
   where from_user = p_user
     and created_at > now() - interval '2 days'
     and ((created_at at time zone 'Europe/Berlin') - interval '6 hours')::date = private.current_set_date();
$$;

-- Likes the caller has left today, or null when there is no limit.
create or replace function private.likes_left(p_user uuid) returns int
language sql stable security definer set search_path = public as $$
  select case when private.daily_like_limit() <= 0 then null
              else greatest(private.daily_like_limit() - private.likes_sent_today(p_user), 0) end;
$$;

create or replace function public.send_like(p_to uuid, p_target_type text default null, p_target_id uuid default null, p_comment text default null)
returns jsonb
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
  -- Free likes per day (admin setting); a like that already exists does not count again.
  if private.likes_left(uid) = 0
     and not exists (select 1 from public.likes where from_user = uid and to_user = p_to) then
    raise exception 'daily_like_limit';
  end if;

  insert into public.likes (from_user, to_user, target_type, target_id, comment)
  values (uid, p_to, p_target_type,
          case when p_target_type is null then null else p_target_id end,
          nullif(left(btrim(coalesce(p_comment, '')), 200), ''))
  on conflict (from_user, to_user) do nothing;

  select id into mid from public.matches
   where user_a = least(uid, p_to) and user_b = greatest(uid, p_to);
  return jsonb_build_object('matched', mid is not null, 'match_id', mid, 'likes_left', private.likes_left(uid));
end $$;

-- Admins change the number (0 = no limit, at most 100); written to the audit log.
create or replace function public.admin_set_like_limit(p_limit int) returns int
language plpgsql security definer set search_path = public as $$
begin
  if not private.is_admin() then
    raise exception 'not_allowed';
  end if;
  if p_limit is null or p_limit < 0 or p_limit > 100 then
    raise exception 'bad_request';
  end if;
  if private.daily_like_limit() is distinct from p_limit then
    insert into public.app_settings (key, value, updated_at, updated_by)
    values ('daily_like_limit', to_jsonb(p_limit), now(), auth.uid())
    on conflict (key) do update set value = excluded.value, updated_at = now(), updated_by = auth.uid();
    insert into public.audit_log (actor_id, action, details)
    values (auth.uid(), 'setting_like_limit', jsonb_build_object('limit', p_limit));
  end if;
  return p_limit;
end $$;
revoke all on function public.admin_set_like_limit(int) from public, anon;
grant execute on function public.admin_set_like_limit(int) to authenticated;

-- The admin overview shows the setting; the daily set tells the app how many likes are left.
do $$
declare
  def text;
  changed text;
begin
  def := pg_get_functiondef('public.admin_stats()'::regprocedure);
  if position('daily_like_limit' in def) = 0 then
    changed := replace(def, '''male_signups_paused'', private.male_signups_paused()',
      '''male_signups_paused'', private.male_signups_paused(),
    ''daily_like_limit'', private.daily_like_limit()');
    if changed = def then raise exception 'like limit not added to admin_stats'; end if;
    execute changed;
  end if;

  def := pg_get_functiondef('public.get_daily_set()'::regprocedure);
  if position('likes_left' in def) = 0 then
    changed := replace(def, '''set_date'', d,', '''set_date'', d,
    ''likes_left'', private.likes_left(uid),');
    if changed = def then raise exception 'likes_left not added to get_daily_set'; end if;
    execute changed;
  end if;
end $$;
