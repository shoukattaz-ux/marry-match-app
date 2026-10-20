-- Profiles now answer 6 questions (was 3); photos stay at 6.
create or replace function public.enforce_profile_limits()
 returns trigger
 language plpgsql
 security definer
 set search_path to 'public'
as $function$
begin
  if tg_table_name = 'profile_photos'
     and (select count(*) from public.profile_photos where user_id = new.user_id) >= 6 then
    raise exception 'profile_photo_limit';
  end if;
  if tg_table_name = 'profile_prompts'
     and (select count(*) from public.profile_prompts where user_id = new.user_id) >= 6 then
    raise exception 'profile_prompt_limit';
  end if;
  return new;
end $function$;
