-- Chat themes: one theme per conversation, shared by both people (like Messenger). When one
-- of them picks a theme, the other sees it at once through Realtime.
create table public.chat_themes (
  match_id uuid primary key references public.matches(id) on delete cascade,
  theme text not null check (theme in ('classic', 'hearts', 'lofi', 'starry', 'ocean', 'sunset', 'garden', 'golden')),
  set_by uuid references auth.users(id) on delete set null,
  updated_at timestamptz not null default now()
);
alter table public.chat_themes enable row level security;

-- Only the two people of the match can read it; changes go through set_chat_theme only.
create policy "chat_themes: read mine" on public.chat_themes for select to authenticated
  using (exists (select 1 from public.matches m
                  where m.id = chat_themes.match_id and (select auth.uid()) in (m.user_a, m.user_b)));

revoke all on public.chat_themes from anon;
revoke insert, update, delete on public.chat_themes from authenticated;
grant select on public.chat_themes to authenticated;

alter publication supabase_realtime add table public.chat_themes;

create or replace function public.set_chat_theme(p_match_id uuid, p_theme text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  me uuid := auth.uid();
  other uuid;
  row public.chat_themes;
begin
  if me is null then raise exception 'not_signed_in'; end if;
  select case when m.user_a = me then m.user_b else m.user_a end into other
    from public.matches m where m.id = p_match_id and me in (m.user_a, m.user_b);
  if other is null then raise exception 'not_allowed'; end if;
  if private.is_blocked_between(me, other) then raise exception 'not_allowed'; end if;
  if p_theme not in ('classic', 'hearts', 'lofi', 'starry', 'ocean', 'sunset', 'garden', 'golden') then
    raise exception 'bad_theme';
  end if;
  insert into public.chat_themes (match_id, theme, set_by, updated_at)
  values (p_match_id, p_theme, me, now())
  on conflict (match_id) do update set theme = excluded.theme, set_by = excluded.set_by, updated_at = now()
  returning * into row;
  return jsonb_build_object('match_id', row.match_id, 'theme', row.theme, 'set_by', row.set_by, 'updated_at', row.updated_at);
end $$;
revoke all on function public.set_chat_theme(uuid, text) from public, anon;
grant execute on function public.set_chat_theme(uuid, text) to authenticated;
