-- Phase 4: selfie verification with manual review.
-- Flow: start_selfie_check() picks a random pose and opens a request -> the app uploads the live
-- selfie to selfies/<user_id>/<request_id>.jpg within 30 minutes -> submit_selfie() puts it in the
-- moderator queue -> the review-selfie Edge Function approves or rejects and deletes the file.

alter table public.verification_requests add column submitted_at timestamptz;

-- Requests are only created through start_selfie_check() (server-chosen pose, attempt limit).
alter policy "verification: create own pending" on public.verification_requests with check (false);

create or replace function public.start_selfie_check() returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  uid uuid := auth.uid();
  rid uuid;
  p text;
begin
  if uid is null then
    raise exception 'not_signed_in';
  end if;
  if not private.country_ok() then
    raise exception 'not_allowed';
  end if;
  if not exists (select 1 from public.consents where user_id = uid and consent_type = 'selfie_biometric') then
    raise exception 'consent_required';
  end if;
  if exists (select 1 from public.user_status where user_id = uid and selfie_verified) then
    raise exception 'already_verified';
  end if;
  if exists (select 1 from public.verification_requests
              where user_id = uid and status = 'pending' and submitted_at is not null) then
    raise exception 'already_pending';
  end if;
  if (select count(*) from public.verification_requests
       where user_id = uid and created_at > now() - interval '24 hours') >= 3 then
    raise exception 'selfie_attempt_limit';
  end if;

  p := (array['touch_nose', 'peace_sign', 'thumbs_up_tilt', 'hand_on_chin', 'wave_hand'])[1 + floor(random() * 5)::int];
  insert into public.verification_requests (user_id, pose) values (uid, p) returning id into rid;
  return jsonb_build_object('request_id', rid, 'pose', p, 'expires_at', now() + interval '30 minutes');
end $$;

-- Called after the upload: checks the file exists and puts the request in the moderator queue.
create or replace function public.submit_selfie(p_request uuid) returns void
language plpgsql security definer set search_path = public as $$
declare
  uid uuid := auth.uid();
  path text := auth.uid()::text || '/' || p_request::text || '.jpg';
begin
  if uid is null then
    raise exception 'not_signed_in';
  end if;
  if not exists (select 1 from storage.objects where bucket_id = 'selfies' and name = path) then
    raise exception 'selfie_not_uploaded';
  end if;
  update public.verification_requests
     set storage_path = path, submitted_at = now()
   where id = p_request and user_id = uid and status = 'pending'
     and submitted_at is null and created_at > now() - interval '30 minutes';
  if not found then
    raise exception 'selfie_request_expired';
  end if;
end $$;

-- Selfies may only be uploaded for the user's own open request, under its exact name.
alter policy "selfies bucket: upload to own folder" on storage.objects
  with check (
    bucket_id = 'selfies'
    and private.country_ok()
    and exists (
      select 1 from public.verification_requests r
       where r.user_id = auth.uid()
         and r.status = 'pending'
         and r.submitted_at is null
         and r.created_at > now() - interval '30 minutes'
         and name = auth.uid()::text || '/' || r.id::text || '.jpg')
  );

-- Moderator queue, oldest first. Admins only.
create or replace function public.get_selfie_queue() returns jsonb
language plpgsql stable security definer set search_path = public as $$
begin
  if not private.is_admin() then
    raise exception 'not_allowed';
  end if;
  return (
    select coalesce(jsonb_agg(jsonb_build_object(
        'request_id', r.id,
        'user_id', r.user_id,
        'display_name', p.display_name,
        'age', extract(year from age(p.birth_date))::int,
        'city', p.city,
        'pose', r.pose,
        'submitted_at', r.submitted_at,
        'selfie_path', r.storage_path,
        'photos', coalesce((select jsonb_agg(ph.storage_path order by ph.position)
                              from public.profile_photos ph where ph.user_id = r.user_id), '[]'::jsonb)
      ) order by r.submitted_at), '[]'::jsonb)
    from public.verification_requests r
    join public.profiles p on p.id = r.user_id
    where r.status = 'pending' and r.submitted_at is not null
  );
end $$;

-- The user's own latest verification state for the profile screen.
create or replace function public.my_selfie_status() returns jsonb
language sql stable security definer set search_path = public as $$
  select coalesce((
    select jsonb_build_object('status', r.status, 'reject_reason', r.reject_reason, 'submitted', r.submitted_at is not null)
      from public.verification_requests r
     where r.user_id = auth.uid() and (r.submitted_at is not null or r.status <> 'pending')
     order by r.created_at desc limit 1
  ), jsonb_build_object('status', 'none'));
$$;

revoke all on function public.start_selfie_check() from public, anon;
revoke all on function public.submit_selfie(uuid) from public, anon;
revoke all on function public.get_selfie_queue() from public, anon;
revoke all on function public.my_selfie_status() from public, anon;
grant execute on function public.start_selfie_check() to authenticated;
grant execute on function public.submit_selfie(uuid) to authenticated;
grant execute on function public.get_selfie_queue() to authenticated;
grant execute on function public.my_selfie_status() to authenticated;

-- A new main photo removes the blue tick: the user has to verify again (blueprint rule).
create or replace function public.on_main_photo_change() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if (tg_op = 'INSERT' and new.position = 0)
     or (tg_op = 'UPDATE' and new.position = 0 and old.position <> 0)
     or (tg_op = 'DELETE' and old.position = 0) then
    update public.user_status set selfie_verified = false
     where user_id = coalesce(new.user_id, old.user_id) and selfie_verified;
  end if;
  return coalesce(new, old);
end $$;
revoke all on function public.on_main_photo_change() from public, anon, authenticated;

create trigger trg_main_photo_change after insert or update of position or delete on public.profile_photos
for each row execute function public.on_main_photo_change();
