# A poll closes because somebody looked, not because time passed

**Severity:** low — correct today, wrong as soon as anything is expected to happen.

## What happens

Whether a poll is open is computed on read: `PollService.stateOf` compares the
closing date with the clock every time a snapshot is built. Nothing is scheduled, so
nothing happens *at* the moment a poll closes.

Today that is invisible, because the only consequence of closing is that the screens
render differently and answers are refused — both of which are reads. The state is
always right when anybody asks.

## Why it will matter

Anything that has to happen when voting ends needs a trigger rather than a
comparison: telling the organizer the poll is theirs to settle, mailing the team the
result, or auto-locking the leading day. There is nowhere to put any of that.

## What has changed since

The scheduling itself is no longer missing. Retention added `@EnableScheduling` and a
sweep that runs on a fixed delay (`RetentionSweep`, `PollService.deleteExpiredPolls`), so
there is now a place for anything that has to happen because time passed rather than
because somebody looked. What is still missing is anything to *do* at the moment a poll
closes, which is the half below.

## What fixing it looks like

A scheduled sweep over open polls whose closing date has passed, doing whatever the
product wants done. It waits on there being something for it to do: the application
sends no mail, and a trigger with nothing to send is not worth building. A sweep can now
record what it has already done, because there is a database for it to record it in.
