-- Online status: "online" (active in the last 2 minutes), "recent" (recently online, within an
-- admin setting that starts at 20 minutes) or "offline". The app sends a heartbeat every minute
-- while it is open (touch_activity), so last_active_at is at most about a minute old for people
-- using the app. Hidden activity (hide_activity, either side) still shows no status at all.

insert into public.app_settings (key, value, updated_at)
values ('recently_online_minutes', '20'::jsonb, now())
on conflict (key) do nothing;

create or replace function private.recently_online_minutes() returns int
language sql stable security definer set search_path = public as $$
  select coalesce((select (value #>> '{}')::int from public.app_settings where key = 'recently_online_minutes'), 20);
$$;

-- Heartbeat: written at most every 30 seconds per person.
create or replace function public.touch_activity() returns void
language sql security definer set search_path = public as $$
  update public.profiles set last_active_at = now()
   where id = auth.uid() and last_active_at < now() - interval '30 seconds';
$$;

create or replace function private.activity_label(p_last timestamptz, p_owner_hides boolean)
returns text
language sql stable security definer set search_path = public as $$
  select case
    when p_owner_hides or coalesce((select hide_activity from public.profiles where id = auth.uid()), false) then null
    when p_last > now() - interval '2 minutes' then 'online'
    when p_last > now() - make_interval(mins => private.recently_online_minutes()) then 'recent'
    else 'offline'
  end;
$$;

-- Admins change the "recently online" time (3 minutes to 24 hours); written to the audit log.
create or replace function public.admin_set_recent_minutes(p_minutes int) returns int
language plpgsql security definer set search_path = public as $$
begin
  if not private.is_admin() then
    raise exception 'not_allowed';
  end if;
  if p_minutes is null or p_minutes < 3 or p_minutes > 1440 then
    raise exception 'bad_request';
  end if;
  if private.recently_online_minutes() is distinct from p_minutes then
    insert into public.app_settings (key, value, updated_at, updated_by)
    values ('recently_online_minutes', to_jsonb(p_minutes), now(), auth.uid())
    on conflict (key) do update set value = excluded.value, updated_at = now(), updated_by = auth.uid();
    insert into public.audit_log (actor_id, action, details)
    values (auth.uid(), 'setting_recent_minutes', jsonb_build_object('minutes', p_minutes));
  end if;
  return p_minutes;
end $$;
revoke all on function public.admin_set_recent_minutes(int) from public, anon;
grant execute on function public.admin_set_recent_minutes(int) to authenticated;

do $$
declare
  def text := pg_get_functiondef('public.admin_stats()'::regprocedure);
  changed text;
begin
  if position('recently_online_minutes' in def) = 0 then
    changed := replace(def, '''daily_like_limit'', private.daily_like_limit()',
      '''daily_like_limit'', private.daily_like_limit(),
    ''recently_online_minutes'', private.recently_online_minutes()');
    if changed = def then raise exception 'recently_online_minutes not added to admin_stats'; end if;
    execute changed;
  end if;
end $$;
