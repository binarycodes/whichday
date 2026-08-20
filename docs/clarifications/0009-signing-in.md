# Signing in

## Decided

Signing in is the only way in, and OIDC is the only way to sign in. There is no
login view: `oauth2LoginPage` points at the registration, so an unauthenticated
request redirects to the provider. The application collects no credentials and never
sees one.

Every `@Route` carries `@PermitAll`, and so does `MainLayout` — an unannotated parent
layout denies everything behind it, and the symptom is a `RouteNotFoundError` with a
log line about the view allowing broader access than the layout
(`CODING_CONVENTIONS.md` §10b). "Authenticated" is the whole authorization model;
there are no roles.

## The registration is called `oidc`, not `google`

Spring builds `/oauth2/authorization/oidc` and the `/login/oauth2/code/oidc`
callback from the registration id, so a vendor named there ends up in the
application's own URLs and in every deployment's provider configuration (§10b). It
is Google — that is what the issuer resolves to — but nothing in `src/main` says so,
and moving to another provider is one environment variable.

The cost is two lines of configuration. Spring infers the grant type and the redirect
URI for provider ids it recognises, and `oidc` is deliberately not one of them, so
both are stated.

## It refuses to start without credentials

Spring does not do this for us, and finding out why took observing rather than
reasoning. An unresolved `${...}` placeholder in a properties file **binds as the
literal string**. The application started happily, fetched Google's discovery
document, and redirected to a real authorization endpoint carrying
`client_id=${WHICHDAY_OIDC_CLIENT_ID}` — so the first person to try signing in would
have met Google's error page, with nothing in our own logs to explain it.

`SecurityConfig.requireCredentials` therefore checks the resolved registration and
fails startup with a message naming the variables to set. Signing in is the only way
in, so a missing client is a misconfiguration, not a surprise for later.

```
WHICHDAY_OIDC_CLIENT_ID      from a Google OAuth client
WHICHDAY_OIDC_CLIENT_SECRET  the same client's secret
WHICHDAY_OIDC_ISSUER_URI     only to point at a different provider
```

The client's authorised redirect URI must be `<base-url>/login/oauth2/code/oidc`.

## An account is somebody who has signed in

`AccountDirectory` used to hold nine names. Nothing seeds it now: it remembers
whoever authenticates, on the way past in `AuthenticatedViewerSession.viewer()`, and
that is the only way it learns about anybody. Invitee search looks there, so a
colleague becomes findable once they have signed in at least once — which is also the
honest reading of the design's "only accounts that share a workspace or a past poll
with you".

Nothing seeds the polls either. The first person to sign in gets an empty list.

## The account switcher is gone

It existed only because there was no login, and its own clarification said to delete
it when there was one. The avatar it lived on now opens a menu with the one thing an
account can do here: sign out. `OidcClientInitiatedLogoutSuccessHandler` makes that
mean what it says — dropping only our session would leave the provider's intact and
the next visit would sign straight back in.

## Voters have to sign in too

The design promises "No sign-up for voters. One link, one tap." That is now
definitively not true: strict means strict, and `/vote/:slug` requires a login like
every other route. It is the one design promise this application breaks outright
rather than approximating, and it is the direct consequence of the decision.

If it matters, it is not a small change: a per-invitee token in the voting link, a
route that accepts it without a session, and a way for an answer to be attributed to
somebody who has no account.

## Testing it

Every Spring test names the `test` profile. A profile can override a key but never
remove one, and Boot resolves an issuer by fetching its discovery document at
startup — so a test that inherited `provider.oidc.issuer-uri` would hang the context
on `accounts.google.com`, and blanking it fails as "issuer cannot be empty". The
profile points the registration at a second provider whose endpoints cannot resolve,
leaving the inherited issuer unreferenced (§11).

`StubIdentity` signs somebody in by putting a real `OAuth2AuthenticationToken` into
the security context, rather than by replacing `ViewerSession`. A browserless test
bypasses the servlet filter chain but not `SpringNavigationAccessControl`, so a
`@PermitAll` route needs an authenticated principal or navigation lands nowhere — and
going through the token means the application's own `AuthenticatedViewerSession` is
what the tests exercise, claims and all.

## Known costs

- **The journey suite is much slower.** `@DirtiesContext` rebuilds the Spring context
  per test method, which is what keeps a shared `PollService` from leaking polls
  between tests now that nothing reseeds it. 44 tests take about 90 seconds where they
  used to take 6.
- **No integration tier drives a real sign-in.** There is no test Google account, so
  the OAuth2 redirect, the token exchange and the callback are exercised only as far
  as the authorization redirect. That was verified by hand: `/` redirects to
  `/oauth2/authorization/oidc`, which redirects to Google's authorize endpoint with the
  configured client and scopes.
