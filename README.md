# Whichday

Put a few days on the table and let a team pick every one that works. Whole days
only, multi-select voting, and the day with the most votes wins.

A mobile-first Vaadin application on the Aura theme.

## Run it (self-hosting)

A prebuilt, multi-architecture image (`linux/amd64` + `linux/arm64`) is published to
Docker Hub at **[`binarycodes/whichday`](https://hub.docker.com/r/binarycodes/whichday)**.
It is one container with no database and nothing else to bring up.

State is held in memory and does not survive a restart
([issue 0001](docs/issues/0001-polls-are-lost-on-restart.md)).

- Use `binarycodes/whichday:latest` for the newest build, or pin a version tag —
  images are also tagged with the project's Maven version.
- The app listens on port **8080**. Override it with `PORT`: `-e PORT=9090 -p 9090:9090`.
- Signing in is configured with `WHICHDAY_OIDC_CLIENT_ID` and
  `WHICHDAY_OIDC_CLIENT_SECRET`, and it refuses to start without them. Set
  `WHICHDAY_OIDC_ISSUER_URI` for a provider other than Google.
- Behind a reverse proxy, see *Behind a proxy*.

### docker compose

Replace `whichday.example.com` with the address readers will actually type: it has to
match the redirect URI registered with your OAuth client.

```yaml
services:
  whichday:
    image: binarycodes/whichday:latest
    ports:
      - "8080:8080"
    environment:
      - WHICHDAY_OIDC_CLIENT_ID=change-me.apps.googleusercontent.com
      - WHICHDAY_OIDC_CLIENT_SECRET=change-me
      # Only for a provider other than Google.
      # - WHICHDAY_OIDC_ISSUER_URI=https://accounts.example.com
      # Required if anything terminates TLS in front of this; see below.
      - FORWARD_HEADERS_STRATEGY=native
    restart: unless-stopped
```

No volume, because there is nothing durable to put in one.

### Podman Quadlet (systemd)

If you run [Podman](https://podman.io), a [Quadlet](https://docs.podman.io/en/latest/markdown/podman-systemd.unit.5.html)
lets systemd manage the container declaratively — start on boot, restart on failure,
and optional auto-updates — without a long-running daemon. One container means one
unit file, and no network of its own: there is nothing for it to find by name.

It goes in `/etc/containers/systemd/` (rootful) or `~/.config/containers/systemd/`
(rootless).

`whichday.container`:

```ini
[Unit]
Description=Whichday

[Container]
ContainerName=whichday
Image=docker.io/binarycodes/whichday:latest
PublishPort=8080:8080
Environment=WHICHDAY_OIDC_CLIENT_ID=change-me.apps.googleusercontent.com
Environment=WHICHDAY_OIDC_CLIENT_SECRET=change-me
# Required if anything terminates TLS in front of this; see below.
Environment=FORWARD_HEADERS_STRATEGY=native
# Opt in to `podman auto-update` pulling newer :latest images.
AutoUpdate=registry

[Service]
Restart=always

[Install]
# multi-user.target for rootful; default.target for a rootless --user unit.
WantedBy=multi-user.target default.target
```

Then reload systemd and start it (add `--user` for the rootless path):

```bash
systemctl daemon-reload
```

```bash
systemctl start whichday.service
```

`AutoUpdate=registry` plus `podman-auto-update.timer` keeps it on the latest
published image, restarting the container to do it.

### Behind a proxy

Anything that terminates TLS in front of this leaves the application seeing plain
HTTP on an internal address, and it builds its OIDC redirect URI from the request —
so it would send the provider `http://172.17.0.3:8080/login/oauth2/code/oidc`, which
is not the registered URI and is refused.

Set `FORWARD_HEADERS_STRATEGY=native` to trust the proxy's `X-Forwarded-*` headers,
and the redirect URI comes out as the address readers typed. It is off by default
because those headers are spoofable with nothing in front, so set it when there is a
proxy and only then.

Register `https://whichday.example.com/login/oauth2/code/oidc` with your OAuth
client. Google requires HTTPS for every redirect URI except `localhost`.

## The screens

| Route              | What it is                                            |
| ------------------ | ----------------------------------------------------- |
| `/`                | Your polls, your drafts, and the settled ones         |
| `/new`             | Name a poll and say who decides it                    |
| `/new/invitees`    | Find people by email address                          |
| `/poll/:id/days`   | Put candidate days on the table                       |
| `/poll/:id/share`  | The voting link, the invite list, the closing date    |
| `/poll/:id`        | The standings, live, and the button that locks a date  |
| `/poll/:id/locked` | The settled date                                      |
| `/vote/:id`        | Tap every day that works                              |
| `/vote/:id/none`   | None of them work, and a day forward instead          |
| `/vote/:id/done`   | What you answered, with the standings underneath      |

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

In memory, in a `PollService` singleton, and nothing seeds it. Reads return immutable
records and the mutable types never leave the service package, so a persistence tier
goes behind that boundary without a view changing —
[issue 0001](docs/issues/0001-polls-are-lost-on-restart.md).

## Develop locally

Login is required here too, so it needs an OAuth client before it will start:

```bash
export WHICHDAY_OIDC_CLIENT_ID=...apps.googleusercontent.com
export WHICHDAY_OIDC_CLIENT_SECRET=...
./run.sh run
```

Open <http://localhost:8080> and it redirects you to the provider. Register
`http://localhost:8080/login/oauth2/code/oidc` with the client — `localhost` is the
one host Google allows over plain HTTP. `PORT` moves the port and the redirect URI
follows it, so register that one too.

### Tasks

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

### Styling

Aura supplies the palette, the type and the light/dark switch, and the application
follows the reader's system preference. Every colour it adds is a semantic token in
`src/main/resources/META-INF/resources/styles/colors.css`, referenced through
`var(--color-…)`. The entry stylesheet holds nothing but `@import` lines — **after
editing a partial, run `./run.sh styles`**, which copies them to `target/classes` and
busts the browser cache.

One column, capped at 30rem and centred: it fills a phone and does not stretch across
a desktop.

## Decisions and known problems

[`docs/clarifications/`](docs/clarifications/) records what was settled deliberately,
and why. [`docs/issues/`](docs/issues/) records what is wrong and what fixing it would
take — read that before deploying this anywhere that matters.
