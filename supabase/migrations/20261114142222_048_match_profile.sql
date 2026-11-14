-- The full profile of a match (tap the photo or name in a chat or in the Chats list): the same
-- card as on Discover (private.member_card, migration 045, privacy settings apply). Only for the
-- two people of a match (not blocked, the other not banned) and for admins.

create or replace function public.get_match_profile(p_user uuid) returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare
  uid uuid := auth.uid();
begin
  if uid is null then
    raise exception 'not_signed_in';
  end if;
  if not private.is_admin() and (
       not exists (select 1 from public.matches m
                    where m.user_a = least(uid, p_user) and m.user_b = greatest(uid, p_user))
       or private.is_blocked_between(uid, p_user)
       or exists (select 1 from public.user_status s where s.user_id = p_user and s.is_banned)) then
    raise exception 'not_allowed';
  end if;
  return private.member_card(p_user);
end $$;
grant execute on function public.get_match_profile(uuid) to authenticated;
