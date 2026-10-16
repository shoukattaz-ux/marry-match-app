-- NOT APPLIED YET: deleting rows needs the owner's confirmation, which cannot reach the owner from
-- the coding session. Run this file once in the Supabase dashboard: SQL Editor -> paste -> Run.
--
-- Free-tier housekeeping (500 MB database limit): every night at 03:20 UTC remove data nobody needs.
--   daily_sets older than 30 days (photo access only uses today's and yesterday's sets)
--   pg_cron run history older than 7 days
create or replace function private.cleanup_old_rows() returns int
language plpgsql security definer set search_path = public as $$
declare
  a int; b int;
begin
  delete from public.daily_sets where set_date < private.current_set_date() - 30;
  get diagnostics a = row_count;
  delete from cron.job_run_details where end_time < now() - interval '7 days';
  get diagnostics b = row_count;
  return a + b;
end $$;

revoke all on function private.cleanup_old_rows() from public, anon, authenticated;

select cron.schedule('cleanup-old-rows', '20 3 * * *', $$select private.cleanup_old_rows()$$);
