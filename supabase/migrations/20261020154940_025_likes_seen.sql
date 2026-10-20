-- "Likes You" badge: the server remembers when the user last opened the tab, so the badge only
-- counts likes that arrived after that and disappears once the tab has been viewed.
alter table public.profiles add column if not exists likes_seen_at timestamptz;

-- { total: likes without a match yet, new: those that arrived since the tab was last opened }
create or replace function public.received_likes_summary() returns jsonb
language sql stable security definer set search_path = public as $$
  with mine as (
    select l.created_at
      from public.likes l
     where l.to_user = auth.uid()
       and not private.is_blocked_between(l.from_user, l.to_user)
       and not exists (select 1 from public.matches m
                        where m.user_a = least(l.from_user, l.to_user)
                          and m.user_b = greatest(l.from_user, l.to_user))
  ), seen as (
    select coalesce((select p.likes_seen_at from public.profiles p where p.id = auth.uid()), '-infinity'::timestamptz) as at
  )
  select jsonb_build_object(
    'total', (select count(*) from mine),
    'new', (select count(*) from mine, seen where mine.created_at > seen.at)
  );
$$;

create or replace function public.mark_likes_seen() returns void
language sql volatile security definer set search_path = public as $$
  update public.profiles set likes_seen_at = now() where id = auth.uid();
$$;

revoke all on function public.received_likes_summary() from public, anon;
revoke all on function public.mark_likes_seen() from public, anon;
grant execute on function public.received_likes_summary() to authenticated;
grant execute on function public.mark_likes_seen() to authenticated;
