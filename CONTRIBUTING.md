# Contributing

## Before you write anything

[`CODING_CONVENTIONS.md`](CODING_CONVENTIONS.md) holds the project-wide rules — naming,
comments, structure, Java and Vaadin idiom, CSS, persistence, build and CI. Follow all
of them; if one is wrong, amend it there rather than making an exception here.

Reads return immutable records and the entities never leave the service package, which
is why no screen changed when the store stopped being a map in memory.

Commits are Conventional Commits, enforced by a `commit-msg` hook that every clone has
to opt into:

```bash
git config core.hooksPath .githooks
```

## Run it locally

Login is required in development as well, so it needs an OIDC client before it will
start:

```bash
export WHICHDAY_OIDC_ISSUER_URI=https://accounts.example.com
export WHICHDAY_OIDC_CLIENT_ID=...
export WHICHDAY_OIDC_CLIENT_SECRET=...
./run.sh run
```

The database appears at `./data/whichday.mv.db` on first start and survives every
restart, devtools included. `./run.sh resetdb` deletes it and the app creates an empty
one next time. The tests never touch it — they run against an in-memory database.

Open <http://localhost:8080> and it redirects you to the provider. Register
`http://localhost:8080/login/oauth2/code/oidc` with the client.

## Tasks

`./run.sh` with no arguments lists them all. The ones you want:

| Task      | What it does                                                     |
| --------- | ---------------------------------------------------------------- |
| `run`     | Start the app in dev mode                                        |
| `test`    | Unit and browserless tests, with the JaCoCo gate                 |
| `verify`  | The same against a production build                              |
| `styles`  | Copy edited stylesheets to `target/classes` and bust the cache   |
| `deps`    | Pre-download dependencies and build plugins, so a later task needs no network |
| `resetdb` | Delete the local database                                        |

`run` needs the OIDC variables above; the tests do not. `./run.sh` pins JDK 21, and
every build carries the commit SHA — the enforcer plugin rejects one that does not. CI
runs `mvn verify` on every push and publishes the image from `main`.

`run.sh` is shared between projects and names none of them: what it needs to know
about Whichday is in `run.conf`, and `resetdb` — the one task it does not ship — is in
`run.tasks.sh`. Improve the runner upstream and copy it back rather than editing it
here.

## Decisions and known problems

[`docs/REQUIREMENTS.md`](docs/REQUIREMENTS.md) is the design record: every requirement
and decision with the reason behind it. Read it before changing behaviour, and add to
it when you take a new decision. [`docs/issues/`](docs/issues/) records what is wrong
and what fixing it would take.
