-- The data export includes the user's own check-ins.
create or replace function public.export_my_data() returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare
  uid uuid := auth.uid();
begin
  if uid is null then
    raise exception 'not_signed_in';
  end if;
  return jsonb_build_object(
    'exported_at', now(),
    'account', (select jsonb_build_object('id', u.id, 'email', u.email, 'created_at', u.created_at,
                                          'last_sign_in_at', u.last_sign_in_at)
                  from auth.users u where u.id = uid),
    'status', (select to_jsonb(s) - 'user_id' from public.user_status s where s.user_id = uid),
    'profile', (select to_jsonb(p) - 'id' from public.profiles p where p.id = uid),
    'photos', coalesce((select jsonb_agg(jsonb_build_object('path', ph.storage_path, 'position', ph.position)
                                            order by ph.position)
                          from public.profile_photos ph where ph.user_id = uid), '[]'::jsonb),
    'prompts', coalesce((select jsonb_agg(jsonb_build_object('key', pr.prompt_key, 'answer', pr.answer))
                           from public.profile_prompts pr where pr.user_id = uid), '[]'::jsonb),
    'consents', coalesce((select jsonb_agg(jsonb_build_object('type', c.consent_type, 'version', c.version,
                                                              'given_at', c.granted_at))
                            from public.consents c where c.user_id = uid), '[]'::jsonb),
    'selfie_checks', coalesce((select jsonb_agg(jsonb_build_object('pose', v.pose, 'status', v.status,
                                                                   'reject_reason', v.reject_reason,
                                                                   'started_at', v.created_at, 'reviewed_at', v.reviewed_at))
                                 from public.verification_requests v where v.user_id = uid), '[]'::jsonb),
    'likes_sent', coalesce((select jsonb_agg(jsonb_build_object('to', pl.display_name, 'comment', l.comment,
                                                                'at', l.created_at))
                              from public.likes l left join public.profiles pl on pl.id = l.to_user
                             where l.from_user = uid), '[]'::jsonb),
    'matches', coalesce((select jsonb_agg(jsonb_build_object(
                             'with', po.display_name,
                             'matched_at', m.created_at,
                             'messages', coalesce((select jsonb_agg(jsonb_build_object(
                                                     'from_me', msg.sender_id = uid, 'text', msg.body,
                                                     'sent_at', msg.created_at, 'read_at', msg.read_at)
                                                     order by msg.created_at)
                                                     from public.messages msg where msg.match_id = m.id), '[]'::jsonb)))
                           from public.matches m
                           left join public.profiles po on po.id = case when m.user_a = uid then m.user_b else m.user_a end
                          where uid in (m.user_a, m.user_b)), '[]'::jsonb),
    'blocked', coalesce((select jsonb_agg(jsonb_build_object('who', pb.display_name, 'at', b.created_at))
                           from public.blocks b left join public.profiles pb on pb.id = b.blocked
                          where b.blocker = uid), '[]'::jsonb),
    'reports_sent', coalesce((select jsonb_agg(jsonb_build_object('about', pr2.display_name, 'reason', r.reason,
                                                                  'details', r.details, 'status', r.status,
                                                                  'at', r.created_at))
                                from public.reports r left join public.profiles pr2 on pr2.id = r.reported_id
                               where r.reporter_id = uid), '[]'::jsonb),
    'chaperone', coalesce((select jsonb_agg(jsonb_build_object('chaperone_name', g.guardian_name, 'status', g.status,
                                                               'invited_at', g.created_at, 'accepted_at', g.accepted_at,
                                                               'ended_at', g.ended_at))
                             from public.guardianships g where g.ward_id = uid), '[]'::jsonb),
    'chaperone_for', coalesce((select jsonb_agg(jsonb_build_object('member', pw.display_name, 'my_name', g.guardian_name,
                                                                   'status', g.status, 'accepted_at', g.accepted_at,
                                                                   'ended_at', g.ended_at))
                                 from public.guardianships g left join public.profiles pw on pw.id = g.ward_id
                                where g.guardian_id = uid), '[]'::jsonb),
    'meetings', coalesce((select jsonb_agg(jsonb_build_object('with', po.display_name, 'how_it_went', mt.feeling,
                                                              'confirmed_at', mt.created_at, 'updated_at', mt.updated_at))
                            from public.meetings mt left join public.profiles po on po.id = mt.other_id
                           where mt.user_id = uid), '[]'::jsonb)
  );
end $$;

revoke execute on function public.export_my_data() from public, anon;
grant execute on function public.export_my_data() to authenticated;
