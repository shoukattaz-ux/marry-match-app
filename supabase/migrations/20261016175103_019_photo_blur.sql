-- Photo blur until match. A member can choose to show only blurred photos to people who are not
-- (yet) a match. The phone uploads a strongly blurred copy of every photo next to it
-- ("<id>_b.jpg"); while the setting is on, the server signs only those copies for anyone except
-- the owner, moderators and matches. So the clear photo cannot be fetched before a match, not
-- even with a modified app.

alter table public.profiles add column if not exists blur_photos boolean not null default false;

-- True when the file may be read by the current user as far as blurring is concerned.
-- can_view_photos still decides whether the user may see this member's photos at all.
create or replace function private.photo_unblurred_ok(p_name text) returns boolean
language plpgsql stable security definer set search_path = public as $$
declare
  me uuid := auth.uid();
  folder text := split_part(p_name, '/', 1);
  owner uuid;
begin
  if p_name like '%\_b.jpg' then
    return true;  -- the blurred copy itself
  end if;
  if me is null or folder !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then
    return false;
  end if;
  owner := folder::uuid;
  if owner = me or private.is_admin() then
    return true;
  end if;
  return not coalesce((select p.blur_photos from public.profiles p where p.id = owner), false)
      or exists (select 1 from public.matches m
                  where m.user_a = least(me, owner) and m.user_b = greatest(me, owner));
end $$;

revoke all on function private.photo_unblurred_ok(text) from public, anon;
grant execute on function private.photo_unblurred_ok(text) to authenticated;

alter policy "photos bucket: read if allowed" on storage.objects
  using (bucket_id = 'profile-photos'
         and private.can_view_photos((storage.foldername(name))[1])
         and private.photo_unblurred_ok(name));

-- Daily set: tell the app which members show blurred photos, so it asks for the blurred copies.
create or replace function public.get_daily_set()
 returns jsonb
 language plpgsql
 security definer
 set search_path to 'public'
as $function$
declare
  uid uuid := auth.uid();
  d date := private.current_set_date();
  result jsonb;
begin
  if uid is null then
    raise exception 'not_signed_in';
  end if;
  update public.profiles set last_active_at = now() where id = uid;
  if not exists (select 1 from public.daily_sets where user_id = uid and set_date = d) then
    perform private.build_daily_set(uid, d);
  end if;

  select jsonb_build_object(
    'set_date', d,
    'total', (select count(*) from public.daily_sets where user_id = uid and set_date = d),
    'profiles', coalesce(jsonb_agg(card order by pos), '[]'::jsonb)
  ) into result
  from (
    select ds.position as pos, jsonb_build_object(
      'id', p.id,
      'display_name', p.display_name,
      'age', extract(year from age(p.birth_date))::int,
      'city', case when p.hide_city then null else p.city end,
      'region', p.region,
      'bio', p.bio,
      'marriage_timeline', p.marriage_timeline,
      'wants_children', p.wants_children,
      'religion', case when p.hide_religion then null else p.religion end,
      'smoking', p.smoking,
      'education', p.education,
      'occupation', case when p.hide_occupation then null else p.occupation end,
      'languages', to_jsonb(p.languages),
      'height_cm', p.height_cm,
      'selfie_verified', s.selfie_verified,
      'phone_verified', s.phone_verified,
      'active', private.activity_label(p.last_active_at, p.hide_activity),
      'photos_blurred', p.blur_photos,
      'photos', coalesce((select jsonb_agg(jsonb_build_object('id', ph.id, 'path', ph.storage_path) order by ph.position)
                            from public.profile_photos ph where ph.user_id = p.id), '[]'::jsonb),
      'prompts', coalesce((select jsonb_agg(jsonb_build_object('id', pr.id, 'key', pr.prompt_key, 'answer', pr.answer))
                             from public.profile_prompts pr where pr.user_id = p.id), '[]'::jsonb)
    ) as card
    from public.daily_sets ds
    join public.profiles p on p.id = ds.candidate_id
    join public.user_status s on s.user_id = p.id
    where ds.user_id = uid and ds.set_date = d
      and p.onboarding_done and not s.is_banned
      and not private.is_blocked_between(uid, p.id)
      and not exists (select 1 from public.likes l where l.from_user = uid and l.to_user = p.id)
      and not exists (select 1 from public.passes x where x.from_user = uid and x.to_user = p.id)
      and not exists (select 1 from public.matches m
                       where m.user_a = least(uid, p.id) and m.user_b = greatest(uid, p.id))
  ) cards;
  return result;
end $function$;
