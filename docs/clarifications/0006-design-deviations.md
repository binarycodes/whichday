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

**The wordmark.** The design says "When2"; this is Whichday. It is `app.name` in
`translations.properties`, so it is one key either way.

**The dimmed weekend.** Every Saturday and Sunday is drawn unselectable. Here they can
be chosen like any other day — see
[`0004-calendar-rules.md`](0004-calendar-rules.md).

## Substituted

**"Tell me when the date is locked."** The receipt screen's notify toggle is gone
rather than substituted. It was a pre-ticked checkbox with no listener, read by
nothing, so every voter was told a promise they had not made and that nothing could
keep — there is no mail path at all
([`../issues/0003-nothing-is-ever-actually-sent.md`](../issues/0003-nothing-is-ever-actually-sent.md)).

That is the same reasoning as "Clashes with your calendar" above: a control that
cannot do what it says is worse than no control. It comes back with the thing that
would send the notification, and then the open question is Vaadin's — there is no
switch component, and turning a checkbox into one means styling into its shadow root,
the kind of `themeFor` reach `CODING_CONVENTIONS.md` §11 makes a build step out of.

**Message and Add-to-calendar do real work.** The design shows both as buttons and
says nothing about what happens. There is no mail server here, so "Message" is a
`mailto:` the browser hands to whatever the reader actually uses, and
"Add to calendar" is a generated iCalendar file — all-day events, since the whole
product is whole days. Both are anchors wearing the button's clothes. "Copy" uses
the clipboard API and says so when it works.

## Added

Each of these exists because the design's flow is unreachable or unusable without
it. They are argued where they live: `0004-calendar-rules.md` for month navigation,
`0005-team-membership.md` for the invitee screen.

**Counter-proposals reach the organizer.** Screen 2a promises "Ada sees it next to
the counts", and the design never draws that screen. The results view grows a
"Proposed instead" section listing each proposal with an "Add it" button, which is
the only thing that makes the promise true — and the only thing that makes
`PollService.acceptProposal` reachable.

**Invitee chips truncate the address; they do not abbreviate it.** The design's
chips read `miro@…`, cutting the domain. Here the chip holds the whole address and
lets CSS truncate it, with the full one on the chip's tooltip and in the Added list
underneath — one rule instead of a rule plus an abbreviation scheme to get wrong.

**The receipt's standings show every day on the table.** The design's receipt draws
three bars, and the voter it draws had said yes to three of five days — so the three
bars are as easily "the days you chose" as "the leading three". Neither truncation
survives contact with a voter who says yes to all five: three bars leave two of
their own days undrawn, with no way to tell whether those are losing or simply not
shown, under a heading that promises where the *team* stands. Every candidate day
gets a bar. Which ones are yours is already answered by the posters above it, at
poster scale, so the bars carry no extra marking.

**The organizer decides whether other days may be put forward.** Not in the design,
which shows the counter-proposal screen as always available. It is a real question
for a poll whose days are already fixed by something outside it — a booked room, a
visitor's only free week — where inviting alternatives just collects answers nobody
can act on.

The switch sits on the candidate-days screen, because it is a rule about those days
and the organizer is looking at them. What it turns off is putting *other* days
forward. Saying "I can't make any of these" stays available either way: it is an
answer the organizer needs — it moves the counts and empties the waiting list — and
the poll would otherwise have no way for somebody to answer it truthfully. With the
switch off, that screen keeps its confirmation and its note field and loses only the
calendar, and the footer stops caveating a proposal that cannot be made.

**No dialogs anywhere.** Two screens needed a picker the design does not draw —
choosing who decides, and proposing a day instead — and an overlay was the wrong
answer to both. On a phone a dialog gets the same few hundred pixels as the screen
under it, and it covers the very thing it is filling in.

Choosing who decides became its own screen, because the search needs a keyboard, a
result list and a growing set of chips at once (`0005-team-membership.md`).
Proposing a day became an inline calendar folded away behind the design's own dashed
"+", because a month is too tall to sit above the note field permanently but far too
small a decision to spend a screen on. It is the same `MonthCalendar` the organizer
puts days on the table with, so a voter proposing an alternative and an organizer
offering one are the same gesture — and it refuses the days already on the table,
which are not alternatives to themselves.

Nothing in `src/main` constructs a `Dialog` now.

**The organizer can answer their own poll.** The design gives the organizer the
results screen and gives voting to everybody else, which leaves the one person who
called the meeting unable to say which days work for them — while still being
invited and still counted in every denominator. The symptom was the results screen
offering the organizer a nudge to themselves.

Both halves are fixed: `Poll.awaitingOthers` excludes the person looking, so a
nudge only ever names somebody else, and the results screen carries a card for the
viewer's own answer — "You haven't picked your days yet" with a way to the ballot,
or "You said yes to N days" with a way to change them. Submitting returns the
organizer to the standings they came from rather than to a voter's receipt.

The organizer's vote is explicit, not implicit. Counting every candidate day as a
yes on their behalf would be the other way to close the gap, and it would be wrong:
an organizer puts days on the table to find out what the team can do, and often
cannot do all of them themselves. Their availability is a real answer, so it is one
they give.

**A way home on every screen.** The design draws a back chevron on three screens
and nothing at all on four others — a voter who lands on a receipt, or an organizer
watching the counts, has no way out of it. Every screen now carries one home
affordance marked `nav-home`: the wordmark where the design already puts one, a
home glyph in the top bar otherwise, and the footer button on the not-found screen.
The two wizard screens keep their back chevron as well, because stepping back to
edit the days and leaving the poll altogether are different intentions and should
not be the same tap. `PollJourneyTest.everyScreenCanLeave` asserts the whole set.

**The results screen has one empty state, not a separate screen.** Screens 5 and 2c
are the same route: `ResultsView` draws dashed rows and a "Waiting on" list while
nobody has answered, and the standings once somebody has. Same layout either way,
so the poll does not appear to change shape when the first answer lands.

## Sample data

The design's numerals — Mon 14, Fri 18, September 2026 — are anchored to the clock
instead. See `0010-an-embedded-database-on-disk.md`.
