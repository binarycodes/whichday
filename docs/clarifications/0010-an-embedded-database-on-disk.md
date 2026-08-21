# Where the polls live

## Decided

An H2 database opened as a file: one file under `./data` locally, `/app/data` in the
container. Flyway owns the schema (`db/migration/V*.sql`) and
`spring.jpa.hibernate.ddl-auto=validate` fails startup if the entities and the
migrations disagree, rather than quietly rewriting the schema to match.

Nothing seeds it. The first person to sign in gets an empty list, and the data the
design was drawn with lives in the test tree as `Sample`.

Reads still build immutable records on the spot. `StoredPoll`, `StoredBallot` and
`StoredAccount` are entities now, and they are still package-private and still never
returned, so a screen holding a `Poll` holds a snapshot rather than a window into the
store. That is why no view changed when this landed.

## Not PostgreSQL, which §10 asks for

`CODING_CONVENTIONS.md` §10 says PostgreSQL through Spring Data JPA. This deviates on
the engine and on nothing else.

The whole product is one container somebody self-hosts from Docker Hub. A second
container, plus a network, plus credentials, plus a backup story, is a much larger
change to the *deployment* than persistence is to the code — and it would be asking
every reader of the README to run a database server so that seven people can pick a
Thursday.

`MODE=PostgreSQL` and Flyway-owned migrations are what keep that from being a one-way
door: the migration in `V1` is portable SQL that a real PostgreSQL runs unchanged, so
moving is a URL and a dependency rather than a rewrite. The two places the engine
shows through are named where they happen — `offered_day` avoids H2's reserved `day`,
and the timestamp columns declare precision 6 because H2 keeps nanoseconds where
PostgreSQL truncates to microseconds, and a test that passed only on H2's extra
precision would be encoding the deviation instead of hiding from it.

## Consequences

- **One process, one file.** An embedded H2 file can only be opened by the process
  holding it, so two containers on one volume means the second will not start. That is
  a deployment rule with nothing enforcing it, which is why the README says it out
  loud. It is also what makes the coarse locking honest: there is exactly one writer.
- **The file is the backup unit.** Copy `whichday.mv.db` while the app is stopped.
- `synchronized` came off every service method. A monitor inside a transactional proxy
  is acquired after the transaction opens and released before it commits, so it reads
  like a guarantee and is not one. Writers take a `PESSIMISTIC_WRITE` row lock on the
  one poll they are changing, held until commit; readers take none.
- **`replaceCandidateDays` is transactional now**, which is what it always wanted to
  be: it clears the candidate days and prunes every ballot's chosen days, and that was
  the one place a partial update would have been observable.
- The poll state is still derived on every read rather than stored, so nothing fires
  when a poll closes — [`../issues/0005-closing-happens-on-read.md`](../issues/0005-closing-happens-on-read.md).
- Still no `owner_id` scoping (§10): anybody who has the link can read the poll —
  [`../issues/0002-any-signed-in-user-can-read-any-poll.md`](../issues/0002-any-signed-in-user-can-read-any-poll.md).
  The migration indexes the two columns that fix will need, so it is a `where` clause
  and not a migration.
- Flyway warns at startup that H2 2.4.240 is newer than the version it was verified
  against. Both come from Boot's managed versions, so the pairing is Boot's rather
  than ours.

## The sample data lives in the tests

It used to be production seeding. Once signing in became the only way in, an account
existed because somebody authenticated — so a hard-coded Ada Lindqvist had nowhere to
live in the application, and the polls she owned had nobody to own them.

`Sample` in the test tree still builds that shape, and still anchors to the clock
rather than writing September 2026 out: the design's numerals would have put the whole
fixture in the past within the year. Six ballots over five days give 6 / 4 / 3 / 2 / 1,
with Jonas Wirtanen holding out so "Everyone but Jonas" and the nudge prompt have
something real to say.
