# Who may see a poll, and who may change it

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
| `send`, `closeOn`, `replaceCandidateDays`, `acceptProposal`, `allowAlternatives`, `lock`, `addInvitee`, `deleteDraft` | the organizer alone |

Three permissions, then, and they nest: seeing the poll needs an invitation, answering
it needs an invitation, and changing it needs to be the person who called it.

`plannedClosing` and `latestClosingDay` are the exception, and deliberately: they are
reads, they say only what the ballot already shows, and the one screen that calls them
now turns a non-organizer away at the door.

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

## The refusals say different amounts, on purpose

A stranger must not learn that the poll is real. Somebody who is on the poll already
knows, so refusing them by name costs nothing and explains everything. So
`requireOrganizer` has three answers rather than two: the organizer proceeds, an
invitee gets `NotTheOrganizerException` naming the poll and the address, and anybody
else gets the same `IllegalArgumentException` an id nobody issued gets.

Getting that ordering wrong is easy, and writing the test found two places where it
was wrong:

- `requireOrganizer` loaded the poll before asking who was calling, so **a stranger
  calling `lock` was refused by name** — which told her the poll existed.
- `castVote` checked that the poll was open *before* checking the caller was invited,
  so **a stranger voting on a closed poll was told it had closed**. Invitation now
  comes first, and `requireOpen` takes a poll rather than an id so it cannot be called
  any earlier.

## The refusal to a stranger says nothing

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
- **`isOrganizer` used to gate nothing at all.** It existed on the presenter and had
  exactly one caller — `BallotView` deciding where to navigate after a submit — so the
  results screen offered "Lock in Thursday" and "Add it" to every invitee, and the
  service took both without asking. `/poll/:id/days` and `/poll/:id/share` had no
  redirect either, so any invitee could edit the days or re-send the poll. All of that
  is closed; the screens hide what is not yours as a courtesy, and the service refuses
  it as the rule.
