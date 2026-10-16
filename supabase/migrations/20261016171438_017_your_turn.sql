-- "Your turn" reminders and match expiry (like Hinge / Bumble), to keep conversations alive.
--   A match without any message expires after 7 days: it is hidden and closed, nothing is deleted.
--   One day before, both get a push "expires tomorrow".
--   If someone wrote and the other has not answered for more than 24 hours: one "your turn" push.
--   At most one reminder per person, chat and kind per 24 hours.

create table public.chat_reminders (
  match_id uuid not null references public.matches(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  kind text not null check (kind in ('your_turn', 'expiring')),
  sent_at timestamptz not null default now(),
  primary key (match_id, user_id, kind)
);
alter table public.chat_reminders enable row level security;  -- server-only, no policies

-- Expired: no message at all and older than 7 days.
create or replace function private.match_expired(p_match uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.matches m
     where m.id = p_match and m.created_at < now() - interval '7 days'
       and not exists (select 1 from public.messages x where x.match_id = m.id)
  );
$$;

-- New messages to an expired match are refused.
create or replace function private.refuse_expired_match() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if private.match_expired(new.match_id) then
    raise exception 'match_expired';
  end if;
  return new;
end $$;
revoke all on function private.refuse_expired_match() from public, anon, authenticated;
create trigger refuse_expired_match before insert on public.messages
  for each row execute function private.refuse_expired_match();

-- Push kinds: 'your_turn' follows the messages setting, 'expiring' the matches setting.
create or replace function private.queue_push(p_user uuid, p_kind text, p_data jsonb) returns void
language plpgsql security definer set search_path = public as $$
declare
  wanted boolean;
begin
  select case p_kind when 'match' then p.notify_matches
                     when 'expiring' then p.notify_matches
                     when 'message' then p.notify_messages
                     when 'your_turn' then p.notify_messages
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

-- Daily job (afternoon, Berlin): queues the reminders. Returns how many were sent.
create or replace function private.send_chat_reminders() returns int
language plpgsql security definer set search_path = public as $$
declare
  r record;
  sent int := 0;
begin
  -- Matches without a message that expire within the next 24 hours: remind both people.
  for r in
    select m.id as match_id, u.me, u.other
      from public.matches m
      cross join lateral (values (m.user_a, m.user_b), (m.user_b, m.user_a)) as u(me, other)
     where m.created_at between now() - interval '7 days' and now() - interval '6 days'
       and not exists (select 1 from public.messages x where x.match_id = m.id)
       and not private.is_blocked_between(u.me, u.other)
       and not exists (select 1 from public.chat_reminders c
                        where c.match_id = m.id and c.user_id = u.me and c.kind = 'expiring')
  loop
    perform private.queue_push(r.me, 'expiring', jsonb_build_object(
      'match_id', r.match_id, 'name', (select display_name from public.profiles where id = r.other)));
    insert into public.chat_reminders (match_id, user_id, kind) values (r.match_id, r.me, 'expiring')
      on conflict (match_id, user_id, kind) do update set sent_at = now();
    sent := sent + 1;
  end loop;

  -- The last message came from the other person more than 24 hours ago (and less than 14 days).
  for r in
    select m.id as match_id, lm.sender_id as other,
           case when m.user_a = lm.sender_id then m.user_b else m.user_a end as me
      from public.matches m
      join lateral (select sender_id, created_at from public.messages x
                     where x.match_id = m.id order by created_at desc limit 1) lm on true
     where lm.created_at between now() - interval '14 days' and now() - interval '24 hours'
  loop
    continue when private.is_blocked_between(r.me, r.other);
    continue when exists (select 1 from public.chat_reminders c
                           where c.match_id = r.match_id and c.user_id = r.me and c.kind = 'your_turn'
                             and c.sent_at > now() - interval '24 hours');
    -- Only once per unanswered message: skip if we already reminded after that message.
    continue when exists (select 1 from public.chat_reminders c
                           where c.match_id = r.match_id and c.user_id = r.me and c.kind = 'your_turn'
                             and c.sent_at > (select max(created_at) from public.messages x where x.match_id = r.match_id));
    perform private.queue_push(r.me, 'your_turn', jsonb_build_object(
      'match_id', r.match_id, 'name', (select display_name from public.profiles where id = r.other)));
    insert into public.chat_reminders (match_id, user_id, kind) values (r.match_id, r.me, 'your_turn')
      on conflict (match_id, user_id, kind) do update set sent_at = now();
    sent := sent + 1;
  end loop;
  return sent;
end $$;
revoke all on function private.send_chat_reminders() from public, anon, authenticated;

-- 16:10 UTC = 17:10 or 18:10 Berlin: a good time to answer messages.
select cron.schedule('chat-reminders', '10 16 * * *', $$select private.send_chat_reminders()$$);

-- The match list hides expired matches and tells the app when a silent match expires.
create or replace function public.get_my_matches()
 returns jsonb
 language sql
 stable security definer
 set search_path to 'public'
as $function$
  select coalesce(jsonb_agg(jsonb_build_object(
      'match_id', m.id,
      'matched_at', m.created_at,
      'user_id', o.id,
      'display_name', o.display_name,
      'age', extract(year from age(o.birth_date))::int,
      'city', case when o.hide_city then o.region else o.city end,
      'photo', (select ph.storage_path from public.profile_photos ph where ph.user_id = o.id order by ph.position limit 1),
      'selfie_verified', s.selfie_verified,
      'phone_verified', s.phone_verified,
      'active', private.activity_label(o.last_active_at, o.hide_activity),
      'expires_at', case when lm.created_at is null then m.created_at + interval '7 days' end,
      'last_message', lm.body,
      'last_message_at', lm.created_at,
      'last_from_me', lm.sender_id = auth.uid(),
      'unread', (select count(*) from public.messages u
                  where u.match_id = m.id and u.sender_id <> auth.uid() and u.read_at is null)
    ) order by coalesce(lm.created_at, m.created_at) desc), '[]'::jsonb)
  from public.matches m
  join public.profiles o on o.id = case when m.user_a = auth.uid() then m.user_b else m.user_a end
  join public.user_status s on s.user_id = o.id
  left join lateral (
    select body, created_at, sender_id from public.messages
     where match_id = m.id order by created_at desc limit 1
  ) lm on true
  where auth.uid() in (m.user_a, m.user_b) and not s.is_banned
    and (lm.created_at is not null or m.created_at >= now() - interval '7 days');
$function$;
