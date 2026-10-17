-- WhatsApp-style chat media: photos, voice messages and videos.
-- The database never stores file bytes: a message row only points to a file in the private
-- "chat-media" bucket at <match_id>/<message_id>/<file>. The phone uploads the file first and
-- inserts the row afterwards (with its own message id). The receiver downloads the file, marks the
-- row 'downloaded' and removes the file from the server. A daily job (edge function
-- chat-media-cleanup) expires files after 14 days and removes leftovers. Files of reported
-- messages stay on the server for the moderators.

-- 1. Message columns
alter table public.messages alter column body drop not null;
alter table public.messages
  add column type text not null default 'text' check (type in ('text', 'image', 'voice', 'video')),
  add column media_path text,
  add column thumb_path text,
  add column media_bytes int check (media_bytes between 1 and 20971520),
  add column duration_ms int check (duration_ms between 0 and 180000),
  add column media_status text check (media_status in ('uploaded', 'downloaded', 'expired')),
  add column downloaded_at timestamptz;
alter table public.messages add constraint messages_kind_check check (
  (type = 'text' and body is not null and media_path is null and thumb_path is null and media_status is null)
  or (type <> 'text' and media_path is not null and media_bytes is not null and media_status is not null
      and (body is null or char_length(body) <= 300)
      and media_path like match_id::text || '/' || id::text || '/%'
      and (thumb_path is null or thumb_path like match_id::text || '/' || id::text || '/%'))
);
create index messages_media_open on public.messages (created_at) where media_status = 'uploaded';

-- 2. Insert rules for media rows: the file must already be uploaded under this message's own
-- folder with the expected name, and the size is taken from the stored file, not the phone.
create or replace function public.enforce_message_rules()
 returns trigger
 language plpgsql
 security definer
 set search_path to 'public'
as $function$
declare
  expected text;
  stored_size bigint;
begin
  if (select count(*) from public.messages
       where sender_id = new.sender_id and created_at > now() - interval '1 minute') >= 30 then
    raise exception 'message_rate_limit';
  end if;
  if new.type = 'text' then
    new.body := btrim(new.body);
    return new;
  end if;

  new.body := nullif(btrim(coalesce(new.body, '')), '');
  new.media_status := 'uploaded';
  new.downloaded_at := null;
  expected := new.match_id::text || '/' || new.id::text || '/' ||
              case new.type when 'image' then 'image.jpg' when 'voice' then 'voice.m4a' else 'video.mp4' end;
  if new.media_path is distinct from expected then raise exception 'media_bad_path'; end if;
  if new.type = 'voice' and new.thumb_path is not null then raise exception 'media_bad_thumb'; end if;
  if new.thumb_path is not null
     and new.thumb_path <> new.match_id::text || '/' || new.id::text || '/thumb.jpg' then
    raise exception 'media_bad_thumb';
  end if;
  select (o.metadata->>'size')::bigint into stored_size
    from storage.objects o where o.bucket_id = 'chat-media' and o.name = new.media_path;
  if stored_size is null then raise exception 'media_not_uploaded'; end if;
  if new.thumb_path is not null and not exists (
       select 1 from storage.objects o where o.bucket_id = 'chat-media' and o.name = new.thumb_path) then
    raise exception 'media_not_uploaded';
  end if;
  new.media_bytes := stored_size;
  if new.type = 'video' and coalesce(new.duration_ms, 0) > 31000 then raise exception 'media_too_long'; end if;
  return new;
end $function$;

-- 3. Bucket: private, 20 MB, only the three formats the app produces.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('chat-media', 'chat-media', false, 20971520, array['image/jpeg', 'audio/mp4', 'video/mp4'])
on conflict (id) do update set public = false, file_size_limit = 20971520,
  allowed_mime_types = array['image/jpeg', 'audio/mp4', 'video/mp4'];

-- p_name is "<match_id>/<message_id>/<file>". True for the two people of that match, while no
-- one has blocked the other (blocking also removes the match).
create or replace function private.chat_media_member(p_name text) returns boolean
language plpgsql stable security definer set search_path = public as $$
declare
  parts text[] := string_to_array(p_name, '/');
  uuid_re constant text := '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$';
  m public.matches;
begin
  if auth.uid() is null or array_length(parts, 1) <> 3 or parts[1] !~ uuid_re or parts[2] !~ uuid_re then
    return false;
  end if;
  select * into m from public.matches where id = parts[1]::uuid and auth.uid() in (user_a, user_b);
  return found and not private.is_blocked_between(m.user_a, m.user_b);
end $$;

-- Upload (and re-upload on retry) only while the message does not exist yet, with a known name.
create or replace function private.chat_media_upload_ok(p_name text) returns boolean
language sql stable security definer set search_path = public as $$
  select private.chat_media_member(p_name)
     and split_part(p_name, '/', 3) in ('image.jpg', 'voice.m4a', 'video.mp4', 'thumb.jpg')
     and not exists (select 1 from public.messages where id = split_part(p_name, '/', 2)::uuid);
$$;

-- Delete: the uploader before the message exists (cancel, failed send), or the receiver after
-- downloading. Never while the message is reported.
create or replace function private.chat_media_delete_ok(p_name text, p_owner text) returns boolean
language plpgsql stable security definer set search_path = public as $$
declare
  msg public.messages;
begin
  if not private.chat_media_member(p_name) then return false; end if;
  select * into msg from public.messages where id = split_part(p_name, '/', 2)::uuid;
  if not found then
    return p_owner = auth.uid()::text;
  end if;
  if exists (select 1 from public.reports r where r.message_id = msg.id) then return false; end if;
  return msg.sender_id <> auth.uid() and msg.media_status = 'downloaded';
end $$;

revoke all on function private.chat_media_member(text) from public, anon;
revoke all on function private.chat_media_upload_ok(text) from public, anon;
revoke all on function private.chat_media_delete_ok(text, text) from public, anon;
grant execute on function private.chat_media_member(text) to authenticated;
grant execute on function private.chat_media_upload_ok(text) to authenticated;
grant execute on function private.chat_media_delete_ok(text, text) to authenticated;

create policy "chat media: read in my match" on storage.objects
  for select to authenticated
  using (bucket_id = 'chat-media' and private.chat_media_member(name));
create policy "chat media: upload in my match" on storage.objects
  for insert to authenticated
  with check (bucket_id = 'chat-media' and private.chat_media_upload_ok(name));
create policy "chat media: replace before sending" on storage.objects
  for update to authenticated
  using (bucket_id = 'chat-media' and private.chat_media_upload_ok(name) and owner_id = auth.uid()::text)
  with check (bucket_id = 'chat-media' and private.chat_media_upload_ok(name));
create policy "chat media: remove when allowed" on storage.objects
  for delete to authenticated
  using (bucket_id = 'chat-media' and private.chat_media_delete_ok(name, owner_id));

-- 4. The receiver marks a file as downloaded (then removes it), or as gone from the server.
-- Returns the paths to remove and whether removing is allowed (not while reported).
create or replace function public.mark_media_downloaded(p_message_id uuid) returns jsonb
language plpgsql volatile security definer set search_path = public as $$
declare
  msg public.messages;
begin
  select * into msg from public.messages where id = p_message_id;
  if not found or not private.in_match(msg.match_id) or msg.sender_id = auth.uid() or msg.type = 'text' then
    raise exception 'not_allowed';
  end if;
  if msg.media_status = 'uploaded' then
    update public.messages set media_status = 'downloaded', downloaded_at = now() where id = p_message_id;
  end if;
  return jsonb_build_object(
    'media_path', msg.media_path,
    'thumb_path', msg.thumb_path,
    'can_remove', not exists (select 1 from public.reports r where r.message_id = p_message_id));
end $$;

-- Called when a download finds no file: only marks it expired if the file is really gone.
create or replace function public.mark_media_missing(p_message_id uuid) returns text
language plpgsql volatile security definer set search_path = public as $$
declare
  msg public.messages;
begin
  select * into msg from public.messages where id = p_message_id;
  if not found or not private.in_match(msg.match_id) or msg.type = 'text' then
    raise exception 'not_allowed';
  end if;
  if msg.media_status = 'uploaded'
     and not exists (select 1 from storage.objects o where o.bucket_id = 'chat-media' and o.name = msg.media_path) then
    update public.messages set media_status = 'expired' where id = p_message_id;
    return 'expired';
  end if;
  return msg.media_status;
end $$;

revoke all on function public.mark_media_downloaded(uuid) from public, anon;
revoke all on function public.mark_media_missing(uuid) from public, anon;
grant execute on function public.mark_media_downloaded(uuid) to authenticated;
grant execute on function public.mark_media_missing(uuid) to authenticated;
