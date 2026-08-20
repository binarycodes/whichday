# Drafts

## Decided

A poll exists from the moment it is named, so leaving the create flow half-finished
leaves a `DRAFT` behind. Those are listed on their own, between the polls that are
out with the team and the settled ones — a draft is further along than nothing and
further back than sent, and the list reads in that order.

Only the person who named it sees it. `PollService.draftPolls` filters by organizer
as well as by state: a draft has been shown to nobody, so it is not a poll anybody
else has business seeing.

They take the quiet list shape the settled polls use rather than a card, because a
draft is not something to answer and should not look like something to answer. Two
actions: **Edit**, which resumes at the day picker, and **Delete**.

## What this replaced

Drafts were in the main list, mixed in with live polls. An abandoned one showed a
dash where a date goes and read "0 days on the table", and the headline counted it —
"3 polls need you" when only one did. `openPolls` now excludes them, which fixes the
count as a consequence rather than as a special case.

## Deleting asks on the row

Tapping Delete turns the row into "Delete this draft? · Keep · Delete". No overlay,
because the question is about one line and is short enough to ask there; no undo,
because the confirmation is what makes the tap safe.

**Only a draft can be deleted.** `deleteDraft` refuses anything that has been sent:
a live poll has answers in it and people waiting on it, and discarding one is a
decision this does not make. If it should be possible, it wants its own thinking —
what happens to the answers, and whether the invitees are told.

## A bug this uncovered

`PollsView`, `NewPollView` and `InviteeSearchView` built their content in the
constructor. Vaadin reuses a view instance when the route asked for is the one
already showing, so the constructor never ran again and the screen kept whatever it
drew the first time. The poll list could be stale: create a poll, come back, and the
new one was missing.

All three now build in `beforeEnter`, which runs on every navigation. The screens
that extend `PollScreen` were always right — they had to read a route parameter, so
they already built there.
