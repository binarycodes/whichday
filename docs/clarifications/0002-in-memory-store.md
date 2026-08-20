# Where the polls live

## Decided

`PollService` keeps every poll in a `LinkedHashMap` guarded by the instance
monitor, seeded at construction from `SampleData`. It is an application-scoped
singleton, not session-scoped: a poll is shared by everybody who has the link,
and one store per browser would make the voting link meaningless.

Reads build immutable records on the spot. `StoredPoll` and `StoredBallot` are
package-private and never returned, so a screen holding a `Poll` holds a snapshot
rather than a window into the store — the same guarantee `CODING_CONVENTIONS.md`
§10 gets from translating entities inside the transaction that loaded them.

## Consequences

- State is lost on restart, and `spring-boot-devtools` restarts on every
  recompile. Sample data comes back; anything typed during the session does not.
- No `owner_id` scoping (§10). Every poll is visible to every viewer. That is
  correct for this tier — there is no authenticated owner to scope by — and it is
  the first thing a persistence tier has to fix, not an afterthought.
- Nothing is transactional. `replaceCandidateDays` drops votes for withdrawn days
  as one synchronized step, which is the only place where a partial update would
  have been observable.

## Sample data anchors to the clock

The design is drawn on September 2026 — Mon 14, Tue 15, Fri 18, Tue 22, Wed 23.
Writing those dates literally would have put the whole sample in the past within
the year, and the results screen would open on five expired days. `SampleData`
instead anchors to the Monday of the week after next and offsets by the same
weekday pattern, so the numerals differ from the design's while the shape — a
Monday, a Tuesday, the Friday, then the next Tuesday and Wednesday — is exactly
the one that was drawn.

The standings the design shows are reproduced exactly: six ballots over five days
giving 6 / 4 / 3 / 2 / 1, with Jonas Wirtanen holding out so that "Everyone but
Jonas" and the nudge prompt have something real to say.
