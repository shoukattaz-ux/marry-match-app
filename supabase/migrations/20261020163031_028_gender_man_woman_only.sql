-- Only "man" and "woman" remain; a man looks for a woman and a woman for a man (see 027).
alter table public.profiles drop constraint profiles_gender_check;
alter table public.profiles add constraint profiles_gender_check check (gender in ('woman', 'man'));
alter table public.profiles drop constraint profiles_looking_for_check;
alter table public.profiles add constraint profiles_looking_for_check check (looking_for in ('woman', 'man'));
