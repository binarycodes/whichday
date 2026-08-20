# When voting closes

## Decided

A poll carries a closing **date**, not a moment: `LocalDate closesOn`. Voting is
over from the day after it — answer on the closing date and it counts; answer the
next morning and it does not. Whole days, like every other date in the product.

The organizer chooses it. The share screen's note is the control — the design's own
copy already promises "You can extend it later" — and tapping *Change* reveals a
calendar inline, the same disclosure the counter-proposal screen uses.

## The default, and the range

Offered: **the last day on the table, that day included.**

It used to be the last working day before the *first* day, on the reasoning that
answering about a day already gone is pointless — which the design's own copy
supported ("Voting closes Friday" for a poll whose earliest candidate is the Monday
after). That reasoning was wrong about the poll as a whole. With five days offered,
the first one passing says nothing about the other four, and a team that has not
decided yet is exactly the team that still needs to. Days that have passed drop out
on their own — the ballot will not offer one — so the poll narrows as it goes instead
of dying at its first deadline.

`closeOn` clamps whatever it is given into the range a closing date can usefully sit
in: never in the past, and never past the last day on the table. The calendar is
bounded to match, so the clamp is a backstop rather than the thing the organizer
meets. Where the two bounds cannot both hold — the last option is today or tomorrow —
the last day on the table wins.

## What this replaced, and why it was wrong

The rule was `today + 5 days`, rolled forward to the next-or-same Friday, at 18:00.
It was wrong three ways, and a screenshot of the share screen showed all of them at
once:

- **The window depended on the weekday it was created.** Five days out on a Sunday,
  eleven on a Monday. Nothing about a poll changes because of when it was made.
- **It ignored the days being voted on**, so it could close *after* them: candidate
  days from the 24th, voting open until the 28th.
- **The share screen showed the current clock.** A poll not yet sent has no closing
  date, and the fallback was `now()` — which is why the note read "Thursday 3:37 PM".
  A minute value in a product with no minutes was the tell.

## Closing has to mean something

A label that said closed while answers still landed would be worse than no label.
`PollService.requireOpen` refuses a vote or a decline unless the poll is `OPEN`, so a
stale tab cannot post one after the date has passed — and a poll that was never sent
refuses answers too. The ballot and the counter-proposal screen forward away rather
than offering a control that would fail, and the results screen drops the nudge and
the your-turn prompt, because neither is anybody's move any more.

`PollState` gained `CLOSED` for the moment voting is over and no day is locked in.
That is the organizer's cue: the poll list marks it, and the results screen shows the
standings with only the lock left to do.

Seeding is the one path around the guard. `PollService.record` is package-private and
`SampleData` alone calls it, to build the two polls that were decided before the
application started — history the public path is right to refuse.

## Testing a date

`Clock.fixed` cannot reach tomorrow, and the service holds one clock for its
lifetime, so `TestClock` is advanceable. `closesTheDayAfter` walks to the closing
date, asserts the poll is still open, advances one more day, and asserts it is not.

## Not built

- **Extending a closed poll.** The note says "You can extend it later", and while a
  poll is open that is true. Once it has closed there is no way to reopen it; the
  organizer's only move is to lock a day in.
- **Anything happening at the moment it closes.** Nothing runs on a schedule, so a
  poll becomes closed because a screen asked what state it was in, not because a
  timer fired. Nobody is notified.
