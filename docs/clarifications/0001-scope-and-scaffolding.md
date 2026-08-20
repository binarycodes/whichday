# Scope, naming and the imported scaffolding

## Decided

The application is **Find a Date** — artifact `findadate`, package
`io.binarycodes.findadate`. The design's "When2" wordmark is a placeholder and
lives in `translations.properties` as `app.name`, so renaming the product is one
key.

This first implementation is the **view layer plus an in-memory service**: every
screen in the design, backed by a session-scoped store rather than a database.
There is no PostgreSQL, no Flyway, no JPA and no OIDC login. The service
boundary is nonetheless the one `CODING_CONVENTIONS.md` §9/§10 describes — views
never see anything but records, and the service exposes no mutable type — so a
persistence tier can be dropped in behind it without a view changing.

## Consequences

Dependencies dropped from the imported `pom.xml`, none of which this tier can
reach: Spring Security and the OAuth2 client, Spring Data JPA, the PostgreSQL
driver, Flyway, jsoup, icu4j, httpclient5, ipaddress, PDFBox, Testcontainers.
`run.sh` lost its `env` task and its Docker-host resolution with them — nothing
here talks to a container.

Conventions §10, §10a and §10b describe a persistence and access-control tier
that does not exist yet. They are not violated; they are simply unreached. The
one place that shows is authorship: `PollService` attributes a new poll to a
fixed sample organizer where §10b would have `LibraryOwner.current()`.

## Open

- **`environment/` is still the imported template.** Its seven top-level files
  are byte-identical copies of the repository root's, and `environment/dev/`
  holds a compose stack — PostgreSQL, Keycloak, a headless Chromium — for an
  application that is not this one. Nothing references it any more. It should
  probably be deleted; that is a call for whoever owns the repository, and it is
  recoverable from the first commit either way.
- **Voter identity.** The design promises "No sign-up for voters. One link, one
  tap", which conventions §10b's mandatory login would forbid. This tier has no
  login at all, so the conflict is deferred rather than resolved — see
  `0003-voter-identity.md`.
