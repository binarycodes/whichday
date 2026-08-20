# Who a poll goes to

## Decided

Every poll goes to everybody in `TeamDirectory` — the seven people the design names.
The create screen's second field, "Who decides with you", shows that team and does
not edit it: three faces, the team name, and the count, in a box drawn like a field
but holding no control.

## Why not a picker

The design does not draw one either. Its field shows exactly the same thing — three
overlapping avatars and "Design team · 7 people" — with no affordance for changing
it, which reads as "this is the team you are in" rather than "choose some people".

Building a picker here would mean deciding where teams come from, and that answer
lives in the account system this tier does not have. A `MultiSelectComboBox` over a
hard-coded list of seven would look like a feature and be a decoration.

## Consequences

- `PollPresenter.create` passes `session.everyone()` as the invitee list, so
  `inviteCount()` is always seven and the design's "6 of 7" arithmetic holds.
- The organizer is in the invited list and votes like anybody else. The design puts
  Ada in her own invite list on screen 3, and it is the right model: the person
  calling the meeting has availability too.
