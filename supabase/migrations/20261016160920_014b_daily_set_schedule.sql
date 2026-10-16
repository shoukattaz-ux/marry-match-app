-- Phase 6: build everyone's daily set each morning (the app also builds a missing set on demand).
-- Morning job: builds today's set for everyone who does not have one yet.
create or replace function private.build_all_daily_sets() returns int
language plpgsql security definer set search_path = public as $$
declare
  d date := private.current_set_date();
  u record;
  total int := 0;
begin
  -- Before 06:00 Berlin time the new day has not started yet (the winter-time early run).
  if extract(hour from now() at time zone 'Europe/Berlin') < 6 then
    return 0;
  end if;
  for u in
    select p.id from public.profiles p
    join public.user_status s on s.user_id = p.id
    where p.onboarding_done and s.country_ok and not s.is_banned
      and not exists (select 1 from public.daily_sets ds where ds.user_id = p.id and ds.set_date = d)
  loop
    total := total + private.build_daily_set(u.id, d);
  end loop;
  return total;
end $$;

revoke all on function private.build_all_daily_sets() from public, anon, authenticated;

-- Shortly after 06:00 Berlin time: 04:05 and 05:05 UTC cover summer and winter time;
-- the run that falls before 06:00 Berlin time does nothing.
select cron.schedule('build-daily-sets', '5 4,5 * * *', $$select private.build_all_daily_sets()$$);
