# The share link is built from a header the browser sets

**Severity:** low — the only person who can be shown a poisoned link is the person
who poisoned it.

## What happens

`VotingLink.origin` builds the poll's one link out of
`VaadinServletRequest.getRequestURL()`, which is assembled from the request's `Host`
header. Nothing checks that the host is one this deployment answers to — Tomcat does
not, and no configuration here asks it to.

The link goes three places, all of them the organizer's own: the share screen, the
clipboard, and the payload handed to the system share sheet — which the results screen
offers a second time, for an organizer back there because somebody never got it. So a request carrying
somebody else's host produces an invite pointing at somebody else's server, in whatever
the organizer is about to send to their team.

## What it costs

Very little, because there is no path by which an attacker makes the organizer's
browser send the poisoned header — this is not a password reset mailed by the server,
and nothing generated here is cached and served to anybody else. It is on the list
because taking the base URL from the request is the decision that makes the class of
bug possible, and because the same code is what a later feature that *does* send mail
would reach for.

## Why it is like this

Deliberately, and for a good reason: reading the origin off the request is what makes
the link right in development and right behind a proxy without either being told
about the other, and a configured base URL is one more thing every deployment has to
get right. `FORWARD_HEADERS_STRATEGY` already exists for the proxy half.

## What fixing it looks like

Not by configuring the base URL — that gives up what the current shape is for. An
allowed-hosts check is the smaller change: Tomcat can be told which host names it
answers to, so a request naming another one is refused before it reaches a route, and
the origin stays the request's. It wants the same environment-variable shape as
`FORWARD_HEADERS_STRATEGY`: unset and unenforced for a local run, set in a deployment
that knows its own name.
