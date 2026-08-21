# A poll that has gone out cannot be called off

**Severity:** medium — nothing is lost, but a poll that should not exist stays in
seven people's lists until somebody locks a day just to be rid of it.

## What happens

`deleteDraft` refuses anything that has been sent, and there is no other way to
remove a poll. So an organizer who sends the wrong thing — the wrong days, the wrong
people, a duplicate of one a colleague already ran — has no way to withdraw it. The
poll sits in every invitee's list asking to be answered.

The only exits are to lock a day nobody wants, which files it under Settled and
tells everybody a date was chosen, or to leave it open until its closing date passes
and it becomes a closed poll nobody can do anything with either
([`0004-a-closed-poll-is-a-dead-end.md`](0004-a-closed-poll-is-a-dead-end.md)).

Together with [`0007-a-forgotten-invitee-cannot-be-added.md`](0007-a-forgotten-invitee-cannot-be-added.md)
this makes a sent poll close to immutable: nobody can be added to it, and it cannot
be withdrawn.

## Why it is like this

`deleteDraft`'s refusal is the right default and was written deliberately: a live
poll has answers in it and people waiting on it, and throwing that away silently is
not a decision the service should make on its own. What was never built is the
version that is not silent.

## What fixing it looks like

The refusal should stay and a cancel should sit beside it, because they are different
things. Two questions to settle first, and they are the reason this was not done at
the time:

- **What happens to the answers.** Cancelling is not deleting: six people spent
  attention on it, and a row that vanishes reads as a bug. A `CANCELLED` state that
  keeps the poll readable and stops it asking for anything is probably the answer,
  which makes it a migration and a fourth state rather than a delete.
- **Whether the invitees are told.** They should be, and nothing is ever actually
  sent ([`0003-nothing-is-ever-actually-sent.md`](0003-nothing-is-ever-actually-sent.md)),
  so today the organizer has to say so by hand — the same shape as every other thing
  this application would like to tell somebody.

Only the organizer may do it, which the service already knows how to enforce.
