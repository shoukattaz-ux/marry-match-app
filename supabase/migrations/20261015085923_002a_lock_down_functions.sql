create schema if not exists private;
grant usage on schema private to authenticated;

alter function public.is_admin()                      set schema private;
alter function public.in_match(uuid)                  set schema private;
alter function public.is_blocked_between(uuid, uuid)  set schema private;

revoke all on function private.is_admin()                     from public, anon;
revoke all on function private.in_match(uuid)                 from public, anon;
revoke all on function private.is_blocked_between(uuid, uuid) from public, anon;
grant execute on function private.is_admin()                     to authenticated;
grant execute on function private.in_match(uuid)                 to authenticated;
grant execute on function private.is_blocked_between(uuid, uuid) to authenticated;

create or replace function public.make_match() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if private.is_blocked_between(new.from_user, new.to_user) then
    return new;
  end if;
  if exists (select 1 from public.likes
              where from_user = new.to_user and to_user = new.from_user) then
    insert into public.matches (user_a, user_b)
    values (least(new.from_user, new.to_user), greatest(new.from_user, new.to_user))
    on conflict do nothing;
  end if;
  return new;
end $$;

create or replace function public.mark_messages_read(p_match uuid) returns void
language plpgsql security definer set search_path = public as $$
begin
  if not private.in_match(p_match) then
    raise exception 'not in this match';
  end if;
  update public.messages
     set read_at = now()
   where match_id = p_match and sender_id <> auth.uid() and read_at is null;
end $$;

create or replace function public.received_likes_count() returns int
language sql stable security definer set search_path = public as $$
  select count(*)::int
    from public.likes l
   where l.to_user = auth.uid()
     and not private.is_blocked_between(l.from_user, l.to_user)
     and not exists (select 1 from public.matches m
                      where m.user_a = least(l.from_user, l.to_user)
                        and m.user_b = greatest(l.from_user, l.to_user));
$$;

revoke all on function public.handle_new_user() from public, anon, authenticated;
revoke all on function public.make_match()      from public, anon, authenticated;
revoke all on function public.on_block()        from public, anon, authenticated;

revoke all on function public.mark_messages_read(uuid) from public, anon;
revoke all on function public.received_likes_count()   from public, anon;
grant execute on function public.mark_messages_read(uuid) to authenticated;
grant execute on function public.received_likes_count()   to authenticated;

create or replace function public.get_badges(p_ids uuid[])
returns table (user_id uuid, selfie_verified boolean, phone_verified boolean)
language sql stable security definer set search_path = public as $$
  select s.user_id, s.selfie_verified, s.phone_verified
    from public.user_status s
   where auth.uid() is not null
     and cardinality(p_ids) <= 100
     and s.user_id = any (p_ids)
     and not private.is_blocked_between(auth.uid(), s.user_id);
$$;
revoke all on function public.get_badges(uuid[]) from public, anon;
grant execute on function public.get_badges(uuid[]) to authenticated;

create policy "server only" on public.phone_otps       for all using (false) with check (false);
create policy "server only" on public.verified_phones  for all using (false) with check (false);
