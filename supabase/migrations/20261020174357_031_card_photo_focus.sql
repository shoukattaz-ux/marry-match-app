-- Each card photo carries the framing its owner chose (focus_x, focus_y, zoom from migration 026).
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
      'interests', to_jsonb(p.interests),
      'traits', jsonb_strip_nulls(jsonb_build_object(
        'smoking', p.smoking, 'drinking', p.drinking, 'exercise', p.exercise, 'diet', p.diet,
        'pets', p.pets, 'sleep', p.sleep, 'personality', p.personality,
        'family_closeness', p.family_closeness, 'household', p.household,
        'home_after_marriage', p.home_after_marriage, 'relocate', p.relocate)),
      'height_cm', p.height_cm,
      'selfie_verified', s.selfie_verified,
      'phone_verified', s.phone_verified,
      'active', private.activity_label(p.last_active_at, p.hide_activity),
      'photos_blurred', p.blur_photos,
      'chaperoned', private.has_guardian(p.id),
      'photos', coalesce((select jsonb_agg(jsonb_build_object('id', ph.id, 'path', ph.storage_path,
                                                              'focus', jsonb_build_object('x', ph.focus_x, 'y', ph.focus_y, 'zoom', ph.zoom))
                                           order by ph.position)
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
