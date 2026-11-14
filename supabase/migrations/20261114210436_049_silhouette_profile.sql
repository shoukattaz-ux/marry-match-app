-- Silhouette profile: a selfie-verified woman can show a silhouette instead of her photos.
-- Matches (and admins) see her real photos; everyone else gets no photos at all, not even the
-- files (can_view_photos). Off by default; it stops by itself when her verification is reset
-- (new main photo) or she is banned, like the Signature Card.

alter table public.profiles add column if not exists silhouette boolean not null default false;

-- True when the caller must see a silhouette instead of p_owner's photos.
create or replace function private.hides_photos_from_me(p_owner uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select (auth.uid() is null or auth.uid() <> p_owner)
     and not private.is_admin()
     and exists (select 1 from public.profiles p join public.user_status s on s.user_id = p.id
                  where p.id = p_owner and p.silhouette and p.gender = 'woman'
                    and s.selfie_verified and not s.is_banned)
     and not exists (select 1 from public.matches m
                      where m.user_a = least(auth.uid(), p_owner) and m.user_b = greatest(auth.uid(), p_owner));
$$;

do $$
declare
  fn text;
  def text;
  changed text;
begin
  -- Discover cards and the full profile card (Likes tab, match profile): no photos, a flag instead.
  foreach fn in array array['public.get_daily_set()', 'private.member_card(uuid)'] loop
    def := pg_get_functiondef(fn::regprocedure);
    if position('hides_photos_from_me' in def) = 0 then
      changed := replace(def,
        '''photos'', coalesce((select jsonb_agg(jsonb_build_object(''id'', ph.id, ''path'', ph.storage_path,',
        '''silhouette'', private.hides_photos_from_me(p.id),
      ''photos'', case when private.hides_photos_from_me(p.id) then ''[]''::jsonb else coalesce((select jsonb_agg(jsonb_build_object(''id'', ph.id, ''path'', ph.storage_path,');
      changed := replace(changed,
        'from public.profile_photos ph where ph.user_id = p.id), ''[]''::jsonb),',
        'from public.profile_photos ph where ph.user_id = p.id), ''[]''::jsonb) end,');
      if changed = def or position(') end,' in changed) = 0 then
        raise exception 'silhouette not added to %', fn;
      end if;
      execute changed;
    end if;
  end loop;

  -- The photo files themselves.
  def := pg_get_functiondef('private.can_view_photos(text)'::regprocedure);
  if position('hides_photos_from_me' in def) = 0 then
    changed := replace(def,
      '     or exists (select 1 from public.user_status s where s.user_id = owner and s.is_banned) then
    return false;
  end if;',
      '     or exists (select 1 from public.user_status s where s.user_id = owner and s.is_banned) then
    return false;
  end if;
  -- Silhouette profile: only matches see her photos.
  if private.hides_photos_from_me(owner) then
    return false;
  end if;');
    if changed = def then
      raise exception 'silhouette not added to can_view_photos';
    end if;
    execute changed;
  end if;
end $$;
