# Where the polls live

## Decided

`PollService` keeps every poll in a `LinkedHashMap` guarded by the instance monitor.
Nothing seeds it: the first person to sign in gets an empty list, and the data the
design was drawn with lives in the test tree as `Sample`. It is an application-scoped
singleton, not session-scoped: a poll is shared by everybody who has the link,
and one store per browser would make the voting link meaningless.

Reads build immutable records on the spot. `StoredPoll` and `StoredBallot` are
package-private and never returned, so a screen holding a `Poll` holds a snapshot
rather than a window into the store — the same guarantee `CODING_CONVENTIONS.md`
§10 gets from translating entities inside the transaction that loaded them.

## Consequences

- State is lost on restart, and `spring-boot-devtools` restarts on every
  recompile. Sample data comes back; anything typed during the session does not.
- No `owner_id` scoping (§10). Any signed-in person can read any poll by its slug.
  There *is* an authenticated subject now, so this is no longer excused by the tier —
  only drafts are scoped to the person who named them. Scoping the rest is the first
  thing a persistence tier has to fix, and it can be done today.
- Nothing is transactional. `replaceCandidateDays` drops votes for withdrawn days
  as one synchronized step, which is the only place where a partial update would
  have been observable.

## The sample data moved to the tests

It used to be production seeding. Once signing in became the only way in, an account
existed because somebody authenticated — so a hard-coded Ada Lindqvist had nowhere to
live in the application, and the polls she owned had nobody to own them.

`Sample` in the test tree still builds that shape, and still anchors to the clock
rather than writing September 2026 out: the design's numerals would have put the whole
fixture in the past within the year. Six ballots over five days give 6 / 4 / 3 / 2 / 1,
with Jonas Wirtanen holding out so "Everyone but Jonas" and the nudge prompt have
something real to say.
