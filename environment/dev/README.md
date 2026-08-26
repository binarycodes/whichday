# Development environment

The one service Whichday talks to while you are working on it, and **only in login
mode**. The polls live in an H2 file the application opens itself
(`docs/REQUIREMENTS.md` §9), so there is no database to bring up; anonymous mode — the
default — has no provider either, so `./run.sh run` on its own needs none of this.

```bash
./run.sh env up
```

Then start the app in login mode; `application-login.properties` defaults to exactly
this Keycloak, so no configuration is needed beyond naming the mode.

```bash
WHICHDAY_ACCESS_MODE=login ./run.sh run
```

Open <http://localhost:8080> and sign in as **`ada` / `ada`**.

Login mode fetches the issuer's discovery document at startup, so it fails immediately
with a connection error against a stopped stack rather than at the first sign-in. That
is the right order to find out in — and it is why the OIDC keys live in the login
profile's file rather than in `application.properties`, where anonymous mode would
inherit them and try the same fetch for nothing.

One task for the whole stack rather than one per service: which containers it brings
up is the stack definition's decision, so a service added there needs no change to
`run.sh`. `./run.sh env down` stops them and keeps the data; `./run.sh env reset`
throws the volumes away. `./run.sh env logs` follows them.

## Two runtimes, two definitions

The stack is defined twice, and `./run.sh env` picks by what is installed.

Under **docker** it is `compose.yaml`, run with `up -d --wait`.

Under **podman** it is the quadlets in `quadlet/` — systemd units that podman's
generator turns into services — because compose is not how podman is meant to be
driven. `systemctl` starts them, and `Notify=healthy` on the Keycloak container is what
makes systemd treat the unit as started only once its healthcheck passes: that is what
buys the ordering `depends_on: service_healthy` and `up --wait` bought under compose,
which is the whole reason the realm setup can assume Keycloak is answering.

The files are templates (`*.in`). A quadlet cannot say where this checkout lives, and
two of them mount a file out of this directory, so `./run.sh env up` substitutes the
path and copies them into `~/.config/containers/systemd`. Editing a unit therefore
means re-running `./run.sh env up`.

The units are started, never enabled — whether this stack should come back after a
reboot is not `run.sh`'s call. `systemctl --user enable whichday-dev-keycloak.service`
if you want it to.

Keeping both definitions in step is manual: a service added to one needs adding to
the other.

## The realm

Keycloak starts empty. `keycloak/init.mjs` then creates the realm over the admin REST
API, from a `keycloak-init` container that runs once and exits — so a fresh checkout
plus `./run.sh env up` is a working identity provider with no manual steps and no
clicking through the admin console.

Not a realm export imported with `--import-realm`. An export has to be an exactly
correct `RealmRepresentation` or the container refuses to boot, and it says so through
a Jackson "unrecognized field" error that names nothing about Keycloak. A wrong field
here is an HTTP 400 that names it instead. Each step looks up what it would create and
skips it if it is already there, so re-running `./run.sh env up` is safe.

Keycloak reports readiness on its management port, and the image carries no curl and no
wget — so `keycloak/HealthCheck.java` is the probe, run straight from source by the
image's own JVM. `keycloak-init` waits on that rather than on the port, because
Keycloak binds long before it can answer.

| | |
|---|---|
| Realm | `whichday`, at <http://localhost:8082/realms/whichday> |
| Client | `whichday`, confidential, secret `whichday-dev-secret` |
| Ada | `ada` / `ada` — `ada.lindqvist@acme.com` |
| Miro | `miro` / `miro` — `m.kallio@acme.com` |
| Admin console | <http://localhost:8082>, `admin` / `admin` |

**Two people, because one cannot invite anybody.** The create flow ends at an invitee
search that only offers accounts which exist, so a realm with a single user is one
where a poll cannot be finished. Sign in as Ada, invite Miro, then answer as Miro from
a private window.

The addresses are the ones the design was drawn with (`Sample` in the test tree), and
they are also what the polls are keyed by: Whichday identifies a person by their
address and nothing else. So **throwing the Keycloak container away costs nothing but
the passwords** — the same addresses come back to the same polls. An application that
keyed on the provider's subject would orphan its data here.

Whichday does not yet check `email_verified`
([`../../docs/issues/0010-a-signed-in-address-is-never-verified.md`](../../docs/issues/0010-a-signed-in-address-is-never-verified.md)),
but both users are created with it set, so this realm keeps working on the day it does.

**A third person**, for a bigger poll: add a user in the admin console with a first
name, a last name, an address and a password, and they are searchable the first time
they sign in — the account table is written on the way past, never seeded.

The client accepts `*` as its redirect URI. A realm's redirect URIs are not Whichday's
to get right — a deployment adds Whichday's exact callback
(`https://your-whichday/login/oauth2/code/oidc`, where `oidc` is Whichday's registration
id rather than the realm or the client) to a provider it already runs, very likely
alongside other applications. What this realm exists to prove is that Whichday speaks
OIDC, so it accepts whatever port a laptop happens to be on.

## What is fine here and nowhere else

Every credential in this directory is published and guessable — `admin`/`admin` on the
Keycloak console, a client secret in version control, and a client that will redirect
anywhere at all. An unrestricted redirect URI is normally a finding, and it is one here
too the moment this reaches a machine anyone else can reach.

**This is a laptop's configuration**, and so are the three defaults in
`application.properties` that point at it. A deployment sets `WHICHDAY_OIDC_ISSUER_URI`,
`WHICHDAY_OIDC_CLIENT_ID` and `WHICHDAY_OIDC_CLIENT_SECRET` against a realm of its own,
with its own secret and its own exact redirect URI. Nothing here is a starting point
for that.

Requires either podman, or docker with the Compose plugin (`docker compose` or the
standalone `docker-compose`). Rootless podman also needs its socket listening —
`systemctl --user start podman.socket` — which `run.sh` will tell you about rather than
do for you. The test suite needs none of this: every tier stubs the identity provider
rather than starting one
([`../../docs/issues/0006-the-real-sign-in-is-never-exercised.md`](../../docs/issues/0006-the-real-sign-in-is-never-exercised.md)).
