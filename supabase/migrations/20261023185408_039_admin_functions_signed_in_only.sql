-- Admin functions already refuse everyone without admin rights; visitors who are not signed in
-- cannot call them at all any more (Supabase security advisor). signup_status stays public: the
-- sign-up screen asks it before an account exists.
revoke execute on function public.admin_audit_log(integer), public.admin_reports(text),
  public.admin_search_users(text), public.admin_stats() from public, anon;
grant execute on function public.admin_audit_log(integer), public.admin_reports(text),
  public.admin_search_users(text), public.admin_stats() to authenticated;

-- Account deletion removes a sender's admin messages; an index keeps that fast.
create index if not exists support_messages_sender on public.support_messages (sender_id);
