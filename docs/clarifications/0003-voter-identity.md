# Who the browser is

## Resolved

Signing in is the only way in, and OIDC is the only way to sign in. See
`0009-signing-in.md` for how, and for what it cost.

This file used to describe the opposite: no login, a session-scoped `ViewerSession`
holding a `Person` from a hard-coded directory, and an account switcher on the
header avatar so that both the organizer's and a voter's half of the design were
reachable. That switcher was always meant to be deleted the moment there was a real
subject to read, and it has been — the avatar now offers signing out instead.

## The conflict it deferred, and how it landed

The design promises, on its first screen, "No sign-up for voters. One link, one
tap." `CODING_CONVENTIONS.md` §10b says login is mandatory on every route. Both
could not hold, and this tier having no login at all deferred rather than resolved
it.

It is resolved now, in favour of §10b: **voters sign in**. `/vote/:slug` requires a
session like every other route. That is the one design promise the application breaks
outright rather than approximating, and it follows directly from "strict login only".

What it would take to honour the design instead: a per-invitee token in the voting
link, a route that accepts that token without a session, and a way to attribute an
answer to somebody who has no account. None of it is small, and all of it wants
deciding on purpose rather than as a side effect.

## What survived

An invitee is still a `Person` whether or not an account answers to their address.
Somebody invited by email who has never signed in gets a blank name, their address as
`displayName()`, and a colour derived from that address — and nothing downstream, not
the tallies, not the ballots, not the denominators, knows the difference. That is what
would let a token-based voting link work later without touching the counting.
