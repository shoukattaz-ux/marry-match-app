alter table public.user_status
  add column if not exists needs_review boolean not null default false,
  add column if not exists geo_checked_at timestamptz;

create or replace function private.country_ok() returns boolean
language sql stable security definer set search_path = public as $$
  select coalesce((select s.country_ok and not s.is_banned from public.user_status s where s.user_id = auth.uid()), false);
$$;
revoke all on function private.country_ok() from public, anon;
grant execute on function private.country_ok() to authenticated;

alter policy "profiles: own" on public.profiles
  with check (auth.uid() = id and private.country_ok());

alter policy "likes: send as yourself" on public.likes
  with check (auth.uid() = from_user and private.country_ok() and not private.is_blocked_between(from_user, to_user));

alter policy "photos: own" on public.profile_photos
  with check (auth.uid() = user_id and private.country_ok());

alter policy "photos bucket: upload to own folder" on storage.objects
  with check (bucket_id = 'profile-photos' and (storage.foldername(name))[1] = auth.uid()::text and private.country_ok());

alter policy "selfies bucket: upload to own folder" on storage.objects
  with check (bucket_id = 'selfies' and (storage.foldername(name))[1] = auth.uid()::text and private.country_ok());

alter policy "verification: create own pending" on public.verification_requests
  with check (auth.uid() = user_id and status = 'pending' and private.country_ok());

-- Babar's own admin account, so he can test from outside Germany
update public.user_status set country_ok = true, geo_checked_at = now()
 where user_id = '20301f7c-1eaf-4dbc-baaf-6b4210d78ccf';
