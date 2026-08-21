# A poll whose days have all passed cannot be finished or restarted

**Severity:** medium — low as a nuisance, but the one way out of it silently rewrites
everybody's answer.

## What happens

The share screen promises "Voting closes Friday. You can extend it later."

While a candidate day is still ahead, that promise holds, and it holds even after the
poll has gone `CLOSED` — an organizer who closed voting on Wednesday for days running
to Friday can open the share screen on Thursday, tap *Change*, and pick Friday. The
poll is `OPEN` again. `ShareView` lets the organizer in whatever the state, and the
calendar is bounded `today … lastDayOnTheTable`, so the days it offers are exactly the
ones that would work.

**The corner is the day after the last candidate day.** From then on:

- The calendar offers nothing: `earliest = today` has passed `latest = lastDay`.
- `clampClosingDay` can only return a date at or before the last day on the table, so
  every closing date it produces is in the past and the poll stays `CLOSED`. Extending
  is not refused — it is undefined, because no legal date is left.
- The only action left is locking a day, and only if somebody voted for one:
  `Poll.leader()` needs a non-empty voter list, so **a closed poll nobody answered
  offers nothing at all.** It sits in seven people's lists reading "Voting closed" for
  ever.

## The escape hatch, which is the actual bug

`replaceCandidateDays` has no state guard — only an organizer one. So the way out
exists: navigate to `/poll/:id/days` by hand, put fresh days on the closed poll, then
extend the closing date to one of them. Verified: `CLOSED` → new days → `closeOn` →
`OPEN`.

Two things are wrong with it.

**It is undiscoverable.** No screen links a closed poll to the day picker. The
organizer has to know the URL.

**It silently empties every answer.** `replaceCandidateDays` prunes each ballot's chosen
days down to the days still on the table — correct in itself, and it is what keeps a
withdrawn day from being counted. But when *every* old day goes, every "yes" becomes an
empty ballot while the ballot row survives. A poll with one voter then reports
`answerCount = 1` with no votes on any day, that person is counted as having answered
rather than still awaiting, and nothing on any screen says their answer was discarded.

## Why it is like this

The closing rule is deliberately bounded by the days being voted on: a closing date
after the last candidate day would collect answers about days already gone. That bound
is right, and it is what makes "extend" undefined once they have all passed — the poll
does not need more time, it needs different days, which is a different operation.

Nothing was built for that operation because nothing distinguished "this poll is over"
from "this poll is waiting for its organizer".

## What fixing it looks like

Three separable pieces, smallest first.

- **Say the poll is finished.** A closed poll with no answers, or with answers nobody
  can act on, currently looks identical to one waiting for a lock. The results screen
  already knows both facts.
- **Refuse to rewrite the days of a poll that has answers, or say so.** Either
  `replaceCandidateDays` gains a state guard and closed polls keep their history, or
  the screen tells the organizer what replacing the days will cost before it does it.
  The silent version should not survive either way.
- **Offer the real operation: start again from this poll.** Copy the title and the
  invitee list into a fresh draft and leave the closed one alone. Cleaner than
  reopening, because answers to a question nobody could act on are not obviously worth
  keeping — and it needs no new state, no relaxed clamp, and nothing rewritten.

Whether reopening should exist *at all* is worth settling first. If copying is the
answer, the clamp stays exactly as it is and this becomes one button.

Related: [`0009-a-sent-poll-cannot-be-called-off.md`](0009-a-sent-poll-cannot-be-called-off.md)
is the same shape from the other end — a poll that should not continue, with no way to
say so.
