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

Login is required in development as well, so an identity provider has to be up before
the app will start. There is one in `environment/dev` — a Keycloak with a realm, a
client and two people already in it — and `application.properties` defaults to exactly
that, so nothing needs configuring:

```bash
./run.sh run
```

That is anonymous mode, which is the default and needs nothing brought up — no
Keycloak, no configuration. Open <http://localhost:8080>, type a name, and you are in.

Login mode is the other half of the application and wants the development Keycloak:

```bash
./run.sh env up
```

```bash
WHICHDAY_ACCESS_MODE=login ./run.sh run
```

It now redirects you to Keycloak. Sign in as **`ada` / `ada`**; `miro` / `miro` is the
second person, which is who you invite. See
[`environment/dev/README.md`](environment/dev/README.md) for the realm, the admin
console and how to add a third.

To point at a provider of your own instead, set all three — the defaults are a
laptop's and none of them belongs anywhere else:

```bash
export WHICHDAY_ACCESS_MODE=login
export WHICHDAY_OIDC_ISSUER_URI=https://accounts.example.com
export WHICHDAY_OIDC_CLIENT_ID=...
export WHICHDAY_OIDC_CLIENT_SECRET=...
```

Register `http://localhost:8080/login/oauth2/code/oidc` with that client.

Both modes share one database, so a poll made in one is visible in the other's tables —
which is a development convenience and nothing more. A deployment picks a mode once.

The database appears at `./data/whichday.mv.db` on first start and survives every
restart, devtools included. `./run.sh resetdb` deletes it and the app creates an empty
one next time. The tests never touch it — they run against an in-memory database.

## Tasks

`./run.sh` with no arguments lists them all. The ones you want:

| Task      | What it does                                                     |
| --------- | ---------------------------------------------------------------- |
| `env`     | The development Keycloak, for login mode only: `up` (default), `down`, `logs`, `reset` |
| `run`     | Start the app in dev mode                                        |
| `test`    | Unit and browserless tests, with the JaCoCo gate                 |
| `verify`  | The same against a production build                              |
| `styles`  | Copy edited stylesheets to `target/classes` and bust the cache   |
| `deps`    | Pre-download dependencies and build plugins, so a later task needs no network |
| `resetdb` | Delete the local database                                        |
| `readmegif` | Re-record the README's GIF (takes a deployment's URL)          |

`run` needs `env up` first; the tests need neither. `./run.sh` pins JDK 21, and
every build carries the commit SHA — the enforcer plugin rejects one that does not. CI
runs `mvn verify` on every push and publishes the image from `main`.

`run.sh` is shared between projects and names none of them: what it needs to know
about Whichday is in `run.conf`, and the two tasks it does not ship — `resetdb` and
`readmegif` — are in `run.tasks.sh`. Improve the runner upstream and copy it back rather
than editing it here.

### The README's GIF

The README opens with `docs/create-a-poll.gif`, a pass through calling a poll. **A change
to any screen in that flow makes it stale**, and the README is supposed to describe the
application as it is now — so re-record it in the same change:

```
./run.sh readmegif https://whichday.example.org/
```

It drives a headless Chromium over the DevTools protocol, forces light mode so every
reader sees one look, screenshots each step and assembles the frames with ImageMagick.
Three things have to be on the machine, none of them installed by this repository: a
Chromium (Playwright's cached one is found automatically, or set `CHROMIUM`), ImageMagick,
and Python's `websockets` package. The script names whichever is missing.

Two things to know before running it. It wants a **real deployment** rather than
localhost, because the share screen puts the voting link on screen and the README's
readers cannot follow `localhost:8080/vote/…`. And it **calls a real poll** on whatever it
points at, which the retention sweep clears in ninety days.

The steps read the UI's own English labels — "Continue", "Choose the days" — so a change
to `translations.properties` can stop the recording rather than the application. It fails
naming the label it could not find.

## Decisions and known problems

[`docs/REQUIREMENTS.md`](docs/REQUIREMENTS.md) is the design record: every requirement
and decision with the reason behind it. Read it before changing behaviour, and add to
it when you take a new decision. [`docs/issues/`](docs/issues/) records what is wrong
and what fixing it would take.
