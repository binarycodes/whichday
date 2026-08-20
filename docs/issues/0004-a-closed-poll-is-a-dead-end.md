# A closed poll cannot be reopened or extended

**Severity:** low — a corner an organizer can get stuck in.

## What happens

The share screen says "You can extend it later", and while a poll is open that is
true: the organizer can move the closing date to any day up to the last one on the
table.

Once the closing date has passed the poll is `CLOSED`, and there is no way back. The
organizer's only remaining move is to lock one of the days in. If nobody answered, or
the answers do not settle anything, the poll is finished with nothing to show and no
way to give the team more time.

There is also no way to change the closing date once it has passed, because the
calendar that changes it lives on the share screen and is bounded to dates that are
still ahead.

## What fixing it looks like

Reopening is `PollService.closeOn` with the clamp relaxed to allow a `CLOSED` poll a
new date in the future, plus a way in from the results screen — which already knows
the poll is closed and already has the organizer's attention.

The question worth deciding first is whether reopening should be allowed at all, or
whether a closed poll that settled nothing should be copied into a new one instead.
Copying is cleaner: the answers to a question nobody could act on are not obviously
worth keeping.
