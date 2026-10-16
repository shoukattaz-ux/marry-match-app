-- Phase 9: moderator tools. Read functions check private.is_admin(); every change goes through
-- the moderate-user Edge Function, which also locks the login (Auth ban) and calls
-- moderation_apply() with the service role. Every change is written to audit_log.

-- Overview numbers for the admin dashboard.
create or replace function public.admin_stats() returns jsonb
language plpgsql stable security definer set search_path = public as $$
begin
  if not private.is_admin() then
    raise exception 'not_allowed';
  end if;
  return jsonb_build_object(
    'users_total',     (select count(*) from public.user_status),
    'new_today',       (select count(*) from auth.users where created_at >= date_trunc('day', now())),
    'active_7d',       (select count(*) from public.profiles where last_active_at > now() - interval '7 days'),
    'profiles_done',   (select count(*) from public.profiles where onboarding_done),
    'verified',        (select count(*) from public.user_status where selfie_verified),
    'pending_selfies', (select count(*) from public.verification_requests where status = 'pending' and submitted_at is not null),
    'open_reports',    (select count(*) from public.reports where status = 'open'),
    'banned',          (select count(*) from public.user_status where is_banned),
    'needs_review',    (select count(*) from public.user_status where needs_review and not is_banned)
  );
end $$;

-- Reports with names and, for message reports, the reported message (if it still exists).
create or replace function public.admin_reports(p_status text default 'open') returns table (
  report_id uuid, reason text, details text, status text, created_at timestamptz,
  reporter_id uuid, reporter_name text,
  reported_id uuid, reported_name text, reported_age int, reported_city text,
  reported_banned boolean, reported_open_reports int, message_body text
)
language plpgsql stable security definer set search_path = public as $$
#variable_conflict use_column
begin
  if not private.is_admin() then
    raise exception 'not_allowed';
  end if;
  return query
  select r.id, r.reason, r.details, r.status, r.created_at,
         r.reporter_id, coalesce(rp.display_name, ''),
         r.reported_id, coalesce(dp.display_name, ''),
         coalesce(extract(year from age(dp.birth_date))::int, 0), coalesce(dp.city, ''),
         coalesce(ds.is_banned, false),
         (select count(*)::int from public.reports x where x.reported_id = r.reported_id and x.status = 'open'),
         (select m.body from public.messages m where m.id = r.message_id)
    from public.reports r
    left join public.profiles rp on rp.id = r.reporter_id
    left join public.profiles dp on dp.id = r.reported_id
    left join public.user_status ds on ds.user_id = r.reported_id
   where r.status = coalesce(p_status, 'open')
   order by (r.reason in ('underage', 'scam', 'harassment')) desc, r.created_at
   limit 200;
end $$;

-- Search by name, email or account id. Empty query lists the newest accounts.
create or replace function public.admin_search_users(p_query text default '') returns table (
  user_id uuid, display_name text, email text, age int, city text, created_at timestamptz,
  selfie_verified boolean, is_banned boolean, is_admin boolean, needs_review boolean,
  respect_score int, open_reports int
)
language plpgsql stable security definer set search_path = public as $$
#variable_conflict use_column
declare
  q text := trim(coalesce(p_query, ''));
begin
  if not private.is_admin() then
    raise exception 'not_allowed';
  end if;
  return query
  select s.user_id, coalesce(p.display_name, ''), coalesce(u.email, '')::text,
         coalesce(extract(year from age(p.birth_date))::int, 0), coalesce(p.city, ''), u.created_at,
         s.selfie_verified, s.is_banned, s.is_admin, s.needs_review, s.respect_score,
         (select count(*)::int from public.reports x where x.reported_id = s.user_id and x.status = 'open')
    from public.user_status s
    join auth.users u on u.id = s.user_id
    left join public.profiles p on p.id = s.user_id
   where q = ''
      or s.user_id::text = q
      or p.display_name ilike '%' || q || '%'
      or u.email ilike '%' || q || '%'
   order by u.created_at desc
   limit 50;
end $$;

-- Newest moderation decisions first.
create or replace function public.admin_audit_log(p_limit int default 100) returns table (
  id bigint, created_at timestamptz, action text, actor_name text, target_user uuid, target_name text, details jsonb
)
language plpgsql stable security definer set search_path = public as $$
#variable_conflict use_column
begin
  if not private.is_admin() then
    raise exception 'not_allowed';
  end if;
  return query
  select a.id, a.created_at, a.action, coalesce(ap.display_name, ''), a.target_user, coalesce(tp.display_name, ''), a.details
    from public.audit_log a
    left join public.profiles ap on ap.id = a.actor_id
    left join public.profiles tp on tp.id = a.target_user
   order by a.created_at desc
   limit least(greatest(coalesce(p_limit, 100), 1), 500);
end $$;

-- The only way moderation changes data. Called by the moderate-user Edge Function with the
-- service role after it has checked that p_actor is an admin.
--   dismiss: close one report without action
--   warn:    close one report and lower the respect score by 20 (below 30 hides from discovery)
--   ban:     hide the account everywhere, block all writes, close all open reports about it
--   unban:   undo a ban
create or replace function public.moderation_apply(
  p_actor uuid, p_action text, p_user uuid, p_report uuid default null, p_note text default null
) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  target uuid := p_user;
  closed int := 0;
begin
  if not exists (select 1 from public.user_status where user_id = p_actor and is_admin and not is_banned) then
    raise exception 'not_allowed';
  end if;
  if p_action not in ('dismiss', 'warn', 'ban', 'unban') then
    raise exception 'bad_action';
  end if;

  if p_action in ('dismiss', 'warn') then
    update public.reports set status = case when p_action = 'dismiss' then 'dismissed' else 'actioned' end
     where id = p_report and status = 'open'
    returning reported_id into target;
    if not found then
      raise exception 'report_not_open';
    end if;
  end if;

  if target is null or not exists (select 1 from public.user_status where user_id = target) then
    raise exception 'user_not_found';
  end if;
  if target = p_actor or exists (select 1 from public.user_status where user_id = target and is_admin) then
    if p_action in ('warn', 'ban') then
      raise exception 'cannot_moderate_admin';
    end if;
  end if;

  if p_action = 'warn' then
    update public.user_status set respect_score = greatest(0, respect_score - 20) where user_id = target;
  elsif p_action = 'ban' then
    update public.user_status set is_banned = true where user_id = target;
    update public.reports set status = 'actioned' where reported_id = target and status = 'open';
    get diagnostics closed = row_count;
  elsif p_action = 'unban' then
    update public.user_status set is_banned = false where user_id = target;
  end if;

  insert into public.audit_log (actor_id, action, target_user, details)
  values (p_actor, 'user_' || p_action, target,
          jsonb_strip_nulls(jsonb_build_object('report_id', p_report, 'note', left(p_note, 500),
                                               'reports_closed', nullif(closed, 0))));
  return jsonb_build_object('user_id', target);
end $$;

revoke execute on function public.moderation_apply(uuid, text, uuid, uuid, text) from public, anon, authenticated;
grant execute on function public.moderation_apply(uuid, text, uuid, uuid, text) to service_role;

revoke execute on function public.admin_stats() from anon;
revoke execute on function public.admin_reports(text) from anon;
revoke execute on function public.admin_search_users(text) from anon;
revoke execute on function public.admin_audit_log(int) from anon;
