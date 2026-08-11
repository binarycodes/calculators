# Spring Security's default headers don't reach the Vaadin HTML document

## Summary

Spring Security is configured (`SecurityConfig`, no authentication), and its
default response headers apply to ordinary static requests — but **not** to the
Vaadin-served HTML document:

```
GET /            → X-Frame-Options: DENY          (set by Vaadin, not Spring Security)
GET /colors.css  → X-Frame-Options: DENY, X-Content-Type-Options: nosniff, X-XSS-Protection: 0
```

Framing is now handled: `vaadin.frameOptions=DENY` in `application.properties`
sets it on the application page. What remains is that **`nosniff` is still absent
from the document**, and more generally that any future header (a
`Referrer-Policy`, a CSP directive) cannot be added through Spring Security's
`headers(...)` configuration, because that configuration demonstrably does not
run on this path.

The cause is now known, and it is not what it first looked like. Vaadin's docs
are explicit that Vaadin *defers* to an existing header: "Vaadin only sets the
header if the response doesn't already contain an `X-Frame-Options` header. A
header set by other means — for example, by Spring Security or a servlet filter —
isn't overwritten." Since the document previously carried Vaadin's `SAMEORIGIN`
rather than Spring Security's `DENY`, Spring Security's header writer never ran
on that request at all. Vaadin was not overwriting anything.

So the open question is narrower: what excludes the Vaadin request path from
Spring Security's `HeaderWriterFilter` — most likely `VaadinSecurityConfigurer`
treating framework requests as ignored rather than permitted — and whether that
is intended to be configurable.

## Where

- `src/main/resources/application.properties` — `vaadin.frameOptions=DENY`
  handles framing; nothing sets `nosniff` on the document.
- `src/main/java/io/binarycodes/calculators/base/config/SecurityConfig.java` —
  applies `VaadinSecurityConfigurer.vaadin()` with no explicit `headers(...)`
  configuration.
- Reproduce with `curl -sS -D - -o /dev/null http://localhost:8080/` and the same
  against `/colors.css`. Always check the document, not a static resource — the
  two paths demonstrably behave differently.

## Why postponed

The remaining gap — `nosniff` on the document — protects against MIME confusion
on attacker-controlled content, and the app serves only its own correctly-typed
files. Nothing exploitable follows from its absence here.

The framing half was worth doing immediately because it turned out to be a
one-line property. The rest is not: it requires understanding Spring Security's
filter chain as `VaadinSecurityConfigurer` assembles it, which is a real
investigation rather than a config tweak.

## When to revisit

- Whenever a *second* header becomes wanted on the document — `Referrer-Policy`
  most likely. One missing header is not worth the investigation; two would be,
  and at that point the answer benefits everything added afterwards.
- Sooner if the app gains authentication or any state-changing action.
- Note that `vaadin.frameOptions` solved framing without touching Spring
  Security at all. Check whether Vaadin exposes similar properties for other
  headers before assuming the filter chain has to be the answer.

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
