# Whichday

Put a few days on the table and let a team pick every one that works. Whole days
only, multi-select voting, and the day with the most votes wins.

A mobile-first Vaadin application on the Aura theme.

## Run it (self-hosting)

A prebuilt, multi-architecture image (`linux/amd64` + `linux/arm64`) is published to
Docker Hub at **[`binarycodes/whichday`](https://hub.docker.com/r/binarycodes/whichday)**.
It is one container with its database inside it, and nothing else to bring up.

- Images are tagged with the project's Maven version as well as `latest`.
- The app listens on port **8080**. Override it with `PORT`: `-e PORT=9090 -p 9090:9090`.
- Polls, ballots and accounts live in an embedded database at
  `/app/data/whichday.mv.db`. **Mount a volume at `/app/data`** and they survive a
  restart; without one they go when the container does. `WHICHDAY_DATA_DIR` moves the
  directory.
- **Run one container, not two.** The database is a file, and only the process holding
  it can open it — a second container on the same volume will not start.
- Signing in needs an OIDC client: `WHICHDAY_OIDC_ISSUER_URI`,
  `WHICHDAY_OIDC_CLIENT_ID` and `WHICHDAY_OIDC_CLIENT_SECRET`. The application refuses
  to start without the id and the secret.
- Behind a reverse proxy, see *Behind a proxy*.

### docker compose

```yaml
services:
  whichday:
    image: binarycodes/whichday:latest
    ports:
      - "8080:8080"
    volumes:
      - whichday-data:/app/data
    environment:
      - WHICHDAY_OIDC_ISSUER_URI=https://accounts.example.com
      - WHICHDAY_OIDC_CLIENT_ID=change-me
      - WHICHDAY_OIDC_CLIENT_SECRET=change-me
      - FORWARD_HEADERS_STRATEGY=native
    restart: unless-stopped

volumes:
  whichday-data:
```

A named volume as above picks up the right ownership on its own; a bind mount has to
be writable by uid 10001, the user inside the image.

### Podman Quadlet (systemd)

One container and no network of its own, so the whole deployment is a single unit —
plus the volume, for the same reason compose needs it.

`whichday.container`:

```ini
[Unit]
Description=Whichday

[Container]
ContainerName=whichday
Image=docker.io/binarycodes/whichday:latest
PublishPort=8080:8080
Volume=whichday-data:/app/data
Environment=WHICHDAY_OIDC_ISSUER_URI=https://accounts.example.com
Environment=WHICHDAY_OIDC_CLIENT_ID=change-me
Environment=WHICHDAY_OIDC_CLIENT_SECRET=change-me
Environment=FORWARD_HEADERS_STRATEGY=native
AutoUpdate=registry

[Service]
Restart=always

[Install]
WantedBy=multi-user.target default.target
```

Then reload systemd and start it:

```bash
systemctl daemon-reload
```

```bash
systemctl start whichday.service
```

`AutoUpdate=registry` is safe here because systemd stops the old container before
starting the new one, and only one process at a time may hold the database file.

### Behind a proxy

Anything that terminates TLS in front of this leaves the application seeing plain
HTTP on an internal address, and it builds its OIDC redirect URI from the request —
so it would send the provider `http://172.17.0.3:8080/login/oauth2/code/oidc`, which
is not the registered URI and is refused.

Set `FORWARD_HEADERS_STRATEGY=native` to trust the proxy's `X-Forwarded-*` headers,
and the redirect URI comes out as the address readers typed. It is off by default
because those headers are spoofable with nothing in front, so set it when there is a
proxy and only then.

The redirect URI to register with your OIDC client is
`https://whichday.example.com/login/oauth2/code/oidc`: the path is fixed, the origin is
whatever readers type.

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

**A poll is visible only to the person who called it and the addresses they invited.**
You sign in with the address you were invited at; anything else gets the not-found
screen, and it is the same screen an imaginary poll gets — the refusal does not confirm
that the link was real. Forwarding a voting link to somebody who was not invited
therefore gives them nothing.

**Everyone invited can answer; only the organizer can change anything.** Editing the
days, sending it, moving the closing date, accepting a proposed day, locking one in and
discarding a draft are all theirs alone. `PollService` refuses the rest, so a hidden
button is a courtesy rather than the check.

## Inviting people

There is no team and no directory. The only way onto a poll is the organizer typing
an email address; matches appear from three characters, and an address with no account
behind it becomes an invitation rather than an error.

An account is somebody who has signed in — that is the only way `AccountDirectory`
learns about anybody, so a colleague becomes findable once they have signed in at
least once, and stays findable across restarts.

Inviting an address that has no account yet works, and that person answers by signing
in with **that** address. An alias of it will not do: the invite list is matched
literally, so `bob+team@example.com` and `bob@example.com` are two different people as
far as a poll is concerned. And the list is fixed once the poll exists — there is no
way to add somebody afterwards
([issue 0007](docs/issues/0007-a-forgotten-invitee-cannot-be-added.md)).

## Where the polls live

In an embedded H2 database — one file under `./data` locally, `/app/data` in the
container. Flyway owns the schema, `ddl-auto=validate` refuses to start if the entities
and the migrations disagree, and nothing seeds it.

A poll row names people by email address; the one place a name lives is the `account`
table, written when somebody signs in. So an invitee who signs up later shows their
real name on polls that predate their account.

It is H2 rather than PostgreSQL on purpose:
[`docs/REQUIREMENTS.md`](docs/REQUIREMENTS.md) says why.

## Contributing

[`CONTRIBUTING.md`](CONTRIBUTING.md) covers running it locally, the `./run.sh` tasks and
the conventions every change follows.

## Decisions and known problems

[`docs/REQUIREMENTS.md`](docs/REQUIREMENTS.md) records what the application has to do
and every decision taken getting there, with the reason.
[`docs/issues/`](docs/issues/) records what is wrong and what fixing it would take —
read that before deploying this anywhere that matters.
