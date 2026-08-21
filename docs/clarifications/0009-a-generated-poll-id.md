# What identifies a poll

## Decided

`UUID.randomUUID()`, generated when the poll is created, and typed as a `UUID` from
the record through the service to the route parameter. The voting link is
`/vote/3f2a1c8e-5b9d-4e7a-8c6f-1d2e3a4b5c6d`.

## What this replaced

The id used to be made from the title: "Q3 team offsite" became `q3-team-offsite`,
and a collision appended a counter held in an `AtomicInteger` on the service. It read
beautifully in the link, and it was wrong in three ways.

**Two polls may legitimately share a name.** Teams ask about "Team event" every few
months. The second one got `team-event-1`, which is not a name anybody would choose
and is one somebody else's poll could already be using.

**The counter could not survive a restart.** It was per-instance and shared across
every title, so it restarted at zero while the polls did not. With `q4-review` and
`q4-review-1` already stored, the next "Q4 review" produced `q4-review-1` again — and
the store was a map, so `put` silently overwrote the existing poll. That was
harmless only because nothing survived a restart anyway; it would have become durable
data loss the moment the polls did.

**A readable id is a guessable id.** At the time, signing in was required but nothing
checked *which* signed-in person was asking, so `q3-team-offsite` was a URL a stranger
could arrive at by typing what a team is obviously called. That hole is closed now —
[`0012-only-invited-people-see-a-poll.md`](0012-only-invited-people-see-a-poll.md) —
and the unguessable id is still worth having: it is what keeps the *existence* of a
poll from being enumerable by anybody, invited or not.

## Consequences

- **The share card truncates the link on a phone.** `.link-url` is `nowrap` with an
  ellipsis, so a 36-character id shows as `whichday.example.com/vote/3f2a1c8e-5b9…`.
  The Copy button beside it is the affordance that matters, and the link is whole in
  the email invite and on a wider screen. A shorter random id would have read better
  and would have meant writing and defending our own generator; a UUID is the boring
  answer and needs no collision argument.
- **A malformed id and an id nobody issued are the same answer.** `PollScreen` is the
  only place a route parameter is read, so it is the only place that parses one, and
  anything that is not a `UUID` we issued forwards to the not-found screen.
- The calendar file's `UID:` and its download name now carry the id rather than a
  readable name. Nothing reads either.
