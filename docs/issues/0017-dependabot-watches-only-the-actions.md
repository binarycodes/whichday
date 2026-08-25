# Dependabot watches the workflow actions and nothing else

**Severity:** low — everything is current today, and nothing is watching to keep it
that way.

## What happens

`.github/dependabot.yml` configures one ecosystem, `github-actions`. There is no
`maven` entry, so nothing raises a pull request when Spring Boot, Spring Security,
Vaadin, Hibernate or H2 publishes a release — including one that fixes a
vulnerability. The versions in `pom.xml` are recent and clean; that is the state
somebody left them in, not a state anything maintains.

The application is a single jar with an embedded database and no service around it,
so its dependencies are the whole of its attack surface that is not written here.

## What fixing it looks like

A second `updates:` entry for `package-ecosystem: maven`, grouped the way the actions
one is so that patch churn stays a single review, and on the same monthly schedule.
Two decisions come with it:

- **Whether the Vaadin BOM is in the group.** `vaadin.version` moves the whole
  platform including the frontend bundle, so a minor bump is a real review and
  probably wants its own pull request rather than a place in the patch group.
- **Whether CI is enough to merge on.** `mvn verify` runs the unit tests, the
  Playwright journey and the coverage gate, which is a genuine signal — but the
  frontend bundle rebuild on a Vaadin bump is the part that fails late and quietly,
  and `./run.sh verify` clears the bundles first for exactly that reason.

Security updates are the half that matters and the half that should not wait for a
monthly window; Dependabot raises those as they land regardless of schedule, which is
the argument for configuring it rather than reading release notes.
