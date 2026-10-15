-- Phase 8 basics: limit reports so the report button cannot be used to flood moderators.

create or replace function public.enforce_report_limit() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if (select count(*) from public.reports
       where reporter_id = new.reporter_id
         and created_at > now() - interval '24 hours') >= 20 then
    raise exception 'report_limit';
  end if;
  return new;
end $$;
revoke all on function public.enforce_report_limit() from public, anon, authenticated;

create trigger trg_report_limit before insert on public.reports
for each row execute function public.enforce_report_limit();

create index if not exists idx_reports_reporter_time on public.reports (reporter_id, created_at);
