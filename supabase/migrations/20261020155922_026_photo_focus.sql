-- Photo framing: where the round avatar shows the main photo. focus_x/focus_y run from -1 (left/top)
-- to 1 (right/bottom), 0 is the middle; zoom 1 shows the whole circle-filling crop, up to 4x.
-- The owner sets it with "Adjust photo"; the profile, match list and chat header use it.
alter table public.profile_photos
  add column if not exists focus_x real not null default 0 check (focus_x between -1 and 1),
  add column if not exists focus_y real not null default 0 check (focus_y between -1 and 1),
  add column if not exists zoom real not null default 1 check (zoom between 1 and 4);

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
      'photo_focus', (select jsonb_build_object('x', ph.focus_x, 'y', ph.focus_y, 'zoom', ph.zoom)
                        from public.profile_photos ph where ph.user_id = o.id order by ph.position limit 1),
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
