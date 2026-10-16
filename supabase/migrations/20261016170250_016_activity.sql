-- "Active today / this week" labels and live "online / typing" inside an open chat.
-- No exact times are ever shown. Hiding your own activity also hides everyone else's from you.

alter table public.profiles add column hide_activity boolean not null default false;

-- 'today' (last 24 hours), 'week' (last 7 days) or null, as the signed-in user may see it.
create or replace function private.activity_label(p_last timestamptz, p_owner_hides boolean) returns text
language sql stable security definer set search_path = public as $$
  select case
    when p_owner_hides or coalesce((select hide_activity from public.profiles where id = auth.uid()), false) then null
    when p_last > now() - interval '24 hours' then 'today'
    when p_last > now() - interval '7 days' then 'week'
  end;
$$;
revoke all on function private.activity_label(timestamptz, boolean) from public, anon;

-- Called when the app opens or comes back; at most one write per user every 5 minutes.
create or replace function public.touch_activity() returns void
language sql security definer set search_path = public as $$
  update public.profiles set last_active_at = now()
   where id = auth.uid() and last_active_at < now() - interval '5 minutes';
$$;
revoke execute on function public.touch_activity() from public, anon;
grant execute on function public.touch_activity() to authenticated;

create or replace function public.get_daily_set()
 returns jsonb
 language plpgsql
 security definer
 set search_path to 'public'
as $function$
declare
  uid uuid := auth.uid();
  d date := private.current_set_date();
  result jsonb;
begin
  if uid is null then
    raise exception 'not_signed_in';
  end if;
  update public.profiles set last_active_at = now() where id = uid;
  if not exists (select 1 from public.daily_sets where user_id = uid and set_date = d) then
    perform private.build_daily_set(uid, d);
  end if;

  select jsonb_build_object(
    'set_date', d,
    'total', (select count(*) from public.daily_sets where user_id = uid and set_date = d),
    'profiles', coalesce(jsonb_agg(card order by pos), '[]'::jsonb)
  ) into result
  from (
    select ds.position as pos, jsonb_build_object(
      'id', p.id,
      'display_name', p.display_name,
      'age', extract(year from age(p.birth_date))::int,
      'city', case when p.hide_city then null else p.city end,
      'region', p.region,
      'bio', p.bio,
      'marriage_timeline', p.marriage_timeline,
      'wants_children', p.wants_children,
      'religion', case when p.hide_religion then null else p.religion end,
      'smoking', p.smoking,
      'education', p.education,
      'occupation', case when p.hide_occupation then null else p.occupation end,
      'languages', to_jsonb(p.languages),
      'height_cm', p.height_cm,
      'selfie_verified', s.selfie_verified,
      'phone_verified', s.phone_verified,
      'active', private.activity_label(p.last_active_at, p.hide_activity),
      'photos', coalesce((select jsonb_agg(jsonb_build_object('id', ph.id, 'path', ph.storage_path) order by ph.position)
                            from public.profile_photos ph where ph.user_id = p.id), '[]'::jsonb),
      'prompts', coalesce((select jsonb_agg(jsonb_build_object('id', pr.id, 'key', pr.prompt_key, 'answer', pr.answer))
                             from public.profile_prompts pr where pr.user_id = p.id), '[]'::jsonb)
    ) as card
    from public.daily_sets ds
    join public.profiles p on p.id = ds.candidate_id
    join public.user_status s on s.user_id = p.id
    where ds.user_id = uid and ds.set_date = d
      and p.onboarding_done and not s.is_banned
      and not private.is_blocked_between(uid, p.id)
      and not exists (select 1 from public.likes l where l.from_user = uid and l.to_user = p.id)
      and not exists (select 1 from public.passes x where x.from_user = uid and x.to_user = p.id)
      and not exists (select 1 from public.matches m
                       where m.user_a = least(uid, p.id) and m.user_b = greatest(uid, p.id))
  ) cards;
  return result;
end $function$;

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
  where auth.uid() in (m.user_a, m.user_b) and not s.is_banned;
$function$;

-- Live presence and "typing" use the private Realtime channel "presence-<match id>".
-- Only the two people of that match may join, and only if they share their activity.
create or replace function private.presence_topic_ok(p_topic text) returns boolean
language plpgsql stable security definer set search_path = public as $$
begin
  if p_topic is null or p_topic !~ '^presence-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then
    return false;
  end if;
  return private.in_match(substring(p_topic from 10)::uuid)
     and not coalesce((select hide_activity from public.profiles where id = auth.uid()), false);
end $$;
revoke all on function private.presence_topic_ok(text) from public, anon;
grant execute on function private.presence_topic_ok(text) to authenticated;

create policy "chat presence: match members read" on realtime.messages
  for select to authenticated
  using (realtime.messages.extension in ('presence', 'broadcast') and private.presence_topic_ok(realtime.topic()));

create policy "chat presence: match members send" on realtime.messages
  for insert to authenticated
  with check (realtime.messages.extension in ('presence', 'broadcast') and private.presence_topic_ok(realtime.topic()));
