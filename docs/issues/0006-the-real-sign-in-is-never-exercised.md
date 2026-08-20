# No test drives a real sign-in

**Severity:** medium — the most fragile path has the least cover.

## What happens

`SecurityConfigTest` proves that an unauthenticated request is redirected to
`/oauth2/authorization/oidc` and that the stylesheets are served without a login.
Past that redirect, nothing is tested: the token exchange, the callback at
`/login/oauth2/code/oidc`, reading the claims out of a real id token, and RP-initiated
logout are all unexercised.

`StubIdentity` puts a hand-built `OAuth2AuthenticationToken` into the security
context, which does cover `AuthenticatedViewerSession` reading claims — but the token
never came from a provider, so nothing checks that a real one has the shape the code
expects.

What has been checked, by hand rather than by a test: the redirect chain reaches
Google's authorize endpoint with the configured client, scopes and a `redirect_uri`
built from the registration id.

## Why it is like this

There is no test Google account, and `CODING_CONVENTIONS.md` §11's pattern for this —
a journey tier that starts a real identity provider in a container and drives its
login form — needs a provider that can be started, which Google cannot.

## What fixing it looks like

Two options, and the second is better:

1. A journey tier that starts a local OIDC provider in a container, points the
   registration at it through `@DynamicPropertySource`, and drives its login form with
   Playwright. It exercises the whole flow but tests a provider that is not the one
   deployments use.
2. `MockMvc` or a `WebTestClient` against the callback endpoint with a signed token
   from a local JWK set. Narrower, much faster, and it tests our own code — the
   callback, the claims, the session — rather than somebody else's login page.

## Related

The journey suite is also slow: `@DirtiesContext` rebuilds the Spring context per
test method, which is what keeps a shared `PollService` from leaking polls between
tests now that nothing seeds it. Forty-four tests take about ninety seconds where
they used to take six. Fixing it wants a deliberate reset seam on the service rather
than a back door, and it gets easier once
[`0001-polls-are-lost-on-restart.md`](0001-polls-are-lost-on-restart.md) gives the
store a real lifecycle.
