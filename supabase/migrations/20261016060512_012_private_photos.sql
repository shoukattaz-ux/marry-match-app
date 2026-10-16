-- Profile photos become private. Before, anyone with a photo's link could open it. Now a photo
-- can only be loaded through a short-lived signed link, and the server creates that link only
-- for: the owner, moderators, people who have the owner in their recent daily suggestions, and
-- matches. Blocking hides photos both ways. Incognito and "verified only" already decide who
-- gets someone in their daily suggestions, so they apply to photos too.

-- p_owner is the first folder of the file path, i.e. the owner's id as text.
create or replace function private.can_view_photos(p_owner text) returns boolean
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
  if owner = me or private.is_admin() then
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

revoke all on function private.can_view_photos(text) from public, anon;
grant execute on function private.can_view_photos(text) to authenticated;

create policy "photos bucket: read if allowed" on storage.objects
  for select to authenticated
  using (bucket_id = 'profile-photos' and private.can_view_photos((storage.foldername(name))[1]));

update storage.buckets set public = false where id = 'profile-photos';
