# Who the browser is

## The conflict

The design promises, on its first screen, "No sign-up for voters. One link, one
tap." `CODING_CONVENTIONS.md` §10b says login is mandatory on every route and
that a route without an access annotation is denied. Both cannot hold.

They are also not really in conflict about the same thing: §10b is about the
organizer's library, and the design's promise is about the person answering a
poll. A deployment can have both — public `/vote/:slug` routes and authenticated
organizer routes — but that is a decision about the product, not about this tier.

## Decided

This tier has no login at all, so nothing is resolved and nothing is violated.
`ViewerSession` is session-scoped and holds a `Person` from `TeamDirectory`,
defaulting to Ada Lindqvist — the organizer in the design's sample data. It is the
seam where an authenticated subject goes: every screen asks it who is looking, and
nothing else does.

## The switcher is an addition

The avatar in the top-right of the design is the account. Here it also opens a menu
that changes who you are.

That is not in the design, and it exists for a specific reason: half these screens
belong to the organizer and half to a voter, and without a login there is no other
way to reach both. Tapping the avatar and becoming Sara Näslund is what makes
screens 4, 2a and 2b reachable at all.

It is one component, `AccountSwitcher`, and it sits hard right wherever it appears —
the header of the two screens that open the application, and the ballot's invitation
row. The ballot briefly had a decorative copy of the viewer's avatar inside that
sentence instead, which looked like the account control without being one.

It should be deleted the moment there is a real subject to read. Everything it
touches goes through `PollPresenter.switchViewer`, so deleting it is one component
and one method.

## What is not built

- **Voting by link alone.** `/vote/:slug` is reachable without signing in, but the
  ballot is attributed to whoever `ViewerSession` currently says — not to an
  anonymous holder of the link. A real implementation needs a per-invitee token in
  the URL, which is a persistence-tier concern.
- **Nudging.** The button reports that a nudge was sent and sends nothing; there is
  no mail or push transport behind it. Sharing does better — see
  `0006-design-deviations.md`.
