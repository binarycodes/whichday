# A signed-in address is trusted without the provider saying it is verified

**Severity:** medium — the address is the whole permission model, and the one claim
that says the address is really theirs is never read.

## What happens

`AuthenticatedViewerSession.personFor` reads `OidcUser.getEmail()` and builds a
`Person` out of it. Everything downstream compares addresses: `poll`, `castVote`,
`decline` and `requireOrganizer` all go through `PollService.addressOf`. The address
*is* the identity ([`../REQUIREMENTS.md`](../REQUIREMENTS.md) §2) — and it arrives on
trust.

OIDC has a claim for exactly this question, `email_verified`, and nothing here reads
it. Against a provider that will issue a token carrying an address its holder never
proved, registering as `sixten@acme.com` is enough to read every poll Sixten was
invited to, to answer in his name, and — if he called one — to settle it.

The subject fallback two lines down is the same hole from the other side. An account
whose provider withholds an email falls back to the `sub` claim, unprefixed, and it
lands in the same column the addresses do. A provider whose subjects are its users'
to choose can therefore return a `sub` of `sixten@acme.com` and be Sixten.

## Why it is like this

The default issuer is the development Keycloak in `environment/dev`, whose realm sets
`emailVerified: true` on both of its people and turns self-registration off. So a
laptop is not exposed — by that realm's configuration, though, not by anything here.

That is the whole of the protection. `WHICHDAY_OIDC_ISSUER_URI` is a deployment's to
set, and the registration is deliberately named `oidc` rather than after any vendor,
precisely so that it can be anything. A provider that verifies an address before
putting it in a token — Google does, and issues numeric subjects that cannot collide
with one — makes this safe. A self-hosted Keycloak or Authentik with registration open
is the configuration it fails on, and that is the same software the default points at
with one realm setting changed.

## What fixing it looks like

Two changes in `personFor`, both small:

- Refuse a token whose `email_verified` is not `true` when it carries an email at
  all. The refusal belongs where the claim is read, not on a screen, and it wants a
  message that says which claim was missing — a person who cannot sign in and is told
  nothing has no way to find out why.
- Namespace the subject fallback, so a subject can never be read as an address. A
  `sub:` prefix is enough: `EmailAddress.isWellFormed` already rejects it, and
  §1 already says such a person cannot be invited.

`AuthenticatedViewerSessionTest` builds an `OidcUser` directly, so both cases are
reachable there — which is where they have to be met, since no tier starts a provider
and the real token exchange is exercised nowhere.
