create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text not null check (char_length(display_name) between 1 and 40),
  birth_date date not null,
  gender text not null check (gender in ('woman','man','other')),
  looking_for text not null check (looking_for in ('woman','man','any')),
  city text,
  region text,
  bio text check (char_length(bio) <= 500),
  marriage_timeline text check (marriage_timeline in ('within_1y','1_2y','2_5y','open')),
  wants_children text check (wants_children in ('yes','no','open')),
  religion text,
  smoking text,
  education text,
  occupation text,
  languages text[] not null default '{}',
  interests text[] not null default '{}',
  height_cm int check (height_cm between 120 and 230),
  onboarding_done boolean not null default false,
  last_active_at timestamptz not null default now(),
  created_at timestamptz not null default now()
);

create table public.user_status (
  user_id uuid primary key references auth.users(id) on delete cascade,
  selfie_verified boolean not null default false,
  phone_verified boolean not null default false,
  is_admin boolean not null default false,
  is_banned boolean not null default false,
  country_ok boolean not null default false,
  respect_score int not null default 100
);

create table public.profile_photos (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  storage_path text not null,
  position int not null default 0
);

create table public.profile_prompts (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  prompt_key text not null,
  answer text not null check (char_length(answer) <= 300)
);

create table public.verification_requests (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  pose text not null,
  storage_path text,
  status text not null default 'pending' check (status in ('pending','approved','rejected')),
  reviewed_by uuid,
  reviewed_at timestamptz,
  reject_reason text,
  created_at timestamptz not null default now()
);

create table public.phone_otps (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  phone_hash text not null,
  code_hash text not null,
  expires_at timestamptz not null,
  attempts int not null default 0,
  consumed boolean not null default false,
  created_at timestamptz not null default now()
);

create table public.verified_phones (
  phone_hash text primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now()
);

create table public.daily_sets (
  user_id uuid not null references auth.users(id) on delete cascade,
  set_date date not null,
  candidate_id uuid not null references auth.users(id) on delete cascade,
  position int,
  primary key (user_id, set_date, candidate_id)
);

create table public.likes (
  id uuid primary key default gen_random_uuid(),
  from_user uuid not null references auth.users(id) on delete cascade,
  to_user uuid not null references auth.users(id) on delete cascade,
  target_type text check (target_type in ('photo','prompt')),
  target_id uuid,
  comment text check (char_length(comment) <= 200),
  created_at timestamptz not null default now(),
  unique (from_user, to_user),
  check (from_user <> to_user)
);

create table public.passes (
  from_user uuid not null references auth.users(id) on delete cascade,
  to_user uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (from_user, to_user),
  check (from_user <> to_user)
);

create table public.matches (
  id uuid primary key default gen_random_uuid(),
  user_a uuid not null references auth.users(id) on delete cascade,
  user_b uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  unique (user_a, user_b),
  check (user_a < user_b)
);

create table public.messages (
  id uuid primary key default gen_random_uuid(),
  match_id uuid not null references public.matches(id) on delete cascade,
  sender_id uuid not null references auth.users(id) on delete cascade,
  body text not null check (char_length(body) between 1 and 2000),
  created_at timestamptz not null default now(),
  read_at timestamptz
);

create table public.blocks (
  blocker uuid not null references auth.users(id) on delete cascade,
  blocked uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (blocker, blocked),
  check (blocker <> blocked)
);

create table public.reports (
  id uuid primary key default gen_random_uuid(),
  reporter_id uuid references auth.users(id) on delete set null,
  reported_id uuid not null references auth.users(id) on delete cascade,
  message_id uuid,
  reason text not null check (reason in ('fake','harassment','scam','underage','photo','other')),
  details text check (char_length(details) <= 1000),
  status text not null default 'open' check (status in ('open','actioned','dismissed')),
  created_at timestamptz not null default now()
);

create table public.push_tokens (
  user_id uuid not null references auth.users(id) on delete cascade,
  token text primary key,
  platform text
);

create table public.consents (
  user_id uuid not null references auth.users(id) on delete cascade,
  consent_type text not null
    check (consent_type in ('terms','privacy','conduct','location','selfie_biometric','sensitive_profile','analytics')),
  version text not null,
  granted_at timestamptz not null default now(),
  primary key (user_id, consent_type, version)
);

create table public.audit_log (
  id bigint generated always as identity primary key,
  actor_id uuid,
  action text not null,
  target_user uuid,
  details jsonb,
  created_at timestamptz not null default now()
);

create index idx_likes_to_user       on public.likes (to_user);
create index idx_messages_match_time on public.messages (match_id, created_at);
create index idx_daily_sets_lookup   on public.daily_sets (user_id, set_date);
create index idx_profile_photos_user on public.profile_photos (user_id, position);
create index idx_prompts_user        on public.profile_prompts (user_id);
create index idx_reports_status      on public.reports (status, created_at);
create index idx_verif_status        on public.verification_requests (status, created_at);
create index idx_profiles_region     on public.profiles (region, gender);

create or replace function public.is_admin() returns boolean
language sql stable security definer set search_path = public as $$
  select coalesce((select is_admin from public.user_status where user_id = auth.uid()), false);
$$;

create or replace function public.is_blocked_between(a uuid, b uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.blocks
    where (blocker = a and blocked = b) or (blocker = b and blocked = a)
  );
$$;

create or replace function public.in_match(m uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.matches
    where id = m and auth.uid() in (user_a, user_b)
  );
$$;

create or replace function public.mark_messages_read(p_match uuid) returns void
language plpgsql security definer set search_path = public as $$
begin
  if not public.in_match(p_match) then
    raise exception 'not in this match';
  end if;
  update public.messages
     set read_at = now()
   where match_id = p_match and sender_id <> auth.uid() and read_at is null;
end $$;

create or replace function public.received_likes_count() returns int
language sql stable security definer set search_path = public as $$
  select count(*)::int
    from public.likes l
   where l.to_user = auth.uid()
     and not public.is_blocked_between(l.from_user, l.to_user)
     and not exists (select 1 from public.matches m
                      where m.user_a = least(l.from_user, l.to_user)
                        and m.user_b = greatest(l.from_user, l.to_user));
$$;

create or replace function public.handle_new_user() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  insert into public.user_status (user_id) values (new.id) on conflict do nothing;
  return new;
end $$;

create trigger trg_new_user after insert on auth.users
for each row execute function public.handle_new_user();

create or replace function public.enforce_adult() returns trigger
language plpgsql set search_path = public as $$
begin
  if new.birth_date > (current_date - interval '18 years')::date then
    raise exception 'MarryMatch is for people aged 18 and over';
  end if;
  return new;
end $$;

create trigger trg_enforce_adult before insert or update of birth_date on public.profiles
for each row execute function public.enforce_adult();

create or replace function public.make_match() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if public.is_blocked_between(new.from_user, new.to_user) then
    return new;
  end if;
  if exists (select 1 from public.likes
              where from_user = new.to_user and to_user = new.from_user) then
    insert into public.matches (user_a, user_b)
    values (least(new.from_user, new.to_user), greatest(new.from_user, new.to_user))
    on conflict do nothing;
  end if;
  return new;
end $$;

create trigger trg_make_match after insert on public.likes
for each row execute function public.make_match();

create or replace function public.on_block() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  delete from public.matches
   where user_a = least(new.blocker, new.blocked)
     and user_b = greatest(new.blocker, new.blocked);
  return new;
end $$;

create trigger trg_on_block after insert on public.blocks
for each row execute function public.on_block();

alter table public.profiles              enable row level security;
alter table public.user_status           enable row level security;
alter table public.profile_photos        enable row level security;
alter table public.profile_prompts       enable row level security;
alter table public.verification_requests enable row level security;
alter table public.phone_otps            enable row level security;
alter table public.verified_phones       enable row level security;
alter table public.daily_sets            enable row level security;
alter table public.likes                 enable row level security;
alter table public.passes                enable row level security;
alter table public.matches               enable row level security;
alter table public.messages              enable row level security;
alter table public.blocks                enable row level security;
alter table public.reports               enable row level security;
alter table public.push_tokens           enable row level security;
alter table public.consents              enable row level security;
alter table public.audit_log             enable row level security;

create policy "profiles: own"
  on public.profiles for all
  using (auth.uid() = id) with check (auth.uid() = id);

create policy "profiles: see finished, unblocked profiles"
  on public.profiles for select
  using (
    auth.uid() is not null
    and onboarding_done
    and not public.is_blocked_between(auth.uid(), id)
  );

create policy "profiles: admin read"
  on public.profiles for select using (public.is_admin());

create policy "status: read own"
  on public.user_status for select using (auth.uid() = user_id);

create policy "status: admin read"
  on public.user_status for select using (public.is_admin());

create policy "status: admin update"
  on public.user_status for update
  using (public.is_admin()) with check (public.is_admin());

create view public.profile_badges as
  select user_id, selfie_verified, phone_verified from public.user_status;
revoke all on public.profile_badges from anon;
grant select on public.profile_badges to authenticated;

create policy "photos: own"
  on public.profile_photos for all
  using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "photos: others see unblocked"
  on public.profile_photos for select
  using (auth.uid() is not null and not public.is_blocked_between(auth.uid(), user_id));

create policy "prompts: own"
  on public.profile_prompts for all
  using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "prompts: others see unblocked"
  on public.profile_prompts for select
  using (auth.uid() is not null and not public.is_blocked_between(auth.uid(), user_id));

create policy "verification: create own pending"
  on public.verification_requests for insert
  with check (auth.uid() = user_id and status = 'pending');
create policy "verification: read own"
  on public.verification_requests for select using (auth.uid() = user_id);
create policy "verification: admin read"
  on public.verification_requests for select using (public.is_admin());
create policy "verification: admin update"
  on public.verification_requests for update
  using (public.is_admin()) with check (public.is_admin());

create policy "daily_sets: read own"
  on public.daily_sets for select using (auth.uid() = user_id);

create policy "likes: send as yourself"
  on public.likes for insert
  with check (auth.uid() = from_user and not public.is_blocked_between(from_user, to_user));
create policy "likes: read my sent"
  on public.likes for select using (auth.uid() = from_user);
create policy "likes: withdraw my own"
  on public.likes for delete using (auth.uid() = from_user);

create policy "passes: add own"
  on public.passes for insert with check (auth.uid() = from_user);
create policy "passes: read own"
  on public.passes for select using (auth.uid() = from_user);

create policy "matches: read mine"
  on public.matches for select using (auth.uid() in (user_a, user_b));
create policy "matches: unmatch"
  on public.matches for delete using (auth.uid() in (user_a, user_b));

create policy "messages: read my chats"
  on public.messages for select using (public.in_match(match_id));
create policy "messages: send in my chats"
  on public.messages for insert
  with check (sender_id = auth.uid() and public.in_match(match_id));

create policy "blocks: add own"    on public.blocks for insert with check (auth.uid() = blocker);
create policy "blocks: read own"   on public.blocks for select using (auth.uid() = blocker);
create policy "blocks: remove own" on public.blocks for delete using (auth.uid() = blocker);

create policy "reports: file one"
  on public.reports for insert
  with check (auth.uid() = reporter_id and reporter_id <> reported_id);
create policy "reports: admin read"
  on public.reports for select using (public.is_admin());
create policy "reports: admin update"
  on public.reports for update
  using (public.is_admin()) with check (public.is_admin());

create policy "push: manage own"
  on public.push_tokens for all
  using (auth.uid() = user_id) with check (auth.uid() = user_id);

create policy "consents: add own"  on public.consents for insert with check (auth.uid() = user_id);
create policy "consents: read own" on public.consents for select using (auth.uid() = user_id);

create policy "audit: admin read"
  on public.audit_log for select using (public.is_admin());
create policy "audit: admin write"
  on public.audit_log for insert
  with check (public.is_admin() and actor_id = auth.uid());

insert into storage.buckets (id, name, public)
values ('profile-photos', 'profile-photos', true),
       ('selfies',        'selfies',        false)
on conflict (id) do nothing;

create policy "photos bucket: upload to own folder"
  on storage.objects for insert
  with check (bucket_id = 'profile-photos'
              and (storage.foldername(name))[1] = auth.uid()::text);
create policy "photos bucket: replace own"
  on storage.objects for update
  using (bucket_id = 'profile-photos'
         and (storage.foldername(name))[1] = auth.uid()::text);
create policy "photos bucket: delete own"
  on storage.objects for delete
  using (bucket_id = 'profile-photos'
         and (storage.foldername(name))[1] = auth.uid()::text);

create policy "selfies bucket: upload to own folder"
  on storage.objects for insert
  with check (bucket_id = 'selfies'
              and (storage.foldername(name))[1] = auth.uid()::text);
create policy "selfies bucket: admin read"
  on storage.objects for select
  using (bucket_id = 'selfies' and public.is_admin());
create policy "selfies bucket: admin delete"
  on storage.objects for delete
  using (bucket_id = 'selfies' and public.is_admin());

alter publication supabase_realtime add table public.messages;
