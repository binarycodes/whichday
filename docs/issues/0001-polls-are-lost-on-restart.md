# Every poll is lost when the application restarts

**Severity:** high — silent data loss in normal operation.

## What happens

`PollService` keeps every poll in a map in memory. A restart empties it: the polls,
the candidate days, every ballot, the settled dates. Nothing warns anybody before or
after, and there is nothing to back up.

In practice that means a deploy, a crash, an OOM kill, a host reboot, or
`podman auto-update` pulling a new image all wipe the application. During development
`spring-boot-devtools` restarts on every recompile, so it happens constantly.

## Why it is like this

The first implementation was deliberately a view layer over an in-memory store, so
that the screens could be built before a schema existed. That was a reasonable place
to start and is no longer a reasonable place to stay.

## What fixing it looks like

The boundary is already in the right place, which is the one piece of good news:
reads return immutable records, `StoredPoll` and `StoredBallot` are package-private
and never escape the service, and no view has ever seen a mutable poll. So a
persistence tier goes behind `PollService` without a single view changing.

`CODING_CONVENTIONS.md` §10 describes the shape the rest of it should take —
PostgreSQL through Spring Data JPA, Flyway owning the schema,
`ddl-auto=validate` so drift fails startup rather than being rewritten, and entities
that never leave the service package.

Do this together with [`0002-any-signed-in-user-can-read-any-poll.md`](0002-any-signed-in-user-can-read-any-poll.md):
both are about the storage layer, and §10's rule that every table carries an owner
and every query is scoped by it answers both at once.
