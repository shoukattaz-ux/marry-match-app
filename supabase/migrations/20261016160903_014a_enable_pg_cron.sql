-- pg_cron runs the morning job that prepares everyone's daily suggestions (free, part of Supabase).
create extension if not exists pg_cron with schema pg_catalog;
