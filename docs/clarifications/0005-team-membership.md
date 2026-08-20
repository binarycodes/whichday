# Who a poll goes to

## Decided

The organizer picks. The create screen's second field, "Who decides with you",
shows the chosen team the way the design draws it — three overlapping faces and a
summary — and tapping it opens the list to check people off. `TeamField` is a
`CustomField<Set<Person>>`, so it goes through the form's `Binder` like any other
field (`CODING_CONVENTIONS.md` §5).

Everybody is selected to start with, which is the common case and matches what the
design shows. The summary says "Design team · 7 people" while that holds and
"Design team · 3 of 7" once it does not.

**The organizer cannot be taken out.** Their checkbox is checked and disabled, and
`PollPresenter.create` puts them back if a caller leaves them out anyway. They are
deciding, so their availability has to be counted somewhere — and the design agrees:
screen 3 lists Ada in her own invite list.

At least one other person is required. That is a `withValidator` on the field
rather than a check in the submit handler, so it reports itself where the mistake
was made.

## What this replaced, and why that was wrong

The field started out read-only, on the reasoning that the design draws no
affordance for changing it and that a picker would mean deciding where teams come
from.

Both halves were wrong. The design draws no affordance because it draws one state
of a field, which is what a mockup does — and the plumbing needed no decision at
all: `PollService.create` already took an invitee list, and `TeamDirectory` already
had the seven people to choose among. Only the view was hard-coding "everyone". The
work was a component, not an account system.

## Consequences

- `inviteCount()` is no longer always seven, so it is read rather than assumed
  everywhere it matters: the share screen's "Send N invites", the results screen's
  "N of M", and the denominator every tally bar is filled against.
- Sample data still invites the whole team, so the design's "6 of 7" arithmetic is
  unchanged.
