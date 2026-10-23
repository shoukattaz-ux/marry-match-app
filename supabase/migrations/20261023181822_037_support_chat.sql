-- Admin messages: an admin can write to any member (feedback, reviews, bugs) without a match.
-- The member sees the admin's own name, photo and Admin badge and can reply. Every admin can
-- read every such conversation; only its own admin writes in it. When the admin loses admin
-- rights the conversation closes for both sides. All writes go through the functions below.

create table public.support_threads (
  id uuid primary key default gen_random_uuid(),
  admin_id uuid not null references auth.users (id) on delete cascade,
  user_id uuid not null references auth.users (id) on delete cascade,
  created_at timestamptz not null default now(),
  last_message_at timestamptz not null default now(),
  -- The member turned off notifications for this conversation.
  user_muted boolean not null default false,
  unique (admin_id, user_id),
  check (admin_id <> user_id)
);
create index support_threads_user on public.support_threads (user_id);

create table public.support_messages (
  id uuid primary key default gen_random_uuid(),
  thread_id uuid not null references public.support_threads (id) on delete cascade,
  sender_id uuid not null references auth.users (id) on delete cascade,
  body text check (body is null or char_length(body) between 1 and 2000),
  -- support-media/<member id>/<file>.jpg
  photo_path text,
  created_at timestamptz not null default now(),
  read_at timestamptz,
  check (body is not null or photo_path is not null)
);
create index support_messages_thread on public.support_messages (thread_id, created_at);

alter table public.support_threads enable row level security;
alter table public.support_messages enable row level security;
revoke all on public.support_threads, public.support_messages from anon;
revoke insert, update, delete on public.support_threads, public.support_messages from authenticated;
grant select on public.support_threads, public.support_messages to authenticated;

create policy "support_threads: member or admin" on public.support_threads for select to authenticated
  using (user_id = (select auth.uid()) or (select private.is_admin()));
create policy "support_messages: member or admin" on public.support_messages for select to authenticated
  using (exists (select 1 from public.support_threads t
                  where t.id = support_messages.thread_id
                    and (t.user_id = (select auth.uid()) or (select private.is_admin()))));

alter publication supabase_realtime add table public.support_messages;

-- Open while its admin still has admin rights and is not banned.
create or replace function private.support_open(p_thread uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.support_threads t join public.user_status s on s.user_id = t.admin_id
                  where t.id = p_thread and s.is_admin and not s.is_banned);
$$;

-- Admin: the conversation with a member (made on first use).
create or replace function public.admin_open_support(p_user uuid) returns uuid
language plpgsql security definer set search_path = public as $$
declare
  me uuid := auth.uid();
  thread uuid;
begin
  if me is null or not exists (select 1 from public.user_status where user_id = me and is_admin and not is_banned) then
    raise exception 'not_allowed';
  end if;
  if p_user = me then
    raise exception 'not_yourself';
  end if;
  if not exists (select 1 from auth.users where id = p_user) then
    raise exception 'user_not_found';
  end if;
  select id into thread from public.support_threads where admin_id = me and user_id = p_user;
  if thread is null then
    insert into public.support_threads (admin_id, user_id) values (me, p_user) returning id into thread;
    insert into public.audit_log (actor_id, action, target_user, details)
    values (me, 'support_started', p_user, '{}'::jsonb);
  end if;
  return thread;
end $$;

-- Sends a message (text and/or one photo) as the member or as the conversation's admin.
create or replace function public.support_send(p_thread uuid, p_body text default null, p_photo_path text default null)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  me uuid := auth.uid();
  t public.support_threads;
  body text := nullif(trim(coalesce(p_body, '')), '');
  msg public.support_messages;
  recipient uuid;
begin
  select * into t from public.support_threads where id = p_thread;
  if t.id is null or me is null or me not in (t.user_id, t.admin_id) then
    raise exception 'not_allowed';
  end if;
  if not private.support_open(t.id) then
    raise exception 'conversation_closed';
  end if;
  if body is null and p_photo_path is null then
    raise exception 'empty_message';
  end if;
  if char_length(body) > 2000 then
    raise exception 'too_long';
  end if;
  if p_photo_path is not null and (p_photo_path not like t.user_id::text || '/%' or p_photo_path like '%..%') then
    raise exception 'bad_photo';
  end if;
  -- A member who does not answer cannot be flooded: at most 30 admin messages per day.
  if me = t.admin_id and (select count(*) from public.support_messages
                          where thread_id = t.id and sender_id = me and created_at > now() - interval '1 day') >= 30 then
    raise exception 'too_many';
  end if;
  insert into public.support_messages (thread_id, sender_id, body, photo_path)
  values (t.id, me, body, p_photo_path) returning * into msg;
  update public.support_threads set last_message_at = msg.created_at where id = t.id;

  recipient := case when me = t.admin_id then t.user_id else t.admin_id end;
  if not (recipient = t.user_id and t.user_muted) then
    perform private.queue_push(recipient, case when recipient = t.user_id then 'support' else 'support_reply' end,
      jsonb_build_object('match_id', t.id, 'name', (select display_name from public.profiles where id = me)));
  end if;
  return to_jsonb(msg);
end $$;

-- Marks the other side's messages as read (only the two people in it, not other admins).
create or replace function public.support_mark_read(p_thread uuid) returns void
language plpgsql security definer set search_path = public as $$
begin
  update public.support_messages m set read_at = now()
    from public.support_threads t
   where t.id = p_thread and m.thread_id = t.id and auth.uid() in (t.user_id, t.admin_id)
     and m.sender_id <> auth.uid() and m.read_at is null;
end $$;

create or replace function public.support_set_muted(p_thread uuid, p_muted boolean) returns void
language plpgsql security definer set search_path = public as $$
begin
  update public.support_threads set user_muted = p_muted where id = p_thread and user_id = auth.uid();
  if not found then
    raise exception 'not_allowed';
  end if;
end $$;

-- The member's conversations with admins, for the top of the chat list.
create or replace function public.my_support_threads() returns jsonb
language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(jsonb_build_object(
      'thread_id', t.id,
      'other_id', t.admin_id,
      'name', coalesce(p.display_name, ''),
      'photo', (select ph.storage_path from public.profile_photos ph where ph.user_id = t.admin_id order by ph.position limit 1),
      'photo_focus', (select jsonb_build_object('x', ph.focus_x, 'y', ph.focus_y, 'zoom', ph.zoom)
                        from public.profile_photos ph where ph.user_id = t.admin_id order by ph.position limit 1),
      'open', private.support_open(t.id),
      'muted', t.user_muted,
      'last_message', lm.body,
      'last_has_photo', lm.photo_path is not null,
      'last_message_at', t.last_message_at,
      'last_from_me', lm.sender_id = auth.uid(),
      'unread', (select count(*) from public.support_messages u
                  where u.thread_id = t.id and u.sender_id <> auth.uid() and u.read_at is null)
    ) order by t.last_message_at desc), '[]'::jsonb)
  from public.support_threads t
  left join public.profiles p on p.id = t.admin_id
  left join lateral (select body, photo_path, sender_id from public.support_messages
                      where thread_id = t.id order by created_at desc limit 1) lm on true
  where t.user_id = auth.uid()
    and exists (select 1 from public.support_messages x where x.thread_id = t.id);
$$;

-- Admin: every admin conversation (own first by time), with who wrote and who it is with.
create or replace function public.admin_support_threads() returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare
  result jsonb;
begin
  if not private.is_admin() then
    raise exception 'not_allowed';
  end if;
  select coalesce(jsonb_agg(jsonb_build_object(
      'thread_id', t.id,
      'other_id', t.user_id,
      'name', coalesce(nullif(p.display_name, ''), u.email, ''),
      'email', coalesce(u.email, ''),
      'admin_id', t.admin_id,
      'admin_name', coalesce(ap.display_name, ''),
      'mine', t.admin_id = auth.uid(),
      'photo', (select ph.storage_path from public.profile_photos ph where ph.user_id = t.user_id order by ph.position limit 1),
      'open', private.support_open(t.id),
      'last_message', lm.body,
      'last_has_photo', lm.photo_path is not null,
      'last_message_at', t.last_message_at,
      'last_from_me', lm.sender_id = t.admin_id,
      'unread', (select count(*) from public.support_messages x
                  where x.thread_id = t.id and x.sender_id = t.user_id and x.read_at is null)
    ) order by t.last_message_at desc), '[]'::jsonb) into result
  from public.support_threads t
  join auth.users u on u.id = t.user_id
  left join public.profiles p on p.id = t.user_id
  left join public.profiles ap on ap.id = t.admin_id
  left join lateral (select body, photo_path, sender_id from public.support_messages
                      where thread_id = t.id order by created_at desc limit 1) lm on true;
  return result;
end $$;

revoke all on function public.admin_open_support(uuid), public.support_send(uuid, text, text),
  public.support_mark_read(uuid), public.support_set_muted(uuid, boolean),
  public.my_support_threads(), public.admin_support_threads() from public, anon;
grant execute on function public.admin_open_support(uuid), public.support_send(uuid, text, text),
  public.support_mark_read(uuid), public.support_set_muted(uuid, boolean),
  public.my_support_threads(), public.admin_support_threads() to authenticated;
revoke all on function private.support_open(uuid) from public, anon;
-- Needed by the storage policy below (it only says whether a conversation is open).
grant execute on function private.support_open(uuid) to authenticated;

-- Photos: support-media/<member id>/<file>. The member and the admins of their conversations
-- upload there; the member and every admin can read. Deleted with the member's account.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('support-media', 'support-media', false, 5242880, array['image/jpeg'])
on conflict (id) do nothing;

create policy "support-media: upload" on storage.objects for insert to authenticated
  with check (bucket_id = 'support-media' and exists (
    select 1 from public.support_threads t
     where t.user_id::text = (storage.foldername(name))[1]
       and ((select auth.uid()) in (t.user_id, t.admin_id))
       and private.support_open(t.id)));
create policy "support-media: read" on storage.objects for select to authenticated
  using (bucket_id = 'support-media' and (
    (storage.foldername(name))[1] = (select auth.uid())::text or (select private.is_admin())));

-- Pushes: "support" to the member, "support_reply" to the admin; both follow message settings.
create or replace function private.queue_push(p_user uuid, p_kind text, p_data jsonb)
returns void
language plpgsql security definer set search_path = public as $$
declare
  wanted boolean;
begin
  select case p_kind when 'match' then p.notify_matches
                     when 'expiring' then p.notify_matches
                     when 'message' then p.notify_messages
                     when 'your_turn' then p.notify_messages
                     when 'support' then p.notify_messages
                     when 'support_reply' then p.notify_messages
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
