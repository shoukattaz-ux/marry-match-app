-- Push notifications through Firebase Cloud Messaging.
-- Database triggers queue a call to the send-push Edge Function (pg_net, asynchronous, so a slow
-- push never delays a message or like). The function checks a shared secret kept in Vault,
-- loads the user's device tokens and sends the push with the Firebase key stored as the
-- FIREBASE_SERVICE_ACCOUNT_JSON Edge Function secret. No message text is ever put in a push.

alter table public.push_tokens
  add column lang text not null default 'en' check (lang in ('en', 'de')),
  add column updated_at timestamptz not null default now();

alter table public.profiles
  add column notify_matches boolean not null default true,
  add column notify_messages boolean not null default true,
  add column notify_likes boolean not null default true;

-- Shared secret between the triggers and send-push; random, never leaves the database.
select vault.create_secret(encode(extensions.gen_random_bytes(32), 'hex'), 'push_webhook_secret',
                           'Checked by the send-push Edge Function');

-- send-push asks the database whether the secret it received is right (service role only).
create or replace function public.push_secret_ok(p_secret text) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from vault.decrypted_secrets where name = 'push_webhook_secret' and decrypted_secret = p_secret);
$$;
revoke execute on function public.push_secret_ok(text) from public, anon, authenticated;
grant execute on function public.push_secret_ok(text) to service_role;

-- Queues one push if the user wants this kind and has a device registered.
create or replace function private.queue_push(p_user uuid, p_kind text, p_data jsonb) returns void
language plpgsql security definer set search_path = public as $$
declare
  wanted boolean;
begin
  select case p_kind when 'match' then p.notify_matches
                     when 'message' then p.notify_messages
                     when 'like' then p.notify_likes
                     else false end
    into wanted
    from public.profiles p
   where p.id = p_user;
  if not coalesce(wanted, false)
     or not exists (select 1 from public.push_tokens t where t.user_id = p_user)
     or exists (select 1 from public.user_status s where s.user_id = p_user and s.is_banned) then
    return;
  end if;
  perform net.http_post(
    url := 'https://zaixmvhfblgarpjckjhc.supabase.co/functions/v1/send-push',
    body := jsonb_build_object('user_id', p_user, 'kind', p_kind, 'data', p_data),
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'x-push-secret', (select decrypted_secret from vault.decrypted_secrets where name = 'push_webhook_secret'))
  );
end $$;
revoke all on function private.queue_push(uuid, text, jsonb) from public, anon, authenticated;

create or replace function private.push_on_message() returns trigger
language plpgsql security definer set search_path = public as $$
declare
  recipient uuid;
begin
  select case when m.user_a = new.sender_id then m.user_b else m.user_a end into recipient
    from public.matches m where m.id = new.match_id;
  if recipient is not null and not private.is_blocked_between(recipient, new.sender_id) then
    perform private.queue_push(recipient, 'message', jsonb_build_object(
      'match_id', new.match_id,
      'name', (select display_name from public.profiles where id = new.sender_id)));
  end if;
  return null;
end $$;

create or replace function private.push_on_match() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  perform private.queue_push(new.user_a, 'match', jsonb_build_object(
    'match_id', new.id, 'name', (select display_name from public.profiles where id = new.user_b)));
  perform private.queue_push(new.user_b, 'match', jsonb_build_object(
    'match_id', new.id, 'name', (select display_name from public.profiles where id = new.user_a)));
  return null;
end $$;

-- Likes are shown blurred in the app, so the push does not name the person. A like that
-- completes a match gets the match push instead.
create or replace function private.push_on_like() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if not exists (select 1 from public.likes l where l.from_user = new.to_user and l.to_user = new.from_user) then
    perform private.queue_push(new.to_user, 'like', '{}'::jsonb);
  end if;
  return null;
end $$;

revoke all on function private.push_on_message() from public, anon, authenticated;
revoke all on function private.push_on_match() from public, anon, authenticated;
revoke all on function private.push_on_like() from public, anon, authenticated;

create trigger push_on_message after insert on public.messages
  for each row execute function private.push_on_message();
create trigger push_on_match after insert on public.matches
  for each row execute function private.push_on_match();
create trigger push_on_like after insert on public.likes
  for each row execute function private.push_on_like();

-- A phone keeps its token when another account signs in on it, so registering moves the
-- token to the signed-in user (the table policy alone would refuse to take it over).
create or replace function public.register_push_token(p_token text, p_lang text default 'en') returns void
language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then
    raise exception 'not_signed_in';
  end if;
  if p_token is null or length(p_token) not between 20 and 4096 then
    raise exception 'bad_token';
  end if;
  insert into public.push_tokens (user_id, token, platform, lang, updated_at)
  values (auth.uid(), p_token, 'android', case when p_lang = 'de' then 'de' else 'en' end, now())
  on conflict (token) do update
    set user_id = excluded.user_id, lang = excluded.lang, platform = excluded.platform, updated_at = now();
end $$;

-- Called at sign-out, so the phone gets no more pushes for this account.
create or replace function public.unregister_push_token(p_token text) returns void
language sql security definer set search_path = public as $$
  delete from public.push_tokens where token = p_token and user_id = auth.uid();
$$;

revoke execute on function public.register_push_token(text, text) from public, anon;
revoke execute on function public.unregister_push_token(text) from public, anon;
grant execute on function public.register_push_token(text, text) to authenticated;
grant execute on function public.unregister_push_token(text) to authenticated;
