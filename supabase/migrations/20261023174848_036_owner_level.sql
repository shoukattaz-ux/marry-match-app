-- Owner level: one account above the admins. Admins can still make new admins, but only the
-- owner can take admin rights away, and nobody (not even another admin) can remove the owner.
-- The owner flag is never writable from the app; it changes only by hand in the database.
alter table public.user_status add column if not exists is_owner boolean not null default false;

-- The owner is the account that is admin today (exactly one).
do $$
begin
  if (select count(*) from public.user_status where is_admin) <> 1 then
    raise exception 'expected exactly one admin to become owner';
  end if;
  update public.user_status set is_owner = true where is_admin;
end $$;

-- An owner is always an admin.
alter table public.user_status add constraint owner_is_admin check (not is_owner or is_admin);

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

create or replace function public.admin_set_admin(p_user uuid, p_admin boolean)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  me uuid := auth.uid();
  i_am_owner boolean;
  target record;
begin
  select is_owner into i_am_owner from public.user_status where user_id = me and is_admin and not is_banned;
  if me is null or i_am_owner is null then
    raise exception 'not_allowed';
  end if;
  select u.id, u.email, u.email_confirmed_at, s.is_admin, s.is_banned, s.is_owner into target
    from auth.users u join public.user_status s on s.user_id = u.id
   where u.id = p_user;
  if target.id is null then
    raise exception 'user_not_found';
  end if;
  if not p_admin and target.is_owner then
    raise exception 'target_is_owner';
  end if;
  if not p_admin and target.id = me then
    raise exception 'cannot_remove_self';
  end if;
  if not p_admin and not i_am_owner then
    raise exception 'owner_only';
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
