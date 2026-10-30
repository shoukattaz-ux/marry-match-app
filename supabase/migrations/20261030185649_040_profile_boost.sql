-- Boost: an admin can give a profile more visibility for a while (admin panel). While it lasts,
-- the profile gets a strong bonus in the daily Discover ranking, so it is picked for more people
-- and shown nearer the top. Nothing about the profile itself changes (no fake activity); only the
-- order. Only admins set it (user_status is not writable from the app), and every change is logged.

alter table public.user_status add column if not exists boost_until timestamptz;

-- p_hours > 0: boosted from now for that many hours (at most 30 days); 0 ends the boost.
create or replace function public.admin_set_boost(p_user uuid, p_hours int)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  me uuid := auth.uid();
  until_at timestamptz;
begin
  if me is null or not exists (select 1 from public.user_status where user_id = me and is_admin and not is_banned) then
    raise exception 'not_allowed';
  end if;
  if p_hours is null or p_hours < 0 or p_hours > 720 then
    raise exception 'invalid_hours';
  end if;
  if not exists (select 1 from public.user_status where user_id = p_user) then
    raise exception 'user_not_found';
  end if;
  until_at := case when p_hours = 0 then null else now() + make_interval(hours => p_hours) end;
  update public.user_status set boost_until = until_at where user_id = p_user;
  insert into public.audit_log (actor_id, action, target_user, details)
  values (me, case when p_hours = 0 then 'boost_ended' else 'boost_set' end, p_user,
          jsonb_build_object('hours', p_hours, 'until', until_at));
  return jsonb_build_object('boost_until', until_at);
end $$;
revoke all on function public.admin_set_boost(uuid, int) from public, anon;
grant execute on function public.admin_set_boost(uuid, int) to authenticated;

-- The admin account search shows the boost.
create or replace function public.admin_search_accounts(p_query text default '', p_signed_in_only boolean default false)
returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare
  q text := lower(trim(coalesce(p_query, '')));
  i_am_owner boolean := exists (select 1 from public.user_status where user_id = auth.uid() and is_owner);
  result jsonb;
begin
  if not private.is_admin() then
    raise exception 'not_allowed';
  end if;
  select coalesce(jsonb_agg(card order by last_sign_in desc nulls last, created desc), '[]'::jsonb) into result
  from (
    select u.last_sign_in_at as last_sign_in, u.created_at as created, jsonb_build_object(
      'user_id', u.id,
      'email', coalesce(u.email, ''),
      'display_name', coalesce(p.display_name, ''),
      'age', coalesce(extract(year from age(p.birth_date))::int, 0),
      'city', coalesce(p.city, ''),
      'created_at', u.created_at,
      'last_sign_in_at', u.last_sign_in_at,
      'email_confirmed', u.email_confirmed_at is not null,
      'selfie_verified', coalesce(s.selfie_verified, false),
      'is_banned', coalesce(s.is_banned, false),
      'is_admin', coalesce(s.is_admin, false),
      'is_owner', coalesce(s.is_owner, false),
      -- Only the owner removes admins, and nobody removes the owner.
      'can_remove_admin', i_am_owner and coalesce(s.is_admin, false) and not coalesce(s.is_owner, false),
      'needs_review', coalesce(s.needs_review, false),
      'respect_score', coalesce(s.respect_score, 100),
      'open_reports', (select count(*)::int from public.reports x where x.reported_id = u.id and x.status = 'open'),
      'boost_until', case when s.boost_until > now() then s.boost_until end
    ) as card
    from auth.users u
    left join public.user_status s on s.user_id = u.id
    left join public.profiles p on p.id = u.id
    where (not p_signed_in_only or u.last_sign_in_at is not null)
      and (q = ''
           or u.id::text = q
           or lower(u.email) like '%' || q || '%'
           or lower(p.display_name) like '%' || q || '%')
    order by u.last_sign_in_at desc nulls last, u.created_at desc
    limit 100
  ) found;
  return result;
end $$;

-- The ranking bonus in the daily Discover set.
do $$
declare
  def text := pg_get_functiondef('private.build_daily_set(uuid,date)'::regprocedure);
  changed text;
begin
  if position('boost_until' in def) > 0 then
    return;
  end if;
  changed := replace(def, '      + (case when s.phone_verified then 5 else 0 end)',
    '      + (case when s.phone_verified then 5 else 0 end)
      -- boosted by an admin: shown to more people and nearer the top
      + (case when s.boost_until > now() then 60 else 0 end)');
  if changed = def then
    raise exception 'boost bonus not added to build_daily_set';
  end if;
  execute changed;
end $$;
