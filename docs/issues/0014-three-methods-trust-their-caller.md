# Three service methods trust the screen that calls them

**Severity:** low — every one of them is safe today, and each is safe because of a
caller rather than because of itself.

## What happens

`PollService` decides who may do what, deliberately and in one place: "Enforced in
`PollService`, never on the screens. The screens do hide what is not yours, and that
is a courtesy; a hidden button is not a check"
([`../REQUIREMENTS.md`](../REQUIREMENTS.md) §2). Three methods do not hold to it.

- **`plannedClosing` and `latestClosingDay`** take no viewer at all. They go through
  `require`, which is `findById`, rather than `findVisibleById` — so an id is enough
  to learn a poll's closing date and its last candidate day. §2 names this as a
  deliberate exception and gives the reasons: they are reads, they say only what the
  ballot already shows, and `ShareView` turns a non-organizer away before it calls
  them.

- **`decline`** takes the proposed days and writes them without asking whether the
  poll accepts alternatives, or how many. Both rules live in `NoDayWorksView`: it
  draws no picker when `alternativesAllowed` is false, sends `List.of()` anyway when
  it is false at send time, and caps the calendar at three. The comment there says as
  much — "reading the flag here keeps that true of the write as well as the view" —
  which is the view keeping a promise the write does not make.

## What it costs

Nothing, today. The exception is documented, the caller does check, and the second
caller does not exist. What it costs is the property that made §2 worth writing: that
the answer to "can this person do this" is in one file, and reading the screens is
never necessary to be sure. Three exceptions are three places a later screen, or a
later caller, can be wrong without being obviously wrong.

## What fixing it looks like

Give the two reads a viewer and route them through `findVisibleById`, the same way
`poll` does — the exception buys nothing that the extra parameter costs. `ShareView`
already holds the poll it is asking about, so the call sites do not change shape.

Give `decline` the check its caller makes: refuse proposed days outright when the
poll does not allow them, and cap the list at the limit the calendar enforces. That
puts the limit somewhere `PollServiceTest` can pin it, which is where the rest of §2
already is.
