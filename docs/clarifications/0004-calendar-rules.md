# What the calendar offers

## Read from the design

Screen 2 draws September on a Monday-first grid. Three kinds of cell are
distinguishable: a hairline-outlined day that can be chosen, an accent-filled day
that has been, and a dimmed day with no outline at all. The dimmed ones are the
days before the design's "today" — the 1st to the 4th — and every Saturday and
Sunday.

`MonthCalendar` implements exactly that: a day is selectable when it is not in the
past and not on a weekend.

## Consequences

**No weekend events.** A leaving lunch on a Saturday cannot be put on the table.
That is the design's rule rather than an oversight of it — the weekend columns are
drawn dimmed in the same grey as the expired days — but it is a real product
limitation and the first thing to revisit if anybody asks for it. Lifting it is one
predicate, `isSelectable`.

**Monday first, in every locale.** `WeekFields.of(locale)` would start the week on
Sunday in `en-US`, splitting Saturday and Sunday to opposite ends of the grid. The
design greys them as a pair, which only reads as "the weekend" when they are
adjacent — so the grid is ISO-8601 Monday-first regardless of locale. Weekday
labels and month names still come from the locale.

## Month navigation is an addition

The design shows "September 2026" and no way to leave September. A poll whose days
straddle a month boundary would be impossible to build, and with the sample data
anchored to the clock that is the common case rather than the edge one. Two arrows
sit beside the year, at the baseline of the month name so they do not compete with
it.
