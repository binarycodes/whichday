# Only invited people see a poll

## Decided

A poll is visible to the person who asked and to the addresses they asked, and to
nobody else. Whoever else holds the link gets the not-found screen — the same screen,
word for word, that an id nobody issued gets.

The match is on the address. You sign in with the address you were invited at, and
nothing else works: not another address of yours, not an alias, not a colleague's
account at the same company.

Enforced in `PollService`, not on the screens:

| | |
| --- | --- |
| `poll(id, viewer)` | empty unless the viewer is the organizer or an invitee |
| `openPolls`, `settledPolls` | scoped in the query, both arms indexed |
| `draftPolls` | organizer only, as before — a draft has been shown to nobody |
| `castVote`, `decline` | refuse anybody who is not on the invitee list |

## What this replaced

Signing in was already the only way in — every route is `@PermitAll`, which in Vaadin
means authenticated — but nothing asked *which* signed-in person was asking. Two holes
came of that, and the second was the worse one:

- Anybody with the link could read the title, the whole invitee list, who voted for
  what, and every counter-proposal, and could add a vote of their own.
- **`openPolls` and `settledPolls` returned every poll in the database.** No link
  needed: the home screen listed strangers' polls to anyone who signed in.

Three strings in the interface promised the opposite of the rule and had never been
true of the implementation — "No sign-up for voters. One link, one tap.", "Nothing
found. We'll email a voting link instead.", and "They'll get a voting link by email
and can answer without signing up." All three now say that everyone signs in with the
address they were invited at.

## The refusal says nothing

A person who was not invited must not be able to tell a real poll from an imaginary
one. So the read path returns `Optional.empty()` and the write path throws the same
`IllegalArgumentException` with the same wording that an unknown id throws, and the
screen is the existing not-found screen with its existing copy.

The copy deliberately does **not** mention invitee lists. Naming the reason would be
the tell: "you are not on the list" confirms there is a list, which confirms the poll.
`PollJourneyTest.aStrangerCannotTellThePollExists` compares the two screens as text
and fails if they ever diverge.

## One loose end in the migration

`V1`'s comment above the two indexes still points at the issue file this closed, which
is gone. It stays wrong on purpose: a migration that has run is immutable, and editing
even a comment changes its checksum — Flyway then refuses to start against any database
that already applied the old bytes, which is not a trade worth making for a tidier
comment.

## Consequences

- **An account without an email claim cannot be invited.** `AuthenticatedViewerSession`
  falls back to the OIDC subject when the provider withholds an address, and a subject
  is not something an organizer can type into an invite field. Such a person can still
  create polls; they cannot be invited to anybody else's.
- **Aliases do not match.** Invited at `bob+team@example.com`, signing in as
  `bob@example.com`, you are a stranger. That is inherent to matching on the address
  and is the price of not having accounts to match on instead.
- **Nothing can add a person to a poll after it is created** — not before sending,
  not after. That was a shrug when a forgotten colleague could just use the link, and
  it is a dead end now:
  [`../issues/0007-a-forgotten-invitee-cannot-be-added.md`](../issues/0007-a-forgotten-invitee-cannot-be-added.md).
- Organizer-only actions are still open to any invitee at the service layer —
  [`../issues/0008-any-invitee-can-settle-the-poll.md`](../issues/0008-any-invitee-can-settle-the-poll.md).
  Scoping reads shrank that from "anybody with the link" to "somebody who was invited",
  which is a much smaller blast radius and not the same as closed.
