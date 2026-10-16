-- pg_net lets database triggers call the send-push Edge Function (free, part of Supabase).
create extension if not exists pg_net with schema extensions;
