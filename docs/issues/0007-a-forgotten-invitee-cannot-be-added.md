# Nothing can add a person to a poll once it exists

**Severity:** medium — no data is at risk, but the poll is unfixable and the only way
out is to start again.

## What happens

The invite list is chosen once, in the create flow, and is read-only from then on. The
share screen shows it; nothing on any screen adds to it. `PollService.addInvitee` and
`PollPresenter.addInvitee` are both written and neither has a caller.

So an organizer who forgets somebody has no way to include them — before sending or
after — and since
[`../clarifications/0012-only-invited-people-see-a-poll.md`](../clarifications/0012-only-invited-people-see-a-poll.md)
the forgotten person cannot see the poll at all, let alone answer it. They get the
not-found screen and no explanation, because the refusal deliberately gives none.

The workaround is to discard the poll and build it again, which only works while it is
still a draft. A poll that has gone out has answers in it, and `deleteDraft` rightly
refuses to throw those away.

## Why it is like this

Until invitee-only visibility landed, the invite list was advisory: it set the
denominator on the standings, and anybody with the link could answer regardless. There
was no reason to edit it, so nothing did. Making the list authoritative is what turned
a missing feature into a dead end.

## What fixing it looks like

The service half exists, is tested, and now refuses anybody but the organizer. What is
missing is one affordance, and the question of where it belongs:

- **Before sending**, on the share screen, next to the invite list it already draws.
  This is the common case — "I forgot Sixten while setting this up" — and it needs
  nothing new: the invitee search from `/new/invitees` and `addInvitee`.
- **After sending** is the harder half, and not because of the plumbing. Every
  denominator everybody has already seen changes: a poll that read "4 of 6" yesterday
  reads "4 of 7" today, and the person added has no idea a poll is waiting on them —
  nothing is ever actually sent
  ([`0003-nothing-is-ever-actually-sent.md`](0003-nothing-is-ever-actually-sent.md)),
  so the organizer has to pass the link on by hand either way.

Doing the first half alone is worth more than it costs and leaves the second honest.
