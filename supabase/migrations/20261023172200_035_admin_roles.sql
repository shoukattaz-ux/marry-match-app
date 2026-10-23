-- Admin roles: admins find any account by e-mail and make it an admin (or take it back);
-- everyone sees the Admin badge on admin profiles. Every change is in the audit log.

-- Admin rights change only through admin_set_admin below (logged), never by editing the row.
-- The app never writes user_status; Edge Functions use the service role.
revoke update on public.user_status from authenticated, anon;

-- Account search for the admin panel: e-mail, name or id; newest sign-in first.
create or replace function public.admin_search_accounts(p_query text default '', p_signed_in_only boolean default false)
returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare
  q text := lower(trim(coalesce(p_query, '')));
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
      'needs_review', coalesce(s.needs_review, false),
      'respect_score', coalesce(s.respect_score, 100),
      'open_reports', (select count(*)::int from public.reports x where x.reported_id = u.id and x.status = 'open')
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
revoke all on function public.admin_search_accounts(text, boolean) from public, anon;
grant execute on function public.admin_search_accounts(text, boolean) to authenticated;

-- Makes an account an admin (p_admin = true) or a normal user again. Only admins may call it;
-- the account must have a confirmed e-mail and not be banned; nobody removes their own rights,
-- so there is always at least one admin.
create or replace function public.admin_set_admin(p_user uuid, p_admin boolean)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  me uuid := auth.uid();
  target record;
begin
  if me is null or not exists (select 1 from public.user_status where user_id = me and is_admin and not is_banned) then
    raise exception 'not_allowed';
  end if;
  select u.id, u.email, u.email_confirmed_at, s.is_admin, s.is_banned into target
    from auth.users u join public.user_status s on s.user_id = u.id
   where u.id = p_user;
  if target.id is null then
    raise exception 'user_not_found';
  end if;
  if not p_admin and target.id = me then
    raise exception 'cannot_remove_self';
  end if;
  if p_admin and target.email_confirmed_at is null then
    raise exception 'email_not_confirmed';
  end if;
  if p_admin and target.is_banned then
    raise exception 'user_banned';
  end if;
  if target.is_admin = p_admin then
    return jsonb_build_object('changed', false, 'is_admin', p_admin);
  end if;
  update public.user_status set is_admin = p_admin where user_id = target.id;
  insert into public.audit_log (actor_id, action, target_user, details)
  values (me, case when p_admin then 'admin_granted' else 'admin_revoked' end, target.id,
          jsonb_build_object('email', target.email));
  return jsonb_build_object('changed', true, 'is_admin', p_admin);
end $$;
revoke all on function public.admin_set_admin(uuid, boolean) from public, anon;
grant execute on function public.admin_set_admin(uuid, boolean) to authenticated;

-- The Admin badge on profile cards (Discover) and matches (list and chat).
do $$
declare
  fn text;
  def text;
  changed text;
begin
  foreach fn in array array['public.get_daily_set()', 'public.get_my_matches()'] loop
    def := pg_get_functiondef(fn::regprocedure);
    if position('''is_admin''' in def) > 0 then
      continue;
    end if;
    changed := replace(def, '''phone_verified'', s.phone_verified,', '''phone_verified'', s.phone_verified,
      ''is_admin'', s.is_admin,');
    if changed = def then
      raise exception 'badge field not added to %', fn;
    end if;
    execute changed;
  end loop;
end $$;
