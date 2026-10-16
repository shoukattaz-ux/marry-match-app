-- NOT APPLIED YET: the function deletes a row, which needs the owner's confirmation; that cannot
-- reach the owner from the coding session. Run this file once in the Supabase dashboard:
-- SQL Editor -> paste -> Run. Until then the app's "Undo" says it could not undo the pass.
--
-- Undo a pass made by mistake. Only the user's most recent pass, and only within 10 minutes,
-- so it stays a quick "oops" and not a way to go back through old decisions.
create or replace function public.undo_pass(p_to uuid) returns void
language plpgsql volatile security definer set search_path = public as $$
declare
  me uuid := auth.uid();
  last_to uuid;
  last_at timestamptz;
begin
  if me is null then raise exception 'not_signed_in'; end if;
  select to_user, created_at into last_to, last_at
    from public.passes where from_user = me order by created_at desc limit 1;
  if last_to is distinct from p_to then raise exception 'not_last_pass'; end if;
  if last_at < now() - interval '10 minutes' then raise exception 'undo_too_late'; end if;
  delete from public.passes where from_user = me and to_user = p_to;
end $$;
revoke all on function public.undo_pass(uuid) from public, anon;
grant execute on function public.undo_pass(uuid) to authenticated;
