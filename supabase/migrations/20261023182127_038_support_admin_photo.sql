-- A member sees the photos of an admin who wrote to them (admin messages show the admin's own
-- profile), also when the admin blurs photos. Otherwise unchanged from migrations 012 and 019.
-- [p_admin] is still an admin and has written to the caller.
create or replace function private.admin_wrote_to_me(p_admin uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.support_threads t
                  where t.user_id = auth.uid() and t.admin_id = p_admin and private.support_open(t.id)
                    and exists (select 1 from public.support_messages m where m.thread_id = t.id));
$$;
revoke all on function private.admin_wrote_to_me(uuid) from public, anon;
grant execute on function private.admin_wrote_to_me(uuid) to authenticated;

create or replace function private.can_view_photos(p_owner text)
returns boolean
language plpgsql stable security definer set search_path = public as $$
declare
  me uuid := auth.uid();
  owner uuid;
begin
  if me is null or p_owner is null
     or p_owner !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then
    return false;
  end if;
  owner := p_owner::uuid;
  if owner = me or private.is_admin() or private.admin_wrote_to_me(owner) then
    return true;
  end if;
  if private.is_blocked_between(me, owner)
     or exists (select 1 from public.user_status s where s.user_id = owner and s.is_banned) then
    return false;
  end if;
  return exists (select 1 from public.daily_sets ds
                  where ds.user_id = me and ds.candidate_id = owner
                    and ds.set_date >= private.current_set_date() - 1)
      or exists (select 1 from public.matches m
                  where m.user_a = least(me, owner) and m.user_b = greatest(me, owner));
end $$;

create or replace function private.photo_unblurred_ok(p_name text)
returns boolean
language plpgsql stable security definer set search_path = public as $$
declare
  me uuid := auth.uid();
  folder text := split_part(p_name, '/', 1);
  owner uuid;
begin
  if p_name like '%\_b.jpg' then
    return true;
  end if;
  if me is null or folder !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then
    return false;
  end if;
  owner := folder::uuid;
  if owner = me or private.is_admin() or private.admin_wrote_to_me(owner) then
    return true;
  end if;
  return not coalesce((select p.blur_photos from public.profiles p where p.id = owner), false)
      or exists (select 1 from public.matches m
                  where m.user_a = least(me, owner) and m.user_b = greatest(me, owner));
end $$;
