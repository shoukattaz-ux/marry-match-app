-- Chat media, part 2: the daily cleanup and small read changes.

-- The cleanup plan for the edge function chat-media-cleanup (service role only):
--   expire:   files never downloaded within 14 days
--   leftover: files of downloaded or expired messages still on the server (e.g. the receiver
--             was offline when it tried to remove them)
--   orphan:   files without a message after a day (cancelled or failed sends, removed matches)
-- Messages with an open report are skipped: moderators need the file.
create or replace function public.chat_media_cleanup_plan(p_limit int default 500) returns jsonb
language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'expire', coalesce((select jsonb_agg(jsonb_build_object('id', m.id, 'paths',
                          array_remove(array[m.media_path, m.thumb_path], null)))
                          from (select * from public.messages m
                                 where m.media_status = 'uploaded' and m.created_at < now() - interval '14 days'
                                   and not exists (select 1 from public.reports r where r.message_id = m.id and r.status = 'open')
                                 order by m.created_at limit p_limit) m), '[]'::jsonb),
    'leftover', coalesce((select jsonb_agg(o.name)
                            from (select o.name from storage.objects o
                                    join public.messages m on m.id::text = split_part(o.name, '/', 2)
                                   where o.bucket_id = 'chat-media' and m.media_status in ('downloaded', 'expired')
                                     and not exists (select 1 from public.reports r where r.message_id = m.id and r.status = 'open')
                                   limit p_limit) o), '[]'::jsonb),
    'orphan', coalesce((select jsonb_agg(o.name)
                          from (select o.name from storage.objects o
                                 where o.bucket_id = 'chat-media' and o.created_at < now() - interval '1 day'
                                   and not exists (select 1 from public.messages m where m.id::text = split_part(o.name, '/', 2))
                                 limit p_limit) o), '[]'::jsonb)
  );
$$;

create or replace function public.chat_media_mark_expired(p_ids uuid[]) returns int
language sql volatile security definer set search_path = public as $$
  with u as (
    update public.messages set media_status = 'expired'
     where id = any(p_ids) and media_status = 'uploaded' returning 1)
  select count(*)::int from u;
$$;

revoke all on function public.chat_media_cleanup_plan(int) from public, anon, authenticated;
revoke all on function public.chat_media_mark_expired(uuid[]) from public, anon, authenticated;
grant execute on function public.chat_media_cleanup_plan(int) to service_role;
grant execute on function public.chat_media_mark_expired(uuid[]) to service_role;

-- Daily at 03:40 UTC the database calls the edge function (same secret as the pushes).
create or replace function private.run_chat_media_cleanup() returns void
language plpgsql security definer set search_path = public as $$
begin
  perform net.http_post(
    url := 'https://zaixmvhfblgarpjckjhc.supabase.co/functions/v1/chat-media-cleanup',
    body := '{}'::jsonb,
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'x-push-secret', (select decrypted_secret from vault.decrypted_secrets where name = 'push_webhook_secret')));
end $$;
revoke all on function private.run_chat_media_cleanup() from public, anon, authenticated;
select cron.schedule('chat-media-cleanup', '40 3 * * *', $$select private.run_chat_media_cleanup()$$);

-- The match list shows "Photo", "Voice message" or "Video" for a media message.
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
      'chaperoned', private.has_guardian(m.user_a) or private.has_guardian(m.user_b),
      'i_met', exists (select 1 from public.meetings mt where mt.match_id = m.id and mt.user_id = auth.uid()),
      'they_met', exists (select 1 from public.meetings mt where mt.match_id = m.id and mt.user_id = o.id),
      'last_message', lm.body,
      'last_message_type', lm.type,
      'last_message_at', lm.created_at,
      'last_from_me', lm.sender_id = auth.uid(),
      'unread', (select count(*) from public.messages u
                  where u.match_id = m.id and u.sender_id <> auth.uid() and u.read_at is null)
    ) order by coalesce(lm.created_at, m.created_at) desc), '[]'::jsonb)
  from public.matches m
  join public.profiles o on o.id = case when m.user_a = auth.uid() then m.user_b else m.user_a end
  join public.user_status s on s.user_id = o.id
  left join lateral (
    select body, type, created_at, sender_id from public.messages
     where match_id = m.id order by created_at desc limit 1
  ) lm on true
  where auth.uid() in (m.user_a, m.user_b) and not s.is_banned
    and (lm.created_at is not null or m.created_at >= now() - interval '7 days');
$function$;

-- The chaperone sees that a photo, voice message or video was sent, never the file.
create or replace function public.get_ward_messages(p_match_id uuid)
 returns jsonb
 language plpgsql
 stable security definer
 set search_path to 'public'
as $function$
declare
  ward uuid;
  since timestamptz;
begin
  select g.ward_id, g.accepted_at into ward, since
    from public.guardianships g
    join public.matches m on m.id = p_match_id and g.ward_id in (m.user_a, m.user_b)
   where g.guardian_id = auth.uid() and g.status = 'active';
  if ward is null then raise exception 'not_allowed'; end if;
  return coalesce((select jsonb_agg(jsonb_build_object('id', t.id, 'from_member', t.sender_id = ward,
                                                       'body', t.body, 'type', t.type, 'sent_at', t.created_at)
                                    order by t.created_at)
                     from (select * from public.messages
                            where match_id = p_match_id and created_at >= since
                            order by created_at desc limit 200) t), '[]'::jsonb);
end $function$;
