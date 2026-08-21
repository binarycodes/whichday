# A poll stores addresses, and one table holds the names

## Decided

A poll row, an invitation and a ballot identify a person by email address and nothing
else. A single `account (email primary key, name)` table is the one place a name
lives, written when somebody signs in and never seeded.

A `Person` handed to a screen is assembled on read: the name from `account` if there
is a row, and `Person.outsider(address)` if there is not. `avatarTone` is recomputed
from the address rather than stored — both `Person` factories already derive it that
way, so a column would only give a stored tone the chance to disagree with a computed
one.

## Why

**An invitee may have no account at all.** Somebody invited by email who has never
signed in is a person this application fully supports — that is the whole model in
[`0005-team-membership.md`](0005-team-membership.md) — so there is no account id for a
foreign key to point at, and `poll_invitee.email` deliberately has no foreign key to
`account.email`.

**A copy per row is a name that goes stale in some places and not others.** The
alternative was `email`, `name` and `avatar_tone` on every invitee row and every
ballot row, which is what the in-memory store held. That had a live bug in it:
`Poll.awaiting()` and `Poll.ballotOf()` compare whole `Person` records, so somebody
invited before they had an account was `("bob@example.com", "")` in the invite list
and `("bob@example.com", "Bob Smith")` on their own ballot once they signed in and
voted — two records that are not equal, so **a person who had answered read as still
awaiting**. It was transient only because nothing survived a restart. Persisting the
copies would have made it permanent.

Assembling from one row per address closes it by construction: every mention of an
address inside one snapshot comes from the same lookup, so the records are equal.

## Consequences

- **A late signup gets its name.** An address invited before its owner ever signed in
  showed as an address forever; now it shows their name from their first sign-in
  onward, on polls that predate the account.
- **A corrected name is corrected everywhere**, because there is one copy. `remember`
  writes nothing when the name has not changed, which matters because it runs on every
  read of who is looking.
- **`draftPolls` got more correct.** Its "only mine" filter is an address match in the
  query now, where it used to compare whole people — so a draft whose author has since
  corrected their name is no longer invisible to its own author.
- `PollService` takes a `PersonLookup` rather than the whole `AccountDirectory`: it
  needs to turn addresses into people and has no business remembering anybody or
  searching. One bulk lookup serves a whole list screen, so the account table is read
  once per screen rather than once per person.
- Every write normalises the address, because the address is the identity now.
  `Person.outsider` does not normalise its argument, so without that a person built
  from a mixed-case address would be stored as a row nothing could find again.
- A test fixture that votes as people with accounts has to record the accounts first.
  `Sample.signedInBefore` is that step, and a poll fixture without it has a team of
  nameless addresses — which is correct, and was worth one comment in each test.
