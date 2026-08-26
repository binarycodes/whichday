# Anonymous names accumulate in the account table and nothing removes them

**Severity:** low — it costs disk and nothing else, but it grows with visitors rather
than with polls and there is no sweep.

## What happens

`AnonymousViewerSession.identify` calls `AccountDirectory.remember`, which inserts a row
keyed on the minted `<uuid>-<timestamp>@whichday.anonymous` address. It has to: a poll
stores addresses and reads names from that table ([`../REQUIREMENTS.md`](../REQUIREMENTS.md)
§10), so a name that is not written there is a name nobody else on the poll ever sees.

But it is written when somebody says who they are, not when they do anything. Somebody
who opens a shared link, types a name and closes the tab leaves a row behind. So does a
crawler that fills the field. Nothing deletes it, and nothing ever matches it again —
the address belonged to a session that no longer exists.

A poll deleted as a draft takes its own rows with it (`on delete cascade`); the account
rows are not among them.

## What it does not do

It is not a leak. `AccountDirectory.matching` is the only reader that could list these,
and the screen that calls it — `/new/invitees` — is not part of anonymous mode and
forwards home. `forInvites` only resolves addresses already on a poll. So a minted
address surfaces exactly where its owner answered something, and nowhere else.

## What would fix it

Either write later or sweep. Writing later means remembering the name at the first
thing the session actually stores — creating a poll, casting a vote, declining one —
which bounds the table to people who took part. That is three call sites in
`PollPresenter` and a new dependency there, which is why it was not done in the change
that introduced the mode.

Sweeping means deleting `@whichday.anonymous` accounts that no poll, invitee row or
ballot refers to. Cheap as a query, and it needs somewhere to run from — this
application has no scheduled work at all today, which is the same gap
`0005-closing-happens-on-read.md` records.

## Where

- `src/main/java/io/binarycodes/whichday/people/ui/presenter/AnonymousViewerSession.java` — `identify`
- `src/main/java/io/binarycodes/whichday/people/service/AccountDirectory.java` — `remember`
