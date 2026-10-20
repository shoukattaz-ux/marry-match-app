-- App versions that draw notifications themselves (own sound and vibration per kind) get
-- data-only pushes; older installs keep the notification pushes Android shows on its own.
alter table public.push_tokens add column data_only boolean not null default false;

-- Newer apps register here; register_push_token (older apps) stays as it is.
create function public.register_push_token_v2(p_token text, p_lang text, p_data_only boolean)
 returns void
 language plpgsql
 security definer
 set search_path to 'public'
as $function$
begin
  if auth.uid() is null then
    raise exception 'not_signed_in';
  end if;
  if p_token is null or length(p_token) not between 20 and 4096 then
    raise exception 'bad_token';
  end if;
  insert into public.push_tokens (user_id, token, platform, lang, data_only, updated_at)
  values (auth.uid(), p_token, 'android', case when p_lang = 'de' then 'de' else 'en' end, coalesce(p_data_only, false), now())
  on conflict (token) do update
    set user_id = excluded.user_id, lang = excluded.lang, platform = excluded.platform,
        data_only = excluded.data_only, updated_at = now();
end $function$;
revoke all on function public.register_push_token_v2(text, text, boolean) from public, anon;
grant execute on function public.register_push_token_v2(text, text, boolean) to authenticated;
