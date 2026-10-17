-- Admin switch for the Germany location check. No policies: only the service role (edge
-- functions) and the SECURITY DEFINER functions below can read or write this table.
create table public.app_settings (
  key text primary key,
  value jsonb not null,
  updated_at timestamptz not null default now(),
  updated_by uuid references auth.users(id) on delete set null
);
alter table public.app_settings enable row level security;
insert into public.app_settings (key, value) values ('location_check_enabled', 'true'::jsonb);

-- Fail closed: a missing row or any value other than JSON false means the check is on.
create or replace function private.location_check_enabled() returns boolean
language sql stable security definer set search_path = public as $$
  select coalesce((select value <> 'false'::jsonb from public.app_settings where key = 'location_check_enabled'), true);
$$;
revoke all on function private.location_check_enabled() from public, anon, authenticated;

-- Admins only. Every real change is written to the audit log.
create or replace function public.admin_set_location_check(p_enabled boolean) returns boolean
language plpgsql volatile security definer set search_path = public as $$
declare
  was boolean := private.location_check_enabled();
begin
  if not private.is_admin() then
    raise exception 'not_allowed';
  end if;
  if p_enabled is null then
    raise exception 'bad_request';
  end if;
  if was is distinct from p_enabled then
    insert into public.app_settings (key, value, updated_at, updated_by)
    values ('location_check_enabled', to_jsonb(p_enabled), now(), auth.uid())
    on conflict (key) do update set value = excluded.value, updated_at = now(), updated_by = auth.uid();
    insert into public.audit_log (actor_id, action, details)
    values (auth.uid(), case when p_enabled then 'setting_location_check_on' else 'setting_location_check_off' end, '{}'::jsonb);
  end if;
  return p_enabled;
end $$;
revoke all on function public.admin_set_location_check(boolean) from public, anon;
grant execute on function public.admin_set_location_check(boolean) to authenticated;

-- The admin overview shows the current state.
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
    'met_unsafe_30d',  (select count(*) from public.meetings where feeling = 'unsafe' and updated_at > now() - interval '30 days'),
    'location_check',  private.location_check_enabled()
  );
end $$;
