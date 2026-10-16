-- Privacy settings that the server enforces, and the GDPR data export (Art. 15 and 20).

alter table public.profiles
  add column incognito boolean not null default false,             -- not put into new daily sets
  add column verified_viewers_only boolean not null default false, -- only shown to verified members
  add column hide_city boolean not null default false,             -- others see the Bundesland only
  add column hide_religion boolean not null default false,
  add column hide_occupation boolean not null default false;

-- Other people's profiles are only readable through get_daily_set and get_my_matches, which
-- apply the settings above. Before, any signed-in user could read every column directly.
alter policy "profiles: see finished, unblocked profiles" on public.profiles using (false);

create or replace function private.build_daily_set(p_user uuid, p_date date)
 returns integer
 language plpgsql
 security definer
 set search_path to 'public'
as $function$
declare
  n int;
begin
  insert into public.daily_sets (user_id, set_date, candidate_id, position)
  select p_user, p_date, c.id, row_number() over (order by c.score desc)
  from (
    select p.id,
      -- same marriage timeline first
        (case when p.marriage_timeline = me.marriage_timeline then 40 else 0 end)
      -- nearby: same city, then same Bundesland (no coordinates are stored)
      + (case when p.city = me.city then 30 when p.region = me.region then 20 else 0 end)
      -- shared languages
      + least(cardinality(array(select unnest(p.languages) intersect select unnest(me.languages))), 2) * 10
      -- verified people
      + (case when s.selfie_verified then 10 else 0 end)
      + (case when s.phone_verified then 5 else 0 end)
      -- recent activity
      + (case when p.last_active_at > now() - interval '7 days' then 10
              when p.last_active_at > now() - interval '30 days' then 5 else 0 end)
      -- people who already liked this user, so mutual likes can happen
      + (case when exists (select 1 from public.likes l where l.from_user = p.id and l.to_user = p_user) then 15 else 0 end)
      -- low respect score is shown less often
      - (case when s.respect_score < 60 then 30 else 0 end)
      -- small random factor so the set is not always identical
      + random() * 10 as score
    from public.profiles me
    join public.user_status ms on ms.user_id = me.id
    cross join public.profiles p
    join public.user_status s on s.user_id = p.id
    where me.id = p_user
      and me.onboarding_done and ms.country_ok and not ms.is_banned
      and p.id <> p_user
      and p.onboarding_done and s.country_ok and not s.is_banned and s.respect_score >= 30
      -- the other person's privacy settings
      and not p.incognito
      and (not p.verified_viewers_only or ms.selfie_verified)
      -- age within both people's ranges
      and extract(year from age(p.birth_date)) between me.pref_min_age and me.pref_max_age
      and extract(year from age(me.birth_date)) between p.pref_min_age and p.pref_max_age
      -- gender matches both preferences
      and (me.looking_for = 'any' or p.gender = me.looking_for)
      and (p.looking_for = 'any' or me.gender = p.looking_for)
      -- the user's own filters
      and (me.pref_region is null or p.region = me.pref_region)
      and (not me.pref_verified_only or s.selfie_verified)
      and (cardinality(me.pref_timelines) = 0 or p.marriage_timeline = any (me.pref_timelines))
      and (cardinality(me.pref_languages) = 0 or p.languages && me.pref_languages)
      -- never blocked either way, already liked, passed or matched
      and not private.is_blocked_between(p_user, p.id)
      and not exists (select 1 from public.likes l where l.from_user = p_user and l.to_user = p.id)
      and not exists (select 1 from public.passes x where x.from_user = p_user and x.to_user = p.id)
      and not exists (select 1 from public.matches m
                       where m.user_a = least(p_user, p.id) and m.user_b = greatest(p_user, p.id))
    order by score desc
    limit 10
  ) c
  on conflict do nothing;
  get diagnostics n = row_count;
  return n;
end $function$;

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

-- Everything stored about the signed-in user, as one JSON document (GDPR Art. 15 and 20).
-- Reports made about the user are left out to protect the people who reported.
create or replace function public.export_my_data() returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare
  uid uuid := auth.uid();
begin
  if uid is null then
    raise exception 'not_signed_in';
  end if;
  return jsonb_build_object(
    'exported_at', now(),
    'account', (select jsonb_build_object('id', u.id, 'email', u.email, 'created_at', u.created_at,
                                          'last_sign_in_at', u.last_sign_in_at)
                  from auth.users u where u.id = uid),
    'status', (select to_jsonb(s) - 'user_id' from public.user_status s where s.user_id = uid),
    'profile', (select to_jsonb(p) - 'id' from public.profiles p where p.id = uid),
    'photos', coalesce((select jsonb_agg(jsonb_build_object('path', ph.storage_path, 'position', ph.position)
                                            order by ph.position)
                          from public.profile_photos ph where ph.user_id = uid), '[]'::jsonb),
    'prompts', coalesce((select jsonb_agg(jsonb_build_object('key', pr.prompt_key, 'answer', pr.answer))
                           from public.profile_prompts pr where pr.user_id = uid), '[]'::jsonb),
    'consents', coalesce((select jsonb_agg(jsonb_build_object('type', c.consent_type, 'version', c.version,
                                                              'given_at', c.granted_at))
                            from public.consents c where c.user_id = uid), '[]'::jsonb),
    'selfie_checks', coalesce((select jsonb_agg(jsonb_build_object('pose', v.pose, 'status', v.status,
                                                                   'reject_reason', v.reject_reason,
                                                                   'started_at', v.created_at, 'reviewed_at', v.reviewed_at))
                                 from public.verification_requests v where v.user_id = uid), '[]'::jsonb),
    'likes_sent', coalesce((select jsonb_agg(jsonb_build_object('to', pl.display_name, 'comment', l.comment,
                                                                'at', l.created_at))
                              from public.likes l left join public.profiles pl on pl.id = l.to_user
                             where l.from_user = uid), '[]'::jsonb),
    'matches', coalesce((select jsonb_agg(jsonb_build_object(
                             'with', po.display_name,
                             'matched_at', m.created_at,
                             'messages', coalesce((select jsonb_agg(jsonb_build_object(
                                                     'from_me', msg.sender_id = uid, 'text', msg.body,
                                                     'sent_at', msg.created_at, 'read_at', msg.read_at)
                                                     order by msg.created_at)
                                                     from public.messages msg where msg.match_id = m.id), '[]'::jsonb)))
                           from public.matches m
                           left join public.profiles po on po.id = case when m.user_a = uid then m.user_b else m.user_a end
                          where uid in (m.user_a, m.user_b)), '[]'::jsonb),
    'blocked', coalesce((select jsonb_agg(jsonb_build_object('who', pb.display_name, 'at', b.created_at))
                           from public.blocks b left join public.profiles pb on pb.id = b.blocked
                          where b.blocker = uid), '[]'::jsonb),
    'reports_sent', coalesce((select jsonb_agg(jsonb_build_object('about', pr2.display_name, 'reason', r.reason,
                                                                  'details', r.details, 'status', r.status,
                                                                  'at', r.created_at))
                                from public.reports r left join public.profiles pr2 on pr2.id = r.reported_id
                               where r.reporter_id = uid), '[]'::jsonb)
  );
end $$;

revoke execute on function public.export_my_data() from public, anon;
grant execute on function public.export_my_data() to authenticated;
