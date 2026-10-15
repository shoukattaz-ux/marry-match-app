-- Phase 7: chat rules and the chat list.

-- At most 30 messages per minute per person (spam protection), and no blank messages.
create or replace function public.enforce_message_rules() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if (select count(*) from public.messages
       where sender_id = new.sender_id and created_at > now() - interval '1 minute') >= 30 then
    raise exception 'message_rate_limit';
  end if;
  new.body := btrim(new.body);
  return new;
end $$;
revoke all on function public.enforce_message_rules() from public, anon, authenticated;

create trigger trg_message_rules before insert on public.messages
for each row execute function public.enforce_message_rules();

-- A report about a message must name a message the reported person sent in a chat the
-- reporter is part of.
alter policy "reports: file one" on public.reports
  with check (
    auth.uid() = reporter_id
    and reporter_id <> reported_id
    and (message_id is null or exists (
      select 1 from public.messages m
       where m.id = message_id and m.sender_id = reported_id and private.in_match(m.match_id)))
  );

-- The signed-in user's matches with the last message and unread count, most recent first.
create or replace function public.get_my_matches() returns jsonb
language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(jsonb_build_object(
      'match_id', m.id,
      'matched_at', m.created_at,
      'user_id', o.id,
      'display_name', o.display_name,
      'age', extract(year from age(o.birth_date))::int,
      'city', o.city,
      'photo', (select ph.storage_path from public.profile_photos ph where ph.user_id = o.id order by ph.position limit 1),
      'selfie_verified', s.selfie_verified,
      'phone_verified', s.phone_verified,
      'last_message', lm.body,
      'last_message_at', lm.created_at,
      'last_from_me', lm.sender_id = auth.uid(),
      'unread', (select count(*) from public.messages u
                  where u.match_id = m.id and u.sender_id <> auth.uid() and u.read_at is null)
    ) order by coalesce(lm.created_at, m.created_at) desc), '[]'::jsonb)
  from public.matches m
  join public.profiles o on o.id = case when m.user_a = auth.uid() then m.user_b else m.user_a end
  join public.user_status s on s.user_id = o.id
  left join lateral (
    select body, created_at, sender_id from public.messages
     where match_id = m.id order by created_at desc limit 1
  ) lm on true
  where auth.uid() in (m.user_a, m.user_b) and not s.is_banned;
$$;
