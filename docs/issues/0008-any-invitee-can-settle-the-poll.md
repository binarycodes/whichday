# Any invitee can settle, reopen or discard a poll

**Severity:** medium — it takes a crafted request rather than a stray click, and the
people who can do it are people the organizer chose.

## What happens

`PollService` checks that a *voter* was invited, and checks nothing about who is
allowed to act as the *organizer*. These are open to anybody who can reach the poll:

| | |
| --- | --- |
| `lock` | settle the poll on a day of their choosing |
| `closeOn` | move the closing date |
| `replaceCandidateDays` | change the days, dropping votes for any it removes |
| `acceptProposal` | put a counter-proposal on the table |
| `allowAlternatives` | turn counter-proposals on or off |
| `send` | open a draft |
| `deleteDraft` | discard it |

The screens are well behaved — `ResultsView` shows the lock button only when
`isOrganizer(poll)` — but the service is the authority and it does not ask. A hidden
button is not a check.

## Why it is like this

`PollService` grew as the store behind a single-user set of screens, and every
operation was reachable only by the person the screen was drawn for. The organizer
check has lived in `PollPresenter.isOrganizer` since then, which is the UI layer
deciding an access question.

## Why it is smaller than it was

Before
[`../clarifications/0012-only-invited-people-see-a-poll.md`](../clarifications/0012-only-invited-people-see-a-poll.md),
anybody signed in who had the link could do all of the above, and the home screen
handed out every poll in the database — so no link was needed either. Now only somebody
the organizer actually invited can reach a poll at all. That is a real reduction and
not a fix: an invitee is not the organizer, and "the seven people on this poll" is not
the same permission as "the person who called it".

## What fixing it looks like

The same shape the voter check took: the caller passes who is asking, and the service
refuses rather than trusting a hidden button. `poll.organizerEmail` is already the
column and is already indexed, and `PollPresenter` already knows the viewer, so no
view has to change — the signatures do.

Worth doing together with
[`0007-a-forgotten-invitee-cannot-be-added.md`](0007-a-forgotten-invitee-cannot-be-added.md),
since adding an invitee is an organizer action and would want the same guard on the
way in.
