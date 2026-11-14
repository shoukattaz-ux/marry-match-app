-- Online status with an admin-set "stays online" time. The app sends a heartbeat every minute
-- while it is open; after the member leaves, the status is:
--   online   for online_minutes after the last activity (admin setting, starts at 10)
--   recent   for recently_online_minutes more (the existing admin setting: how long
--            "Recently online" stays visible)
--   offline  after that.
-- Coming back to the app makes the status online again at once (the next heartbeat).

insert into public.app_settings (key, value, updated_at)
values ('online_minutes', '10'::jsonb, now())
on conflict (key) do nothing;

create or replace function private.online_minutes() returns int
language sql stable security definer set search_path = public as $$
  select coalesce((select (value #>> '{}')::int from public.app_settings where key = 'online_minutes'), 10);
$$;

create or replace function private.activity_label(p_last timestamptz, p_owner_hides boolean)
returns text
language sql stable security definer set search_path = public as $$
  select case
    when p_owner_hides or coalesce((select hide_activity from public.profiles where id = auth.uid()), false) then null
    when p_last > now() - make_interval(mins => private.online_minutes()) then 'online'
    when p_last > now() - make_interval(mins => private.online_minutes() + private.recently_online_minutes()) then 'recent'
    else 'offline'
  end;
$$;

-- Admins change how long members stay online after leaving (1 minute to 4 hours); logged.
create or replace function public.admin_set_online_minutes(p_minutes int) returns int
language plpgsql security definer set search_path = public as $$
begin
  if not private.is_admin() then
    raise exception 'not_allowed';
  end if;
  if p_minutes is null or p_minutes < 1 or p_minutes > 240 then
    raise exception 'bad_request';
  end if;
  if private.online_minutes() is distinct from p_minutes then
    insert into public.app_settings (key, value, updated_at, updated_by)
    values ('online_minutes', to_jsonb(p_minutes), now(), auth.uid())
    on conflict (key) do update set value = excluded.value, updated_at = now(), updated_by = auth.uid();
    insert into public.audit_log (actor_id, action, details)
    values (auth.uid(), 'setting_online_minutes', jsonb_build_object('minutes', p_minutes));
  end if;
  return p_minutes;
end $$;
grant execute on function public.admin_set_online_minutes(int) to authenticated;

do $$
declare
  def text := pg_get_functiondef('public.admin_stats()'::regprocedure);
  changed text;
begin
  if position('private.online_minutes()' in def) = 0 then
    changed := replace(def, '''recently_online_minutes'', private.recently_online_minutes()',
      '''recently_online_minutes'', private.recently_online_minutes(),
    ''online_minutes'', private.online_minutes()');
    if changed = def then raise exception 'online_minutes not added to admin_stats'; end if;
    execute changed;
  end if;
end $$;
