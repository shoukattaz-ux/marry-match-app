-- An older app registering again cannot draw data-only pushes, so its token goes back to the
-- notification format.
create or replace function public.register_push_token(p_token text, p_lang text default 'en'::text)
 returns void
 language plpgsql
 security definer
 set search_path to 'public'
as $function$
begin
  perform public.register_push_token_v2(p_token, p_lang, false);
end $function$;
