# Whichday

Put a few days on the table and let a team pick every one that works. Whole days
only, multi-select voting, and the day with the most votes wins.

A mobile-first Vaadin application on the Aura theme.

## Two ways in

A deployment picks one, once, with `WHICHDAY_ACCESS_MODE`. It cannot be both, and it
cannot be switched at runtime.

| | `anonymous` (the default) | `login` |
| --- | --- | --- |
| Getting in | type a name on the way past | an OIDC provider you run |
| Configuration | none | `WHICHDAY_OIDC_*`, and it will not start without them |
| Seeing a poll | anybody holding its link | the organizer or somebody invited |
| Answering one | anybody holding its link | the invitee, signed in at the address they were invited at |
| Changing one | the session that called it, or anybody with its six-digit admin code | the organizer |
| Home (`/`) | where a poll starts — there is no list | your polls, drafts and settled ones |
| Invitations | none; the link is the invitation | typed in by address, searched by account |

**Anonymous mode is Doodle's bargain, and it is worth reading twice.** Anybody who has
the link can open the poll and answer it — that is the point of not having accounts.
The organizer gets a six-digit code on the share screen, and it is the only way back to
changing a poll once the browser tab is gone: identity lives in the session and nothing
else. Nobody is emailed, nobody is reminded, and nothing knows who has not answered,
because nothing knows who was asked.

Choose `login` when the polls are a company's and it already has a provider. Choose
`anonymous` when the point is that a group can pick a day without anybody signing up
for anything.

## Run it (self-hosting)

A prebuilt, multi-architecture image (`linux/amd64` + `linux/arm64`) is published to
Docker Hub at **[`binarycodes/whichday`](https://hub.docker.com/r/binarycodes/whichday)**.
It is one container with its database inside it, and nothing else to bring up.

- Images are tagged with the project's Maven version as well as `latest`.
- The app listens on port **8080**. Override it with `PORT`: `-e PORT=9090 -p 9090:9090`.
- Polls, ballots and accounts live in one database file, `whichday.mv.db`, in the
  directory the image calls `/app/data`. `WHICHDAY_DATA_DIR` moves it.
- **Mount a volume there.** The image declares `/app/data` a volume, so the database
  never lands in the container's own filesystem — but forget the mount and it goes to an
  anonymous volume instead, which the next `docker run` will not reuse and
  `docker run --rm` deletes outright. Recoverable from `docker volume ls`, and not where
  you want it.
- **Run one container, not two.** The database is a file, and only the process holding
  it can open it — a second container on the same volume will not start.
- **Polls do not stay forever.** A poll is deleted five days after it ends
  (`WHICHDAY_RETENTION_AFTER_POLL_ENDS`), and no poll survives 90 days from the day it
  was made (`WHICHDAY_RETENTION_DAYS`) — whatever state it is in, a draft nobody sent
  and a poll still collecting answers included. Deleting a poll deletes the answers with
  it and cannot be undone, so a saved link then reads as a link to a poll that never
  existed. Both are whole days, and either is `never` to switch that window off.
  `WHICHDAY_RETENTION_DAYS` also drops the names anonymous visitors typed, once no poll
  refers to them any more.
- `WHICHDAY_ACCESS_MODE` is `anonymous` or `login`, and defaults to `anonymous`.
  Anything else is a startup failure naming both.
- **`login` mode needs an OIDC client**: `WHICHDAY_OIDC_ISSUER_URI`,
  `WHICHDAY_OIDC_CLIENT_ID` and `WHICHDAY_OIDC_CLIENT_SECRET`. It refuses to start
  without the id and the secret, and it resolves the issuer as it starts — so an
  unreachable provider is a container that will not come up. `anonymous` mode reads
  none of the three, and does not need the provider to exist.
- Behind a reverse proxy, see *Behind a proxy*. It matters for `login` mode and for
  the share links both modes hand out.

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
      - WHICHDAY_ACCESS_MODE=login
      - WHICHDAY_OIDC_ISSUER_URI=https://accounts.example.com
      - WHICHDAY_OIDC_CLIENT_ID=change-me
      - WHICHDAY_OIDC_CLIENT_SECRET=change-me
      - FORWARD_HEADERS_STRATEGY=native
      - WHICHDAY_RETENTION_AFTER_POLL_ENDS=5
      - WHICHDAY_RETENTION_DAYS=90
    restart: unless-stopped

volumes:
  whichday-data:
```

Anonymous mode is the same file with the four `WHICHDAY_OIDC_*` and `ACCESS_MODE` lines
dropped — it is the default, and it configures nothing:

```yaml
    environment:
      - FORWARD_HEADERS_STRATEGY=native
      - WHICHDAY_RETENTION_AFTER_POLL_ENDS=5
      - WHICHDAY_RETENTION_DAYS=90
```

The left side of the mount is yours — the named volume above, or any host path. The
right side has to be whatever `WHICHDAY_DATA_DIR` says.

A named volume at the image's own `/app/data` is the case that needs nothing else: a
fresh one is seeded from that directory, ownership included. Anywhere else — a bind
mount, or a volume at a path the image never created — you make writable by uid 10001
yourself, which is the user inside the image.

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
Environment=WHICHDAY_ACCESS_MODE=login
Environment=WHICHDAY_OIDC_ISSUER_URI=https://accounts.example.com
Environment=WHICHDAY_OIDC_CLIENT_ID=change-me
Environment=WHICHDAY_OIDC_CLIENT_SECRET=change-me
Environment=FORWARD_HEADERS_STRATEGY=native
Environment=WHICHDAY_RETENTION_AFTER_POLL_ENDS=5
Environment=WHICHDAY_RETENTION_DAYS=90
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

Anonymous mode drops the same four lines here, leaving `FORWARD_HEADERS_STRATEGY` and
the two retention lines as the only `Environment=` the unit needs.

### Behind a proxy

Anything that terminates TLS in front of this leaves the application seeing plain
HTTP on an internal address, and it builds its OIDC redirect URI from the request —
so it would send the provider `http://172.17.0.3:8080/login/oauth2/code/oidc`, which
is not the registered URI and is refused.

Set `FORWARD_HEADERS_STRATEGY=native` to trust the proxy's `X-Forwarded-*` headers,
and the redirect URI comes out as the address readers typed. It is off by default
because those headers are spoofable with nothing in front, so set it when there is a
proxy and only then.

In `login` mode, the redirect URI to register with your OIDC client is
`https://whichday.example.com/login/oauth2/code/oidc`: the path is fixed, the origin is
whatever readers type.

## The screens

Two of these are `login` mode's alone. In `anonymous` mode `/` is where a poll starts,
`/new/invitees` leads back there, and `/who` stands in front of everything.

| Route              | What it is                                            |
| ------------------ | ----------------------------------------------------- |
| `/`                | Your polls, your drafts, and the settled ones — `login` mode |
| `/who`             | Your name, and an admin code if you have one — `anonymous` mode |
| `/new`             | Name a poll and say who decides it                    |
| `/new/invitees`    | Find people by email address — `login` mode           |
| `/poll/:id/days`   | Put candidate days on the table                       |
| `/poll/:id/share`  | The voting link, the invite list, the closing date    |
| `/poll/:id`        | The standings, live, and the way to settle them        |
| `/poll/:id/settle` | Confirm the day, or pick between tied ones            |
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

In an embedded H2 database — one file in the directory `WHICHDAY_DATA_DIR` names.
Flyway owns the schema, `ddl-auto=validate` refuses to start if the entities and the
migrations disagree, and nothing seeds it.

A poll row names people by email address; the one place a name lives is the `account`
table, written when somebody signs in. So an invitee who signs up later shows their
real name on polls that predate their account.

Nothing stays forever. A sweep runs daily and deletes a poll once either retention
window has passed it — five days after it ended, or 90 days after it was created,
whichever comes first. It takes the ballots, the invitations, the candidate days and the
proposals with it.

The same 90 days drop the names typed into anonymous mode's who-are-you screen, once no
poll refers to them any more — so a visitor who typed a name and closed the tab leaves
nothing behind. An address a provider vouched for is never dropped: it belongs to somebody
who can sign in again and be recognised.

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
