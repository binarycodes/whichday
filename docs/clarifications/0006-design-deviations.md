# Where the implementation departs from the design

Everything here is deliberate. Anything not listed is meant to match.

## Left out

**The phone status bar.** Every frame draws `9:41` and a battery. That is the
device the mockup sits in, not the application — a web page drawing a fake system
clock is a lie about what it is. The frames' 390×844 outline goes with it: the
column is capped at 30rem and centred, so it fills a phone and does not stretch
across a desktop (`CODING_CONVENTIONS.md` §8).

**"Clashes with your calendar."** Screen 4's fifth row is dimmed and unavailable
because the voter's own calendar says so. There is no calendar integration to ask,
and inventing a clash would be worse than not having one. The dimmed-and-disabled
row treatment survives and is used for a candidate day that has already passed,
which is a real reason a day cannot be voted for.

**The wordmark.** The design says "When2"; this is Find a Date. It is
`app.name` in `translations.properties`, so it is one key either way.

## Substituted

**A checkbox, not a switch.** The receipt screen's "Tell me when the date is
locked" is an iOS-style toggle in the design. Vaadin has no switch component, and
turning a checkbox into one means styling into its shadow root — the kind of
`themeFor` reach `CODING_CONVENTIONS.md` §11 makes a build step out of. It is a
checkbox until there is a switch, and it means the same thing.

**Message and Add-to-calendar do real work.** The design shows both as buttons and
says nothing about what happens. There is no mail server here, so "Message" is a
`mailto:` the browser hands to whatever the reader actually uses, and
"Add to calendar" is a generated iCalendar file — all-day events, since the whole
product is whole days. Both are anchors wearing the button's clothes. "Copy" uses
the clipboard API and says so when it works.

## Added

Each of these exists because the design's flow is unreachable or unusable without
it. They are argued where they live: `0003-voter-identity.md` for the account
switcher, `0004-calendar-rules.md` for month navigation.

**Counter-proposals reach the organizer.** Screen 2a promises "Ada sees it next to
the counts", and the design never draws that screen. The results view grows a
"Proposed instead" section listing each proposal with an "Add it" button, which is
the only thing that makes the promise true — and the only thing that makes
`PollService.acceptProposal` reachable.

**The results screen has one empty state, not a separate screen.** Screens 5 and 2c
are the same route: `ResultsView` draws dashed rows and a "Waiting on" list while
nobody has answered, and the standings once somebody has. Same layout either way,
so the poll does not appear to change shape when the first answer lands.

## Sample data

The design's numerals — Mon 14, Fri 18, September 2026 — are anchored to the clock
instead. See `0002-in-memory-store.md`.
