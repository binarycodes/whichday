# What the calendar offers

## Read from the design

Screen 2 draws September on a Monday-first grid. Three kinds of cell are
distinguishable: a hairline-outlined day that can be chosen, an accent-filled day
that has been, and a dimmed day with no outline at all. The dimmed ones are the
days before the design's "today" — the 1st to the 4th — and every Saturday and
Sunday.

`MonthCalendar` draws exactly that grid, with one rule changed.

## Weekends are offered, which the design does not do

The design dims every Saturday and Sunday in the same grey as the expired days, and
`isSelectable` used to agree. It no longer does: a day is selectable when it has not
gone yet, is inside the range the caller allows, and is not already ruled out.

The dimmed weekend was the design's rule rather than an oversight of it, and it was
still wrong to keep. A leaving lunch on a Saturday, a weekend offsite, a Sunday
kickoff — a tool for asking a group which days work has no business deciding that two
of the seven are not days. The greying said "we know better than you", about the two
days most likely to be the answer for anything social.

What is left of the rule is the reading order: the grid is ISO-8601 Monday-first
regardless of locale, where `WeekFields.of(locale)` would start on Sunday in `en-US`
and split the weekend to opposite ends. A week reads as five days and then the
weekend, whether or not the weekend can be chosen. Weekday labels and month names
still come from the locale.

`theCalendarOffersWeekends` pins it, and pins that a day already gone is still refused
— the two halves used to be one predicate and it would be easy to lose the second
while changing the first.

## Whole weeks, always

The grid renders complete weeks and only as many as the month needs — four, five or
six rows. Counting to a fixed six rows and stopping when the days run out was wrong
twice over: November 2026 starts on a Sunday, so its 36th cell landed alone on a
sixth row, and February 2027 is exactly four weeks, so a fixed grid trailed a whole
spare week of March. `theCalendarGridIsAlwaysWholeWeeks` checks both shapes and the
months either side of them.

## At most three

`setMaximumSelection` caps a selection. At the cap the unchosen days stop offering
themselves and the chosen ones stay live, so the ceiling shows in the grid instead
of arriving as a rejection, and swapping one day for another is two taps and no
error. Enabling and disabling happens in place, for the same reason toggling does:
the grid is still holding the button the reader just pressed.

The counter-proposal screen sets it to three — what the poster row holds across a
phone, and the point past which a counter-proposal stops being one. Reaching it
folds the calendar away, because the decision is finished; below it, a "Done" line
closes the calendar for whoever is finished early.

## Unavailable days

`setUnavailable` takes days the calendar must not offer whatever the rules above
say. One caller: the counter-proposal screen passes the poll's own candidate days,
because a day already on the table is not an alternative to it.

## Month navigation is an addition

The design shows "September 2026" and no way to leave September. A poll whose days
straddle a month boundary would be impossible to build, and with the sample data
anchored to the clock that is the common case rather than the edge one. Two arrows
sit beside the year, at the baseline of the month name so they do not compete with
it.
