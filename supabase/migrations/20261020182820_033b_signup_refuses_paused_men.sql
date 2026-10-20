-- The sign-up screen now asks "I am a man / woman" (metadata gender). While new accounts from men
-- are paused, a man's sign-up is refused before the account exists.
create or replace function public.handle_new_user()
 returns trigger
 language plpgsql
 security definer
 set search_path to 'public'
as $function$
declare
  meta jsonb := coalesce(new.raw_user_meta_data, '{}'::jsonb);
  pass public.signup_passes%rowtype;
  birth date;
begin
  if coalesce((new.raw_app_meta_data ->> 'gate_bypass')::boolean, false) then
    insert into public.user_status (user_id) values (new.id) on conflict do nothing;
    return new;
  end if;

  -- Checked before the location pass is used up, so the pass stays valid for a woman's sign-up.
  if meta ->> 'gender' = 'man' and private.male_signups_paused() then
    raise exception 'signup_male_paused';
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
end $function$;
