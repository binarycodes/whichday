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

## What fixing it looks like

A scheduled sweep over open polls whose closing date has passed, doing whatever the
product wants done. It depends on
[`0003-nothing-is-ever-actually-sent.md`](0003-nothing-is-ever-actually-sent.md) —
a trigger with nothing to send is not worth building. A sweep can now record what it
has already done, because there is a database for it to record it in.
