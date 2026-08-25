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

The default issuer is Google, which verifies addresses before it puts them in a
token and issues numeric subjects that cannot collide with one. A default deployment
is not exposed, and the code was written against that provider.

But `WHICHDAY_OIDC_ISSUER_URI` is a deployment's to set, and the registration is
deliberately named `oidc` rather than after any vendor, precisely so that it need not
be Google. Which provider it is, is not this application's to assume — a self-hosted
Keycloak or Authentik with open registration is the configuration this fails on.

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
reachable there. The real token exchange is still untested for the separate reason in
[`0006-the-real-sign-in-is-never-exercised.md`](0006-the-real-sign-in-is-never-exercised.md).
