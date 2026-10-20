-- Habits, interests and values (onboarding steps 7-9) for better match ranking.
-- The allowed answers match app/src/main/java/com/example/model/MatchTraits.kt.
alter table public.profiles
  add column drinking text check (drinking in ('never', 'socially', 'regularly')),
  add column exercise text check (exercise in ('rarely', 'sometimes', 'often', 'daily')),
  add column diet text check (diet in ('anything', 'vegetarian', 'vegan', 'pescatarian')),
  add column pets text check (pets in ('have_pets', 'like_pets', 'no_pets', 'allergic')),
  add column sleep text check (sleep in ('early_bird', 'night_owl', 'flexible')),
  add column personality text check (personality in ('introvert', 'ambivert', 'extrovert')),
  add column family_closeness text check (family_closeness in ('very_close', 'close', 'independent')),
  add column household text check (household in ('traditional', 'shared', 'flexible')),
  add column home_after_marriage text check (home_after_marriage in ('city', 'countryside', 'flexible')),
  add column relocate text check (relocate in ('yes', 'maybe', 'no'));

alter table public.profiles add constraint profiles_smoking_check
  check (smoking in ('non_smoker', 'occasionally', 'smoker'));

alter table public.profiles add constraint profiles_interests_check check (
  cardinality(interests) <= 10 and interests <@ array[
    'cooking', 'baking', 'travel', 'hiking', 'nature', 'cycling', 'running', 'gym',
    'football', 'swimming', 'yoga', 'dancing', 'music', 'concerts', 'reading', 'writing',
    'art', 'photography', 'movies', 'series', 'gaming', 'tech', 'cars', 'gardening',
    'volunteering', 'family_time', 'board_games', 'coffee', 'fashion', 'learning_languages',
    'animals', 'theatre', 'museums'
  ]::text[]);

-- Onboarding now has 13 steps; anyone midway past step 6 continues at the first new step.
alter table public.profiles drop constraint profiles_onboarding_step_check;
update public.profiles set onboarding_step = 7 where not onboarding_done and onboarding_step >= 7;
alter table public.profiles add constraint profiles_onboarding_step_check check (onboarding_step between 1 and 13);

-- How well two profiles fit on habits, interests and values (unknown answers count as 0).
create or replace function private.compat_score(a public.profiles, b public.profiles) returns int
language sql stable set search_path = public as $$
  select
      -- shared interests, up to 4
      least(cardinality(array(select unnest(a.interests) intersect select unnest(b.interests))), 4) * 6
      -- smoking: both non-smokers fit best, a non-smoker and a smoker the least
    + case when a.smoking = 'non_smoker' and b.smoking = 'non_smoker' then 10
           when (a.smoking = 'non_smoker' and b.smoking = 'smoker') or (a.smoking = 'smoker' and b.smoking = 'non_smoker') then -15
           when a.smoking = b.smoking then 5 else 0 end
    + case when (a.drinking = 'never' and b.drinking = 'regularly') or (a.drinking = 'regularly' and b.drinking = 'never') then -10
           when a.drinking = b.drinking then 6 else 0 end
    + case when a.diet = b.diet then 4 else 0 end
    + case when (a.pets = 'allergic' and b.pets = 'have_pets') or (a.pets = 'have_pets' and b.pets = 'allergic') then -15
           when a.pets in ('have_pets', 'like_pets') and b.pets in ('have_pets', 'like_pets') then 4 else 0 end
    + case when a.sleep = b.sleep or 'flexible' in (a.sleep, b.sleep) then 3 else 0 end
    + case when a.exercise = b.exercise then 3 else 0 end
    + case when a.family_closeness = b.family_closeness then 6 else 0 end
    + case when a.household = b.household then 8
           when 'flexible' in (a.household, b.household) then 3
           when a.household is not null and b.household is not null then -5 else 0 end
    + case when a.home_after_marriage = b.home_after_marriage or 'flexible' in (a.home_after_marriage, b.home_after_marriage) then 4 else 0 end
      -- different cities: someone has to be willing to move
    + case when a.city is distinct from b.city and a.relocate = 'no' and b.relocate = 'no' then -10
           when a.city is distinct from b.city and 'yes' in (a.relocate, b.relocate) then 4 else 0 end
$$;
revoke all on function private.compat_score(public.profiles, public.profiles) from public, anon, authenticated;
