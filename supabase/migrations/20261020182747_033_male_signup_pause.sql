-- Admin > Overview "Gender balance": the admin can pause new accounts from men while far more men
-- than women have signed up. Existing members are never affected.

-- Off unless an admin switches it on; any read problem counts as "open" so sign-up never breaks.
create or replace function private.male_signups_paused() returns boolean
language sql stable security definer set search_path = public as $$
  select coalesce((select value = 'true'::jsonb from public.app_settings where key = 'male_signups_paused'), false);
$$;
revoke all on function private.male_signups_paused() from public, anon, authenticated;

-- Asked by the sign-up screen before an account is made (also when signed out).
create or replace function public.signup_status() returns jsonb
language sql stable security definer set search_path = public as $$
  select jsonb_build_object('male_signups_open', not private.male_signups_paused());
$$;
revoke all on function public.signup_status() from public;
grant execute on function public.signup_status() to anon, authenticated;

create or replace function public.admin_set_male_signups(p_paused boolean) returns boolean
language plpgsql security definer set search_path = public as $$
declare
  was boolean := private.male_signups_paused();
begin
  if not private.is_admin() then
    raise exception 'not_allowed';
  end if;
  if p_paused is null then
    raise exception 'bad_request';
  end if;
  if was is distinct from p_paused then
    insert into public.app_settings (key, value, updated_at, updated_by)
    values ('male_signups_paused', to_jsonb(p_paused), now(), auth.uid())
    on conflict (key) do update set value = excluded.value, updated_at = now(), updated_by = auth.uid();
    insert into public.audit_log (actor_id, action, details)
    values (auth.uid(), case when p_paused then 'setting_male_signups_paused' else 'setting_male_signups_open' end, '{}'::jsonb);
  end if;
  return p_paused;
end $$;
revoke all on function public.admin_set_male_signups(boolean) from public, anon;
grant execute on function public.admin_set_male_signups(boolean) to authenticated;

-- A new profile as a man (or switching to "man") is refused while paused. This holds even for
-- an account made with an older app or with a different gender at sign-up.
create or replace function private.enforce_male_signup_pause() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if new.gender = 'man'
     and (tg_op = 'INSERT' or old.gender is distinct from 'man')
     and private.male_signups_paused() then
    raise exception 'male_signups_paused';
  end if;
  return new;
end $$;
revoke all on function private.enforce_male_signup_pause() from public, anon, authenticated;

create trigger trg_male_signup_pause
  before insert or update of gender on public.profiles
  for each row execute function private.enforce_male_signup_pause();

-- Overview numbers: profiles by gender, in total and new in the last 7 days.
create or replace function public.admin_stats()
 returns jsonb
 language plpgsql
 stable security definer
 set search_path to 'public'
as $function$
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
    'location_check',  private.location_check_enabled(),
    'men',             (select count(*) from public.profiles where gender = 'man'),
    'women',           (select count(*) from public.profiles where gender = 'woman'),
    'men_7d',          (select count(*) from public.profiles where gender = 'man' and created_at > now() - interval '7 days'),
    'women_7d',        (select count(*) from public.profiles where gender = 'woman' and created_at > now() - interval '7 days'),
    'male_signups_paused', private.male_signups_paused()
  );
end $function$;
