# Whichday

Put a few days on the table and let a team pick every one that works. Whole days
only, multi-select voting, and the day with the most votes wins.

A mobile-first Vaadin application on the Aura theme, built from
`Date Voting.dc.html` in the Claude Design project *Voting app UI mockups*.

Signing in is the only way in, so it needs an OAuth client before it will start:

```bash
export WHICHDAY_OIDC_CLIENT_ID=...apps.googleusercontent.com
export WHICHDAY_OIDC_CLIENT_SECRET=...
./run.sh run
```

Then open <http://localhost:8080>, which redirects you to Google. The client's
authorised redirect URI must be `http://localhost:8080/login/oauth2/code/oidc`.
There is no database and no container to bring up.

## The flow

Ten screens, in two groups.

**The happy path.** An organizer names the poll and says who decides it (`/new`,
and `/new/invitees` to search), puts days on the table from a month grid
(`/poll/:slug/days`), shares one link and sends the invites
(`/poll/:slug/share`). Everybody invited taps every day that works
(`/vote/:slug`) and gets a receipt with the standings underneath
(`/vote/:slug/done`). The organizer watches the counts come in (`/poll/:slug`) and
locks the winner, which turns the whole screen over to the date
(`/poll/:slug/locked`). `/` lists the polls you are part of.

**The edges.** A voter who can make none of the days says so and puts a day
forward instead (`/vote/:slug/none`); the proposal appears on the organizer's
results screen and becomes a candidate day only if they accept it. A poll nobody
has answered yet keeps its rows and shows who is still owed an answer — the same
route as the standings, not a screen of its own.

## Inviting people

There is no team and no directory. The only way onto a poll is the organizer typing
an email address: results appear from three characters, matches show why they
matched — the polls you have both answered, or nothing more than a shared workspace
— and anything that is an address but not an account becomes an invitation rather
than an error.

An account is somebody who has signed in; that is the only way `AccountDirectory`
learns about anybody, and it has no method that hands a screen everybody. So a
colleague becomes findable once they have signed in at least once.

## Signing in

Login is required. Every route needs a signed-in user, OIDC is the only way to sign
in, and there is no login view — an unauthenticated request redirects straight to the
provider. The application collects no credentials and never sees one.

The registration is called `oidc` rather than after the provider behind it, so the
vendor stays out of the application's URLs and out of `src/main`; which provider it is
belongs to `WHICHDAY_OIDC_ISSUER_URI` and to nothing else. It refuses to start without
a client id and secret.

## Where the polls live

In memory, in a `PollService` singleton, seeded with the data the design was drawn
with: the Q3 offsite, seven people, five candidate days, standings of 6 / 4 / 3 / 2
/ 1, and Jonas Wirtanen holding out so the nudge prompt has a name. The account list
holds two people outside that team, so searching has something to narrow. The dates are
anchored to the clock rather than written out, so the sample stays in the future.

Restarting loses everything typed and brings the sample back. The service boundary
is nonetheless where a persistence tier goes: reads return immutable records, and
the mutable types never leave the service package.

## Sharing does real work

"Copy" writes the absolute voting link to the clipboard. "Message" is a `mailto:`
the browser hands to whatever mail client the reader uses. "Add to calendar"
generates an iCalendar file — all-day events, since the whole product is whole
days. There is no mail server and no calendar integration behind any of it, and
none of them pretend otherwise.

## Layout and theming

Aura supplies the palette, the type and the light/dark switch; the application
follows the reader's system preference and remembers no choice of its own. Every
colour it adds is a semantic token in
`src/main/resources/META-INF/resources/styles/colors.css`, and component
stylesheets reference them through `var(--color-…)`. The entry stylesheet holds
nothing but `@import` lines — after editing a partial, run `./run.sh styles`.

The design draws 390×844 phone frames. The application caps one column at 30rem
and centres it, so it fills a phone and does not stretch across a desktop.

## Decisions the design left open

Every one is written down in [`docs/clarifications/`](docs/clarifications/) —
scope and scaffolding, the in-memory store, voter identity, what the calendar
offers, who a poll goes to, and each place the implementation departs from the
design and why.

## Tasks

`./run.sh` with no arguments lists them. The ones you want:

| Task      | What it does                                                     |
| --------- | ---------------------------------------------------------------- |
| `run`     | Start the app in dev mode                                        |
| `test`    | Unit and browserless tests, with the JaCoCo gate                 |
| `verify`  | The same against a production build                              |
| `styles`  | Copy edited stylesheets to `target/classes` and bust the cache   |
| `deps`    | Fetch newly added dependencies (every other task builds offline) |

`run` needs `WHICHDAY_OIDC_CLIENT_ID` and `WHICHDAY_OIDC_CLIENT_SECRET` in the
environment; the tests do not.

`./run.sh` pins JDK 21, and every build carries the commit SHA — the enforcer
plugin rejects one that does not.
