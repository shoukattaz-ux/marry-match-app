-- Admin "Create account" failed with signup_location_required: Supabase Auth writes app_metadata
-- (gate_bypass) only after inserting the user, so handle_new_user never saw it. Instead the
-- admin-create-account function now stores a one-time pass here (service role only: RLS on, no
-- policies, no grants) and sends it in the new user's metadata; handle_new_user accepts a valid,
-- unused pass in place of the location check and the sign-up pause.

create table if not exists public.admin_signup_passes (
  token_hash text primary key,
  created_by uuid not null references auth.users(id) on delete cascade,
  expires_at timestamptz not null default now() + interval '5 minutes',
  used_at timestamptz
);
alter table public.admin_signup_passes enable row level security;
revoke all on public.admin_signup_passes from anon, authenticated;

do $$
declare
  def text := pg_get_functiondef('public.handle_new_user()'::regprocedure);
  changed text;
begin
  if position('admin_signup_passes' in def) > 0 then
    return;
  end if;
  changed := replace(def,
    '  if coalesce((new.raw_app_meta_data ->> ''gate_bypass'')::boolean, false) then',
    '  -- a one-time pass from admin-create-account (only the service role can store one)
  update public.admin_signup_passes
     set used_at = now()
   where coalesce(meta ->> ''admin_pass'', '''') <> ''''
     and token_hash = encode(sha256(convert_to(meta ->> ''admin_pass'', ''UTF8'')), ''hex'')
     and used_at is null
     and expires_at > now();
  if found or coalesce((new.raw_app_meta_data ->> ''gate_bypass'')::boolean, false) then');
  if changed = def then
    raise exception 'admin pass not added to handle_new_user';
  end if;
  execute changed;
end $$;
