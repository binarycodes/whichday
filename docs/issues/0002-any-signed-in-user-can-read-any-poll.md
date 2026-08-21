# Any signed-in user can read any poll from its link

**Severity:** medium — an access-control hole, but no longer one a stranger can
stumble into. It was high while the link was guessable.

## What happens

Signing in is required, but nothing checks *which* signed-in person is asking.
`PollService.poll(id)` returns any poll to anybody, so a signed-in stranger who is
given an id can open `/poll/<id>` and read the title, the invitee list, who voted for
what, and the counter-proposals.

Guessing one is no longer realistic: the id is a random `UUID`
([`../clarifications/0009-a-generated-poll-id.md`](../clarifications/0009-a-generated-poll-id.md)),
where it used to be the title — `q3-team-offsite` was a URL somebody could arrive at
by typing what a team is obviously called. That narrows this a great deal and does
not close it. A voting link is meant to be passed around, so it ends up in forwarded
mail and pasted into the wrong channel, and everyone who receives one can read
everything about the poll whether they were invited or not.

Drafts are the exception: `draftPolls` filters by organizer, so an unsent poll is
visible only to the person who named it.

## Why it is like this

Before there was a login there was no subject to scope by, and the gap was excused
by the tier. There is a subject now, so it is not excused any more — it was simply
not closed at the same time.

## What fixing it looks like

`CODING_CONVENTIONS.md` §10 is explicit: every table carries an owner and every query
is scoped by it. Applied here, a poll is readable by its organizer and its invitees
and by nobody else, and `PollService` enforces that rather than each screen
remembering to.

Note that an invitee may have no account yet — somebody invited by email who has
never signed in — so the check is by address, not by account id.

The query layer this needs now exists: every poll is a row and every read is a query,
and `V1` already indexes `poll.organizer_email` and `poll_invitee.email`. So this is a
`where` clause and a guard in `PollService.poll`, not a migration and not a rewrite.
