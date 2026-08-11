# No security response headers or session-cookie flags

## Summary

The app sends no security-relevant response headers and hardens no cookie. There
is no `Content-Security-Policy` (or `frame-ancestors`), no `X-Frame-Options`, no
`X-Content-Type-Options: nosniff`, and no `Referrer-Policy`; the servlet
`JSESSIONID` that Vaadin creates uses container defaults, with no
`http-only` / `secure` / `same-site` configured.

Practical consequences today: the calculators can be framed by any third-party
site (clickjacking, or a look-alike wrapper around a financial tool), and
`Referrer-Policy` defaults mean a `?s=` share URL — which encodes the user's
financial figures — can leak in the `Referer` header of outbound clicks to the
footer links.

## Where

- `src/main/resources/application.properties` — no
  `server.servlet.session.cookie.*` entries.
- No servlet `Filter` or `VaadinServiceInitListener` sets response headers; the
  only listener is `base/i18n/AppLocaleConfig.java`, which pins the locale.
- Spring Security is not on the classpath, so none of its defaults apply.

## Why postponed

Impact is bounded by the app having nothing to steal server-side: no accounts,
no session-bound privileges, no CSRF-able state changes (Vaadin's UIDL token
already covers the RPC channel), and all user data living in the visitor's own
`localStorage`. A stolen `JSESSIONID` grants an attacker a blank calculator.

The one item with real bite — a strict CSP — is also the riskiest to add
blindly: Vaadin Flow emits inline styles and bootstraps its client engine in
ways that a naive `default-src 'self'` breaks, sometimes only in production mode
where the bundle differs from dev. That needs a deliberate pass with the
production build, not a one-line property.

## When to revisit

- Before any change that introduces authentication, accounts, or server-side
  persistence — at that point cookie flags and CSRF/session hardening stop being
  optional.
- Sooner, and cheaply, if the framing or referrer leak matters: `frame-ancestors`
  / `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff` and
  `Referrer-Policy: strict-origin-when-cross-origin` can go in a small
  `VaadinServiceInitListener` with no CSP risk. Add `secure` and `same-site` to
  the session cookie at the same time — deployments are already expected to sit
  behind a TLS proxy (see `README.md`).
- Treat a full `Content-Security-Policy` as a separate task, verified against a
  production build (`./run.sh package`), not a dev-mode run.
