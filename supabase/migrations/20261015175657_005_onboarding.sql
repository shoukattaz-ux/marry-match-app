-- Phase 3: onboarding progress and server-side profile rules.

-- The step to resume at when a user leaves onboarding midway (1..10).
alter table public.profiles
  add column onboarding_step int not null default 1 check (onboarding_step between 1 and 10);

-- A photo row may only point to a file in the owner's own storage folder.
alter policy "photos: own" on public.profile_photos
  with check (
    auth.uid() = user_id
    and private.country_ok()
    and split_part(storage_path, '/', 1) = auth.uid()::text
  );

create or replace function public.enforce_profile_limits() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if tg_table_name = 'profile_photos'
     and (select count(*) from public.profile_photos where user_id = new.user_id) >= 6 then
    raise exception 'profile_photo_limit';
  end if;
  if tg_table_name = 'profile_prompts'
     and (select count(*) from public.profile_prompts where user_id = new.user_id) >= 3 then
    raise exception 'profile_prompt_limit';
  end if;
  return new;
end $$;
revoke all on function public.enforce_profile_limits() from public, anon, authenticated;

create trigger trg_photo_limit before insert on public.profile_photos
for each row execute function public.enforce_profile_limits();
create trigger trg_prompt_limit before insert on public.profile_prompts
for each row execute function public.enforce_profile_limits();

-- A profile only counts as finished (and becomes visible to others) with at least 3 photos.
create or replace function public.enforce_onboarding_done() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if new.onboarding_done
     and (tg_op = 'INSERT' or not old.onboarding_done)
     and (select count(*) from public.profile_photos where user_id = new.id) < 3 then
    raise exception 'profile_needs_three_photos';
  end if;
  return new;
end $$;
revoke all on function public.enforce_onboarding_done() from public, anon, authenticated;

create trigger trg_onboarding_done before insert or update of onboarding_done on public.profiles
for each row execute function public.enforce_onboarding_done();
