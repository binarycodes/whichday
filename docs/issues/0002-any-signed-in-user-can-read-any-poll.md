# Any signed-in user can read any poll from its slug

**Severity:** high — it is an access-control hole, not a rough edge.

## What happens

Signing in is required, but nothing checks *which* signed-in person is asking.
`PollService.poll(slug)` returns any poll to anybody, so a signed-in stranger who
guesses or is given a slug can open `/poll/<slug>` and read the title, the invitee
list, who voted for what, and the counter-proposals.

Slugs are readable and derived from the title — `q3-team-offsite` — so guessing is
not far-fetched.

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

Worth doing with [`0001-polls-are-lost-on-restart.md`](0001-polls-are-lost-on-restart.md),
since scoping is a property of the query layer either way.
