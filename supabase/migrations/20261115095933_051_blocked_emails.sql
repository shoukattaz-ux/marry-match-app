-- Deleting a banned account (owner, admin panel) can also block its e-mail address from signing
-- up again. Only a SHA-256 fingerprint of the lower-case address is kept, never the address.
-- Written by the admin-delete-account function (service role); nobody else can read or write it.

create table if not exists public.blocked_emails (
  email_hash text primary key,
  created_at timestamptz not null default now(),
  created_by uuid references auth.users(id) on delete set null
);
alter table public.blocked_emails enable row level security;

-- Sign-up: a blocked address is refused before the location pass is used up.
do $$
declare
  def text := pg_get_functiondef('public.handle_new_user()'::regprocedure);
  changed text;
begin
  if position('blocked_emails' in def) > 0 then
    return;
  end if;
  changed := replace(def,
    '  -- Checked before the location pass is used up, so the pass stays valid for a woman''s sign-up.',
    '  -- An address blocked when a banned account was deleted (only its fingerprint is stored).
  if exists (select 1 from public.blocked_emails
              where email_hash = encode(sha256(convert_to(lower(btrim(coalesce(new.email, ''''))), ''UTF8'')), ''hex'')) then
    raise exception ''signup_email_blocked'';
  end if;

  -- Checked before the location pass is used up, so the pass stays valid for a woman''s sign-up.');
  if changed = def then
    raise exception 'blocked e-mail check not added to handle_new_user';
  end if;
  execute changed;
end $$;
