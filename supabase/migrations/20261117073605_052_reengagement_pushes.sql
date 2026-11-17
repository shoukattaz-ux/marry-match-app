-- Pushes that bring members back, all based on real activity:
--  * "Sara likes you / liked your photo": the like push names the person and what was liked
--    (the Likes tab shows who it is anyway).
--  * likes_waiting: away for a day with unseen likes, at most every 2 days.
--  * daily_ready: away for 3 days (and less than 60), at most once a week.
-- The two reminders follow the new "Reminders" setting (notify_reminders, on by default) and
-- are sent at most one per person per day, from a daily job in the early evening.

alter table public.profiles add column if not exists notify_reminders boolean not null default true;

create table if not exists public.reengage_pushes (
  user_id uuid not null references auth.users(id) on delete cascade,
  kind text not null check (kind in ('likes_waiting', 'daily_ready')),
  sent_at timestamptz not null default now(),
  primary key (user_id, kind)
);
alter table public.reengage_pushes enable row level security;

do $$
declare
  def text;
  changed text;
begin
  def := pg_get_functiondef('private.push_on_like()'::regprocedure);
  if position('''what''' in def) = 0 then
    changed := replace(def, 'perform private.queue_push(new.to_user, ''like'', ''{}''::jsonb);',
      'perform private.queue_push(new.to_user, ''like'', jsonb_build_object(
      ''name'', (select display_name from public.profiles where id = new.from_user),
      ''what'', new.target_type));');
    if changed = def then raise exception 'name not added to push_on_like'; end if;
    execute changed;
  end if;

  def := pg_get_functiondef('private.queue_push(uuid,text,jsonb)'::regprocedure);
  if position('notify_reminders' in def) = 0 then
    changed := replace(def, '                     when ''like'' then p.notify_likes',
      '                     when ''like'' then p.notify_likes
                     when ''likes_waiting'' then p.notify_reminders
                     when ''daily_ready'' then p.notify_reminders');
    if changed = def then raise exception 'reminders not added to queue_push'; end if;
    execute changed;
  end if;
end $$;

-- Daily job: queues the reminders and returns how many were sent (at most 500 per kind).
create or replace function private.send_reengagement() returns int
language plpgsql security definer set search_path = public as $$
declare
  r record;
  sent int := 0;
begin
  -- Unseen likes, away for at least a day.
  for r in
    select p.id, count(*)::int as waiting
      from public.profiles p
      join public.user_status s on s.user_id = p.id
      join public.likes l on l.to_user = p.id
     where p.onboarding_done and not s.is_banned
       and coalesce(p.last_active_at, '-infinity') < now() - interval '24 hours'
       and l.created_at > coalesce(p.likes_seen_at, '-infinity')
       and private.like_listed(l.from_user)
       and private.open_like(l.from_user, p.id)
       and not exists (select 1 from public.reengage_pushes x
                        where x.user_id = p.id and x.kind = 'likes_waiting' and x.sent_at > now() - interval '2 days')
     group by p.id
     limit 500
  loop
    perform private.queue_push(r.id, 'likes_waiting', jsonb_build_object('count', r.waiting));
    insert into public.reengage_pushes (user_id, kind) values (r.id, 'likes_waiting')
      on conflict (user_id, kind) do update set sent_at = now();
    sent := sent + 1;
  end loop;

  -- Away for 3 to 60 days: new profiles are waiting. Not on a day they got the likes reminder.
  for r in
    select p.id
      from public.profiles p
      join public.user_status s on s.user_id = p.id
     where p.onboarding_done and not s.is_banned and s.country_ok
       and p.last_active_at between now() - interval '60 days' and now() - interval '3 days'
       and not exists (select 1 from public.reengage_pushes x
                        where x.user_id = p.id and (
                              (x.kind = 'daily_ready' and x.sent_at > now() - interval '7 days')
                           or (x.kind = 'likes_waiting' and x.sent_at > now() - interval '20 hours')))
     limit 500
  loop
    perform private.queue_push(r.id, 'daily_ready', '{}'::jsonb);
    insert into public.reengage_pushes (user_id, kind) values (r.id, 'daily_ready')
      on conflict (user_id, kind) do update set sent_at = now();
    sent := sent + 1;
  end loop;
  return sent;
end $$;

-- 17:40 UTC = 18:40 or 19:40 Berlin: after work, a good time to look at profiles.
select cron.schedule('reengagement', '40 17 * * *', $$select private.send_reengagement()$$);
