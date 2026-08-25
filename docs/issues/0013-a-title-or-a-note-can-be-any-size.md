# A title or a note can be any size at all

**Severity:** low — nothing breaks, and the only thing standing between a signed-in
person and the disk is that nobody has tried.

## What happens

`poll.title` and `ballot.note` are unbounded `varchar` in the migration, the entity
says so out loud — "Unbounded, because the field that collects it sets no maximum
either" — and neither the field nor `PollService` caps what arrives. So a signed-in
person can write a megabyte into a poll title, and a poll they were invited to takes
a megabyte of note.

The store is one H2 file that the process opens and nothing prunes
([`../REQUIREMENTS.md`](../REQUIREMENTS.md) §9), so what goes in stays in. A title is
also read on every list screen and every snapshot, so an outsized one is carried into
memory each time somebody's home screen renders.

## Why it is like this

The entity comment is honest about it: the column matches the field, and the field
was never given a maximum. That is the right instinct — a limit invented in the
schema and not shown to the person typing is a save that fails for no stated reason —
but it settles on the wrong end of the pair.

## What fixing it looks like

Pick the number once and say it in both places: `setMaxLength` on the title field in
`NewPollView` and the note in `NoDayWorksView`, so the browser stops at it, and a
matching `length` on the two columns, so nothing else can go around them. A title has
to fit a `TopBar` and a calendar `SUMMARY`; a note is a sentence or two to an
organizer. Neither wants four figures.

The service is where the second half belongs, for the reason
[`0014-three-methods-trust-their-caller.md`](0014-three-methods-trust-their-caller.md)
gives: a maximum the field enforces is a courtesy, not a check.
