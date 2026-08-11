# Dependabot covers only GitHub Actions, not Maven or npm

## Summary

`.github/dependabot.yml` declares a single ecosystem, `github-actions`. The
application's own dependencies get no automated update or vulnerability alerts:
Spring Boot, Vaadin, ICU4J and Lombok on the Maven side, and the whole
`package.json` tree (React, lit, ol/proj4, the Vaadin web components) on the npm
side. A published CVE in any of them would sit unnoticed until someone bumps a
version by hand.

## Where

- `.github/dependabot.yml` — one `- package-ecosystem: "github-actions"` entry.
- `pom.xml` — Spring Boot 4.0.6, Vaadin 25.2.5, icu4j 78.3, Playwright 1.59.0.
- `package.json` — the frontend tree pulled in by the Vaadin build.

## Why postponed

The app's runtime attack surface is unusually small — no authentication, no
database, no server-side user data, no REST endpoints, and the only untrusted
input is the `?s=` share token, which `ScenarioCodec` bounds and validates. That
makes a dependency CVE less likely to be reachable here than in a typical web
app, so this was deprioritised against the live secret-exposure fix in the same
review.

Adding the ecosystems is also not free: Vaadin's Maven and npm versions are
coupled through the platform BOM, so an unsupervised Dependabot bump of a single
`@vaadin/*` package can desynchronise the frontend from the server and break the
build. Doing it properly means grouping the Vaadin packages and probably pinning
the npm tree to the BOM.

## When to revisit

Whenever any of these becomes true:

- The app grows a genuinely reachable dependency surface — auth, a datastore,
  file upload, outbound HTTP, or any server-side handling of third-party input.
- A Spring Boot or Vaadin CVE lands that affects the pinned versions.
- The Vaadin version is next bumped by hand — that's the natural moment to add
  a `maven` ecosystem with the Vaadin artifacts grouped, and an `npm` ecosystem
  scoped to non-Vaadin packages only.
