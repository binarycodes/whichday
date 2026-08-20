# Whichday

Put a few days on the table and let a team pick every one that works. Whole days
only, multi-select voting, and the day with the most votes wins.

A mobile-first Vaadin application on the Aura theme.

## Running it

Login is required, so it needs an OAuth client before it will start:

```bash
export WHICHDAY_OIDC_CLIENT_ID=...apps.googleusercontent.com
export WHICHDAY_OIDC_CLIENT_SECRET=...
./run.sh run
```

Open <http://localhost:8080> and it redirects you to the provider. The client's
authorised redirect URI must be `http://localhost:8080/login/oauth2/code/oidc`. Set
`WHICHDAY_OIDC_ISSUER_URI` to use a provider other than Google.

There is no database and no container to bring up.

## The screens

| Route                | What it is                                            |
| -------------------- | ----------------------------------------------------- |
| `/`                  | Your polls, your drafts, and the settled ones         |
| `/new`               | Name a poll and say who decides it                    |
| `/new/invitees`      | Find people by email address                          |
| `/poll/:slug/days`   | Put candidate days on the table                       |
| `/poll/:slug/share`  | The voting link, the invite list, the closing date    |
| `/poll/:slug`        | The standings, live, and the button that locks a date  |
| `/poll/:slug/locked` | The settled date                                      |
| `/vote/:slug`        | Tap every day that works                              |
| `/vote/:slug/none`   | None of them work, and a day forward instead          |
| `/vote/:slug/done`   | What you answered, with the standings underneath      |

Every route requires a signed-in user. OIDC is the only way to sign in, there is no
login view, and the application collects no credentials.

## Inviting people

There is no team and no directory. The only way onto a poll is the organizer typing
an email address; matches appear from three characters, and an address with no account
behind it becomes an invitation rather than an error.

An account is somebody who has signed in — that is the only way `AccountDirectory`
learns about anybody, so a colleague becomes findable once they have signed in at
least once.

## Where the polls live

In memory, in a `PollService` singleton. Nothing seeds it and a restart loses
everything. Reads return immutable records and the mutable types never leave the
service package, so a persistence tier goes behind that boundary without a view
changing.

## Styling

Aura supplies the palette, the type and the light/dark switch, and the application
follows the reader's system preference. Every colour it adds is a semantic token in
`src/main/resources/META-INF/resources/styles/colors.css`, referenced through
`var(--color-…)`. The entry stylesheet holds nothing but `@import` lines — **after
editing a partial, run `./run.sh styles`**, which copies them to `target/classes` and
busts the browser cache.

One column, capped at 30rem and centred: it fills a phone and does not stretch across
a desktop.

## Tasks

`./run.sh` with no arguments lists them all. The ones you want:

| Task     | What it does                                                     |
| -------- | ---------------------------------------------------------------- |
| `run`    | Start the app in dev mode                                        |
| `test`   | Unit and browserless tests, with the JaCoCo gate                 |
| `verify` | The same against a production build                              |
| `styles` | Copy edited stylesheets to `target/classes` and bust the cache   |
| `deps`   | Fetch newly added dependencies (every other task builds offline) |

`run` needs the OIDC variables above; the tests do not. `./run.sh` pins JDK 21, and
every build carries the commit SHA — the enforcer plugin rejects one that does not.

## Decisions

Where the implementation had to settle something, or departs from the design,
[`docs/clarifications/`](docs/clarifications/) says what was chosen and why.
