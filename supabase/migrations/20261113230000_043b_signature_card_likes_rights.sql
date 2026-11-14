-- Signature Card, part 2: likes may point at the card; tighter rights; removing the card.
-- (Not applied yet: waits for the confirmation in Supabase; the file name gets the real version then.)
alter table public.likes drop constraint if exists likes_target_type_check;
alter table public.likes add constraint likes_target_type_check check (target_type in ('photo','prompt','card'));
revoke insert, update, delete on public.profile_cards from anon, authenticated;

create or replace function public.delete_profile_card() returns void
language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then
    raise exception 'not_signed_in';
  end if;
  delete from public.profile_cards where user_id = auth.uid();
end $$;

revoke all on function public.get_my_profile_card() from public, anon;
revoke all on function public.save_profile_card(text, text, text[], text, text) from public, anon;
revoke all on function public.delete_profile_card() from public, anon;
grant execute on function public.get_my_profile_card() to authenticated;
grant execute on function public.save_profile_card(text, text, text[], text, text) to authenticated;
grant execute on function public.delete_profile_card() to authenticated;
revoke all on function private.signature_card(uuid) from public, anon, authenticated;
revoke all on function private.card_unlocked(uuid) from public, anon, authenticated;

