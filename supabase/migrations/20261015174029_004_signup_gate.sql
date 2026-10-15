-- Phase 1 + 2: server-side sign-up rules.
-- A new account is only created when the sign-up carries a fresh one-time pass from the
-- check-germany function, an 18+ birth date, a non-disposable email and the legal consents.
-- The app sends these in the sign-up metadata (raw_user_meta_data).
--
-- Accounts created by an admin through the service-role API skip the gate when they are
-- given app_metadata {"gate_bypass": true}. Accounts added in the dashboard without it fail.

create table public.signup_passes (
  token_hash text primary key,              -- sha256 of the pass; the pass itself is never stored
  needs_review boolean not null default false,  -- GPS said Germany but the IP country did not
  expires_at timestamptz not null,
  used_at timestamptz,
  created_at timestamptz not null default now()
);
create index idx_signup_passes_expiry on public.signup_passes (expires_at);
alter table public.signup_passes enable row level security;
create policy "server only" on public.signup_passes for all using (false) with check (false);
revoke all on public.signup_passes from anon, authenticated;

create table public.blocked_email_domains (
  domain text primary key
);
alter table public.blocked_email_domains enable row level security;
create policy "server only" on public.blocked_email_domains for all using (false) with check (false);
revoke all on public.blocked_email_domains from anon, authenticated;

-- Keep in sync with DisposableEmail.kt in the app (the app checks first for a friendly message).
insert into public.blocked_email_domains (domain) values
  ('mailinator.com'), ('guerrillamail.com'), ('guerrillamail.de'), ('sharklasers.com'),
  ('10minutemail.com'), ('temp-mail.org'), ('tempmail.com'), ('tempail.com'),
  ('yopmail.com'), ('trashmail.com'), ('trash-mail.com'), ('wegwerfmail.de'),
  ('einrot.com'), ('byom.de'), ('getnada.com'), ('dispostable.com'), ('maildrop.cc'),
  ('throwawaymail.com'), ('fakeinbox.com'), ('mailnesia.com'), ('emailondeck.com'),
  ('mohmal.com'), ('burnermail.io'), ('spamgourmet.com')
on conflict do nothing;

create or replace function public.handle_new_user() returns trigger
language plpgsql security definer set search_path = public as $$
declare
  meta jsonb := coalesce(new.raw_user_meta_data, '{}'::jsonb);
  pass public.signup_passes%rowtype;
  birth date;
begin
  if coalesce((new.raw_app_meta_data ->> 'gate_bypass')::boolean, false) then
    insert into public.user_status (user_id) values (new.id) on conflict do nothing;
    return new;
  end if;

  -- Raising an exception here rolls back the whole sign-up, so no account is created.
  update public.signup_passes
     set used_at = now()
   where token_hash = encode(sha256(convert_to(coalesce(meta ->> 'signup_pass', ''), 'UTF8')), 'hex')
     and used_at is null
     and expires_at > now()
  returning * into pass;
  if not found then
    raise exception 'signup_location_required';
  end if;

  begin
    birth := (meta ->> 'birth_date')::date;
  exception when others then
    birth := null;
  end;
  if birth is null or birth > (current_date - interval '18 years')::date then
    raise exception 'signup_underage';
  end if;

  if exists (select 1 from public.blocked_email_domains
              where domain = lower(split_part(coalesce(new.email, ''), '@', 2))) then
    raise exception 'signup_email_domain';
  end if;

  if coalesce(meta ->> 'terms_version', '') = ''
     or coalesce(meta ->> 'privacy_version', '') = ''
     or coalesce(meta ->> 'conduct_version', '') = '' then
    raise exception 'signup_consent_required';
  end if;

  insert into public.user_status (user_id, country_ok, geo_checked_at, needs_review)
  values (new.id, true, now(), pass.needs_review)
  on conflict (user_id) do update
    set country_ok = true, geo_checked_at = now(), needs_review = excluded.needs_review;

  insert into public.consents (user_id, consent_type, version)
  select new.id, c.consent_type, meta ->> c.meta_key
    from (values ('terms', 'terms_version'),
                 ('privacy', 'privacy_version'),
                 ('conduct', 'conduct_version'),
                 ('location', 'location_version')) as c(consent_type, meta_key)
   where coalesce(meta ->> c.meta_key, '') <> ''
  on conflict do nothing;

  return new;
end $$;

revoke all on function public.handle_new_user() from public, anon, authenticated;
