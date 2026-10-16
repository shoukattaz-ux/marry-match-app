-- Chaperone (guardian) mode. A member can invite a trusted family member (for example a wali)
-- to read along in their chats with matches. Rules that keep this fair for everyone:
--   * Only the member can invite, with a one-time code (valid 48 hours, stored only as a hash).
--   * The chaperone can only read, never write, like or see photos.
--   * The chaperone sees only messages sent after they joined, so nobody's earlier words are
--     shown to a person they did not know about.
--   * Both people in a chat, and everyone who gets the member as a suggestion, see that a
--     chaperone reads along ('chaperoned' in get_daily_set and get_my_matches).
--   * The member can end it at any time; so can the chaperone.
-- Everything goes through the functions below; the table itself has no policies.

create table public.guardianships (
  id uuid primary key default gen_random_uuid(),
  ward_id uuid not null references auth.users(id) on delete cascade,
  guardian_id uuid references auth.users(id) on delete cascade,
  guardian_name text check (char_length(guardian_name) between 1 and 40),
  status text not null default 'pending' check (status in ('pending', 'active', 'ended')),
  code_hash text,
  code_expires_at timestamptz,
  created_at timestamptz not null default now(),
  accepted_at timestamptz,
  ended_at timestamptz,
  check (ward_id <> guardian_id)
);
-- One open invitation or chaperone per member.
create unique index guardianships_one_open on public.guardianships (ward_id) where status in ('pending', 'active');
create index guardianships_guardian on public.guardianships (guardian_id) where status = 'active';
create index guardianships_code on public.guardianships (code_hash) where status = 'pending';
alter table public.guardianships enable row level security;

create or replace function private.has_guardian(p_user uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.guardianships g where g.ward_id = p_user and g.status = 'active');
$$;
revoke all on function private.has_guardian(uuid) from public, anon;
grant execute on function private.has_guardian(uuid) to authenticated;

-- Creates a new code (an older unused one stops working). Returns { id, code, expires_at }.
create or replace function public.guardian_invite() returns jsonb
language plpgsql volatile security definer set search_path = public as $$
declare
  me uuid := auth.uid();
  alphabet constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';  -- 32 letters, no 0/O or 1/I
  bytes bytea := extensions.gen_random_bytes(8);
  code text := '';
  inv public.guardianships;
begin
  if me is null then raise exception 'not_signed_in'; end if;
  if not exists (select 1 from public.profiles where id = me and onboarding_done) then
    raise exception 'profile_required';
  end if;
  if private.has_guardian(me) then raise exception 'already_has_chaperone'; end if;
  for i in 0..7 loop
    code := code || substr(alphabet, get_byte(bytes, i) % 32 + 1, 1);
  end loop;
  update public.guardianships set status = 'ended', ended_at = now(), code_hash = null
   where ward_id = me and status = 'pending';
  insert into public.guardianships (ward_id, code_hash, code_expires_at)
  values (me, encode(extensions.digest(code, 'sha256'), 'hex'), now() + interval '48 hours')
  returning * into inv;
  return jsonb_build_object('id', inv.id, 'code', code, 'expires_at', inv.code_expires_at);
end $$;

-- The chaperone enters the code and their name. Returns the member's first name.
create or replace function public.guardian_accept(p_code text, p_name text) returns jsonb
language plpgsql volatile security definer set search_path = public as $$
declare
  me uuid := auth.uid();
  clean text := upper(regexp_replace(coalesce(p_code, ''), '[^A-Za-z0-9]', '', 'g'));
  who text := btrim(coalesce(p_name, ''));
  inv public.guardianships;
begin
  if me is null then raise exception 'not_signed_in'; end if;
  if char_length(who) not between 1 and 40 then raise exception 'name_required'; end if;
  select * into inv from public.guardianships
   where status = 'pending' and code_hash = encode(extensions.digest(clean, 'sha256'), 'hex')
     and code_expires_at > now()
   for update;
  if not found then raise exception 'invalid_code'; end if;
  if inv.ward_id = me then raise exception 'own_code'; end if;
  if (select count(*) from public.guardianships where guardian_id = me and status = 'active') >= 5 then
    raise exception 'too_many_members';
  end if;
  update public.guardianships
     set guardian_id = me, guardian_name = who, status = 'active', accepted_at = now(),
         code_hash = null, code_expires_at = null
   where id = inv.id;
  return jsonb_build_object('id', inv.id,
    'member_name', (select display_name from public.profiles where id = inv.ward_id));
end $$;

-- Ends an invitation or chaperone; the member and the chaperone can both do this.
create or replace function public.guardian_end(p_id uuid) returns void
language plpgsql volatile security definer set search_path = public as $$
begin
  update public.guardianships set status = 'ended', ended_at = now(), code_hash = null
   where id = p_id and status <> 'ended' and auth.uid() in (ward_id, guardian_id);
  if not found then raise exception 'not_found'; end if;
end $$;

-- For the member: the open invitation or the current chaperone, or null.
create or replace function public.get_my_chaperone() returns jsonb
language sql stable security definer set search_path = public as $$
  select jsonb_build_object('id', g.id, 'status', g.status, 'name', g.guardian_name,
                            'expires_at', g.code_expires_at, 'accepted_at', g.accepted_at)
    from public.guardianships g
   where g.ward_id = auth.uid() and g.status in ('pending', 'active')
     and (g.status = 'active' or g.code_expires_at > now());
$$;

-- For the chaperone: the members they read along for, with their current matches.
create or replace function public.get_my_wards() returns jsonb
language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(jsonb_build_object(
      'id', g.id,
      'member_id', g.ward_id,
      'member_name', w.display_name,
      'since', g.accepted_at,
      'matches', coalesce((
        select jsonb_agg(jsonb_build_object(
                 'match_id', m.id,
                 'name', o.display_name,
                 'age', extract(year from age(o.birth_date))::int,
                 'last_message_at', (select max(x.created_at) from public.messages x
                                      where x.match_id = m.id and x.created_at >= g.accepted_at))
                 order by m.created_at desc)
          from public.matches m
          join public.profiles o on o.id = case when m.user_a = g.ward_id then m.user_b else m.user_a end
         where g.ward_id in (m.user_a, m.user_b)), '[]'::jsonb)
    ) order by g.accepted_at), '[]'::jsonb)
  from public.guardianships g
  join public.profiles w on w.id = g.ward_id
  where g.guardian_id = auth.uid() and g.status = 'active';
$$;

-- For the chaperone: the last 200 messages of one of the member's chats, sent since they joined.
create or replace function public.get_ward_messages(p_match_id uuid) returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare
  ward uuid;
  since timestamptz;
begin
  select g.ward_id, g.accepted_at into ward, since
    from public.guardianships g
    join public.matches m on m.id = p_match_id and g.ward_id in (m.user_a, m.user_b)
   where g.guardian_id = auth.uid() and g.status = 'active';
  if ward is null then raise exception 'not_allowed'; end if;
  return coalesce((select jsonb_agg(jsonb_build_object('id', t.id, 'from_member', t.sender_id = ward,
                                                       'body', t.body, 'sent_at', t.created_at)
                                    order by t.created_at)
                     from (select * from public.messages
                            where match_id = p_match_id and created_at >= since
                            order by created_at desc limit 200) t), '[]'::jsonb);
end $$;

do $$
declare f text;
begin
  foreach f in array array['guardian_invite()', 'guardian_accept(text, text)', 'guardian_end(uuid)',
                           'get_my_chaperone()', 'get_my_wards()', 'get_ward_messages(uuid)'] loop
    execute format('revoke all on function public.%s from public, anon', f);
    execute format('grant execute on function public.%s to authenticated', f);
  end loop;
end $$;

