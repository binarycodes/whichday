# The response headers and the session cookie are whatever the defaults are

**Severity:** low — the defaults are good ones, and the two things they leave out are
the two a deployment behind a proxy needs.

## What happens

Neither filter chain says anything about headers. The only thing configured is that
they get written at all: `SecurityHeadersConfig` holds a `HeaderWriterFilter`
post-processor, once for both modes, because Spring writes headers as the response
commits and a Vaadin-rendered route never commits that way. So every response carries
Spring Security's defaults: `X-Frame-Options: DENY`, `nosniff`,
the cache headers that keep a page out of a store, and HSTS on a request that
arrived over TLS.

Two things are not in that set.

- **No `Content-Security-Policy`.** Spring Security writes one only when asked, and
  nothing asks.
- **The session cookie is not marked `Secure`, and says nothing about `SameSite`.**
  Tomcat marks the cookie secure when the request was secure, and behind a
  TLS-terminating proxy with `server.forward-headers-strategy` at its default of
  `none` the request never is — the application only ever sees plain HTTP. That
  default is right and deliberate (the headers are spoofable with nothing in front of
  them), which is exactly why the cookie cannot be left to infer it.

## What it costs

Nothing on its own: there is no injection to contain — no `Html`, no `innerHTML`, and
the one `executeJs` binds its argument — and the session cookie is only exposed on a
deployment that terminates TLS somewhere else, which is the deployment the README
describes. It is the layer that catches the mistake that has not been made yet.

## What fixing it looks like

`server.servlet.session.cookie.secure=true` and `same-site=lax` in
`application.properties`, the second because sign-in returns through a redirect from
the provider and `strict` would drop the session on the way back. Both want the same
treatment as `FORWARD_HEADERS_STRATEGY`: a default that is right for the container
and overridable for plain-HTTP local runs.

The CSP is the larger half, because Vaadin bootstraps through inline script and a
strict policy needs its nonce integration to be worth having. A first policy that
costs nothing and can go in now: `frame-ancestors 'none'`, `object-src 'none'`,
`base-uri 'self'`. `LoginSecurityConfigTest` and `AnonymousSecurityConfigTest` curl real
routes over a real container, so they are where any of this gets pinned — both of
them, since one configuration serves two chains and a test on a single mode would not
notice the other losing its headers. Neither asserts a header today. What is curled
has to be a route, never only a stylesheet, for the reason the post-processor exists.
