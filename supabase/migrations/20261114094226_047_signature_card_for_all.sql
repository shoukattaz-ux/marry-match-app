-- Signature Card for every selfie-verified member (men too), and two tags fewer:
-- "Friendship first" and "Honest conversations" are no longer offered (no saved card used them).

create or replace function private.card_reasons() returns text[]
language sql immutable as $$
  select array['marriage_soon','shared_faith','family_first','serious_only','partner_for_life',
               'start_family','shared_values','new_chapter','home_in_germany','family_blessing'];
$$;

-- Shown while the member is selfie-verified and not banned (any gender).
create or replace function private.card_unlocked(p_user uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.user_status s
     where s.user_id = p_user and s.selfie_verified and not s.is_banned);
$$;

do $$
declare
  def text := pg_get_functiondef('public.save_profile_card(text,text,text[],text,text)'::regprocedure);
  changed text;
begin
  if position('card_women_only' in def) = 0 then
    return;
  end if;
  changed := replace(def, '  if not exists (select 1 from public.profiles where id = uid and gender = ''woman'') then
    raise exception ''card_women_only'';
  end if;
', '');
  if changed = def then
    raise exception 'women-only check not removed from save_profile_card';
  end if;
  execute changed;
end $$;
