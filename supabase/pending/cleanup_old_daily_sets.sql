-- NOT APPLIED: deleting rows needs a confirmation that did not reach the owner from this session.
-- Optional housekeeping: run it once in the Supabase SQL editor to keep only 30 days of daily sets
-- (photo access only needs today's and yesterday's sets).
create or replace function private.cleanup_old_daily_sets() returns int
language sql security definer set search_path = public as $$
  with gone as (delete from public.daily_sets where set_date < private.current_set_date() - 30 returning 1)
  select count(*)::int from gone;
$$;
revoke all on function private.cleanup_old_daily_sets() from public, anon, authenticated;
select cron.schedule('cleanup-daily-sets', '20 3 * * *', $$select private.cleanup_old_daily_sets()$$);
