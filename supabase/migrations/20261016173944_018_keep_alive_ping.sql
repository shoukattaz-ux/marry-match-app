-- Free plan projects pause after 7 days without activity. A GitHub Action calls this every
-- 3 days (.github/workflows/keep-alive.yml). It reads nothing private and writes nothing.
create or replace function public.ping() returns text
language sql stable set search_path = public as $$
  select 'ok'::text;
$$;
revoke execute on function public.ping() from public;
grant execute on function public.ping() to anon, authenticated;
