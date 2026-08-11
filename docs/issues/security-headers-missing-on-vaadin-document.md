# Spring Security's default headers don't reach the Vaadin HTML document

## Summary

Spring Security is now configured (`SecurityConfig`, no authentication), and its
default response headers apply to ordinary static requests — but **not** to the
Vaadin-served HTML document. Measured against the running app:

```
GET /            → X-Frame-Options: SAMEORIGIN
GET /colors.css  → X-Frame-Options: DENY, X-Content-Type-Options: nosniff, X-XSS-Protection: 0
```

The stylesheet gets Spring Security's defaults. The document does not: it still
carries Vaadin's own `SAMEORIGIN` and has no `nosniff`. That inverts the useful
case — framing protection matters on the HTML document and is irrelevant on a
stylesheet.

The intended policy is `DENY` (decided: the calculators should not be framable by
third parties). Today the document is framable by any same-origin page and
`SAMEORIGIN` is what third-party browsers actually enforce.

The underlying question is *why* the header writer is bypassed on that path —
most likely `VaadinSecurityConfigurer` excluding Vaadin's own request handling
from parts of the chain, or Vaadin's bootstrap handler overwriting the header
after Spring Security wrote it. That needs establishing before choosing a fix;
guessing between those two leads to different solutions.

## Where

- `src/main/java/io/binarycodes/calculators/base/config/SecurityConfig.java` —
  applies `VaadinSecurityConfigurer.vaadin()` with no explicit `headers(...)`
  configuration, so Spring Security's defaults are in play unmodified.
- Reproduce with `curl -sS -D - -o /dev/null http://localhost:8080/` and the same
  against `/colors.css`.

## Why postponed

Nothing exploitable follows from it here. There is no authentication, so there
are no privileged actions for a clickjacked frame to trigger — the worst outcome
is someone embedding a public calculator under their own branding, which is a
presentation concern rather than a security one. `nosniff` protects against MIME
confusion on attacker-controlled content, and the app serves only its own
correctly-typed files.

It was also deliberately left out of the commit that introduced Spring Security,
to keep that change to "the controls are now available, nothing else changes."
Fixing the header at the same time would have mixed a behavioural change into a
plumbing one.

## When to revisit

- Alongside the next piece of security-header work, since the mechanism found
  here (whatever is bypassing the header writer) also determines how
  `Referrer-Policy` and any future CSP directives would have to be applied.
- Sooner if the app ever gains authentication or any state-changing action, at
  which point clickjacking stops being theoretical and `DENY` becomes load-bearing.
- The fix is expected to be small once the cause is known — either a `headers(...)`
  block in `SecurityConfig` or letting Vaadin's own configuration set the value —
  but confirm with `curl` on the document, not on a static resource, since those
  two paths demonstrably behave differently.

## Not part of this issue

Deliberately kept separate, since each needs its own decision:

- **Strict CSP** — declined outright, not deferred. Vaadin's documentation states
  Flow "isn't generally compatible with strict CSP rules": the client engine
  requires `script-src 'unsafe-inline' 'unsafe-eval'` and `style-src
  'unsafe-inline'` to bootstrap, described there as an architectural limitation.
  Nonce-based strict CSP is possible only by shimming `window.eval` to harvest
  eval calls into a build-time map, and the documented workaround carries a
  special case for Vaadin Charts, which this app uses.
- **`Referrer-Policy`** — not a Spring Security default, undecided. Note the
  original concern about `?s=` share tokens leaking via `Referer` does not hold
  up: browsers default to `strict-origin-when-cross-origin`, which strips the
  query cross-origin, and the app strips `?s=` from the address bar on load.
- **Session cookie `SameSite` and `Secure`** — undecided. `HttpOnly` is already
  set by Tomcat. `Secure` should not be hardcoded, since the app is self-hosted
  on plain HTTP behind an operator's proxy; `server.forward-headers-strategy` is
  the mechanism worth evaluating.
