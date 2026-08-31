# The invitee search reads every account, on every keystroke

**Severity:** low — invisible at today's size, and it is the one screen a signed-in
person can point at the database.

## What happens

`AccountDirectory.matching` calls `findAllByOrderByEmailAsc`, which is every row in
the `account` table, and filters the result in Java. `InviteeSearch.matching` then calls
`PollService.pollsSharedBy` once per hit, and each of those runs
`countPollsAnsweredByBoth` — a correlated subquery over the ballots — so up to five of
them per search.

The search field is `ValueChangeMode.LAZY` at 250 ms, so that is one full table load
plus five counts every quarter second for as long as somebody holds a key down. The
work is per-keystroke and the table it reads grows with every person who has ever
signed in.

## Why it is like this

The filtering is in Java on purpose, and it should stay there: matching the local
part's pieces is not a prefix query, and the rule that the domain must never match is
written down there — `like '%acme%'` would hand five colleagues to anybody who
guessed a company name, which is the listing that method exists to refuse.

What does not have to stay is the *load*. The rule is written in Java; the reading of
every row is only what makes the rule reachable.

## What fixing it looks like

Narrow the query to what a prefix can express — an address starting with the needle —
and keep the part-splitting rule in Java over the narrowed set. The two arms are not
the same set, so a query that only the parts rule would match must still find its
rows; a second indexed predicate, or an index over the parts, is the decision to
make. `AccountDirectoryTest` already pins the behaviour the rewrite has to preserve,
including the domain refusal.

The five counts are the smaller half and can go the same way: one query keyed by the
whole match list rather than one per person.
