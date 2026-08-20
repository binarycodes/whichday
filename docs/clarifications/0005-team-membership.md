# Who a poll goes to

## Decided

There is no team and no directory. The only way anybody gets onto a poll is the
organizer typing their email address — the model section C of the design sets out,
and a replacement for the browsable member list this file used to describe.

`AccountDirectory` has no method that hands a screen everybody. `matching` is the
only way in, and it answers nothing at all below three characters, so nobody is
listed until the organizer has typed enough to have known who they were looking
for. `forInvite` turns an address with no account behind it into somebody who can
still be invited.

**An account is somebody who has signed in.** Nothing seeds the directory: it
remembers whoever authenticates, on the way past, and that is the only way it learns
about anybody. So a colleague becomes findable once they have signed in at least
once, which is also the honest reading of the design's "only accounts that share a
workspace or a past poll with you".

An invitee is a `Person` either way. An outsider gets one with a blank name, their
address as `displayName()`, and an avatar tone derived from the address so they are
the same colour on every screen and after every restart. Nothing downstream —
tallies, ballots, denominators — knows the difference.

## Two screens, not an overlay

Naming a poll and choosing who decides it are separate screens. The search needs a
keyboard, a result list and a growing set of chips at the same time, and on a phone
a dialog gives all three the same few hundred pixels.

The create screen keeps the design's field: chips that wrap, each removable, and a
prompt that opens `/new/invitees`. The prompt is text rather than an input, so the
keyboard only ever opens on the screen that has somewhere to put results.

Both write to `PollDraft`, held by the session-scoped presenter. Stepping out to
search and coming back loses nothing, and abandoning the flow writes no half-built
poll into the store.

## The rules, and where they came from

The design's own implementation notes, followed as written:

- **Three characters minimum**, debounced 250ms (`ValueChangeMode.LAZY` with a
  matching timeout).
- **Match a whole address as it is typed, or the start of any part of its local
  part** — so `sar` finds both `sara.naslund@acme.com` and `t.sarkar@acme.com`.
- **At most five rows**, because a long answer is a directory too.
- **The organizer never appears as a match** and is added implicitly.
- **Anything else resolves to an email invite** rather than to an error.

## Searching for yourself

The organizer is never a match, which is the design's rule and the right one — but
a query only they answer to then comes back empty and reads as "nobody by that
name", as if their own account did not exist. Typing `tom` as Tom Beck was exactly
that.

`AccountDirectory.matchesSearcher` answers whether the query was reaching for the
searcher's own account, and the field says "That's you — you decide either way"
instead. It is not a leak: the only address it confirms is the one the searcher
already typed and already owns.

## Where this tightened the spec

The design says matches come from "email prefix or any dot/at-delimited part". Read
literally that includes the domain, and a search for `acme` would hand five
colleagues to anybody who guessed a company name — the listing the three-character
rule exists to prevent. `AccountDirectory` therefore matches the local part only,
and `doesNotMatchTheDomain` pins it.

The other half of the design's rule — "only accounts that share a workspace or a
past poll with you" — has no workspace to filter against: there is no notion of one.
What the directory does support is the distinction the rows show, so `InviteeSearch`
counts the polls two people have actually both answered and the matches with history
sort first. Everybody who has signed in is findable; a workspace boundary would be a
persistence-tier concern.

## Not built

- **Adding people after a poll is live.** `PollService.addInvitee` exists and the
  design's C2 hint promises it ("You can keep adding people after the poll is
  live"), but no screen calls it yet. The hint is not shown, rather than shown and
  broken.
- **A bounced invite.** C3 names it as a state; there is no mail transport to bounce.
- **Warning chips for a malformed pasted address.** C3 draws the bad entry as an
  orange chip. Here it stays in the field with the rest of the paste accepted, on
  the grounds that an address with a typo in it is worth fixing where it can still
  be edited — a chip you cannot correct is a worse dead end than a field you can.
