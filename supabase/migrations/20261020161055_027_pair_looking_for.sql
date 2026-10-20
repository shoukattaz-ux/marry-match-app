-- A man looks for a woman and a woman for a man; the app only offers that choice, and the server
-- sets it the same way so no other combination can be saved.
create or replace function private.pair_looking_for() returns trigger
language plpgsql set search_path = public as $$
begin
  if new.gender = 'man' then
    new.looking_for := 'woman';
  elsif new.gender = 'woman' then
    new.looking_for := 'man';
  end if;
  return new;
end $$;
revoke all on function private.pair_looking_for() from public, anon, authenticated;

create trigger trg_pair_looking_for
  before insert or update of gender, looking_for on public.profiles
  for each row execute function private.pair_looking_for();
