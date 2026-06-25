# Plan: Optional "Saved Calculations" — server persistence + OIDC login

**Status:** Proposed — parked for future consideration (not started)
**Raised:** 2026-06-26

## Context

Today the app stores nothing server-side: inputs/preferences live in browser
`localStorage`, and sharing is a self-contained `?s=` token. The README makes
this a headline privacy promise. For **self-hosters who own their deployment**,
that promise can be preserved while still offering a real convenience: sign in,
**save** a calculation, and browse a **Saved** corner later.

This feature is **opt-in and OFF by default**. When off, the app is byte-for-byte
today's behaviour — no DB, no login, no Save UI, anonymous everything. When a
self-hoster turns it on (one env var), they run a Postgres sidecar, configure an
OIDC provider, and users can sign in to save/browse their own scenarios.

### Decisions (locked with the user)
- **DB:** PostgreSQL with a `jsonb` column (separate container is acceptable).
  Chosen for first-class, queryable JSON to support future cross-scenario
  analysis. FOSS. (MongoDB excluded: SSPL, not FOSS.)
- **Auth:** generic, configurable **OIDC** via Spring Security; identity is the
  OIDC `sub` (+ issuer). **No user management / no user table / no passwords.**
- **Access model:** calculators stay fully anonymous; **only Save and the Saved
  view require login**. Saved scenarios are **per-user** (filtered by `sub`).
- **Restore reuses the share pipeline:** a saved scenario is `(calculator,
  currency, inputs JSON, name)`; opening it navigates to
  `/{routeSegment}?s={token}` and the existing `applyShareTokenIfPresent()` does
  the rest. Comparison of 2+ scenarios can be done in-app (deserialize via
  `InputsStore.fromJsonNode`) now; `jsonb` leaves the door open to DB-side
  analytics later.

## Reused building blocks (no change to their behaviour)
- `base/common/ScenarioCodec.java` — `encode(currency, inputs)` / `decode` /
  `Decoded(currency, inputs)`. A scenario == `(SupportedCurrency, JsonNode)`.
- `base/common/InputsStore.java` — `toJsonNode` / `fromJsonNode` per calculator.
- `base/ui/BaseCalculatorView.java` — `applyShareTokenIfPresent()` restore path;
  `routeSegment` per view (`retirement`, `goal`, `inflation`, `investment`,
  `loan`, `buyrent`).
- `base/ui/MainLayout.java` (SideNav from `@Menu`), `LandingView` tiles,
  `base/i18n/Translations` + `vaadin-i18n/translations.properties` (all new
  strings go here).

---

## Architecture

New feature package `io/binarycodes/calculators/persistence/`:
```
persistence/
├── config/
│   ├── PersistenceProfileActivator.java   # EnvironmentPostProcessor: flag → 'persistence' profile
│   ├── PersistenceProperties.java         # @ConfigurationProperties(app.persistence)
│   └── SecurityConfig.java                # @Profile("persistence") VaadinWebSecurity + OIDC
├── domain/
│   └── SavedScenario.java                 # JPA @Entity (jsonb inputs)
├── service/
│   ├── SavedScenarioRepository.java       # Spring Data JPA
│   ├── SavedScenarioService.java          # @Service: save/list/delete, owner-scoped
│   └── CurrentUser.java                   # reads OIDC principal (sub, issuer, name)
└── ui/
    ├── SaveScenarioButton.java            # "Save" control beside Share
    ├── SavedView.java                     # @Route("saved") list/open/delete/share
    └── LoginControls.java                 # login button / avatar+logout in MainLayout
```

### Gating (the one subtle bit)
The whole stack must not autoconfigure when off. Mechanism:
- `application.properties` (default): exclude DataSource / JPA / OAuth2-client /
  Spring-Security servlet autoconfigurations via `spring.autoconfigure.exclude`
  so a no-DB, no-auth startup is unchanged.
- `application-persistence.properties`: set `spring.autoconfigure.exclude=`
  (empty — profile properties override, re-enabling them) plus the datasource,
  JPA, and OIDC config.
- `PersistenceProfileActivator` (an `EnvironmentPostProcessor`, registered in
  `META-INF/spring.factories`): if `app.persistence.enabled=true`, add the
  `persistence` profile. So the single switch is `PERSISTENCE_ENABLED=true`.
- All persistence beans/UI wiring are `@Profile("persistence")` /
  `@ConditionalOnProperty(app.persistence.enabled)`.
- *Implementation note:* verify the exact Boot 4 autoconfig class names at code
  time; this is the highest-risk detail and gets a dedicated startup test (app
  boots with the flag off **and** on).

### Security (Vaadin + Spring Security)
- `SecurityConfig extends VaadinWebSecurity`, `@Profile("persistence")`,
  configures OIDC login (`oauth2Login`) and logout.
- Enabling Vaadin security flips the default to *deny*, so every public view must
  declare access. Add `@AnonymousAllowed` to all six calculator views + landing
  (harmless when the feature is off); `SavedView` gets `@PermitAll` (any
  authenticated user).
- OIDC config is standard `spring.security.oauth2.client.registration.oidc.*` +
  `provider.oidc.*` — self-hoster supplies issuer-uri, client-id, client-secret.

### Data model
`saved_scenario`:
| column | type | notes |
|---|---|---|
| id | uuid (pk) | |
| owner_subject | text | OIDC `sub` |
| owner_issuer | text | OIDC issuer (sub is only unique per issuer) |
| calculator | text | route segment |
| name | text | user-given |
| currency | text | `SupportedCurrency.name()` |
| inputs | jsonb | the `InputsStore.toJsonNode` blob |
| created_at | timestamptz | |

Index on `(owner_subject, owner_issuer, created_at desc)`. Entity maps `inputs`
with Hibernate `@JdbcTypeCode(SqlTypes.JSON)`. Schema created by a single Flyway
migration under the `persistence` profile (or `ddl-auto` as a lighter fallback).
No user table — owner identity is carried on the row; display name comes from the
live OIDC principal.

### Save / restore flow
- **Save:** `SaveScenarioButton` (beside Share, visible only when persistence on
  **and** authenticated) → name dialog → `SavedScenarioService.save(currentUser,
  calculator, name, currency, inputsJson)`.
- **Restore (open):** `SavedView` builds a token with
  `ScenarioCodec.encode(currency, inputs)` and calls `UI.navigate(calculator +
  "?s=" + token)` — reusing `applyShareTokenIfPresent()` end to end. Also offers
  Delete (owner-scoped) and Share (copy the same link).
- **Saved nav/tile:** shown only when persistence enabled; `MainLayout` and
  `LandingView` filter the `saved` entry out when off; `SavedView` is also
  `@PermitAll`-guarded.

---

## Files to create / change

**Create:** the `persistence/**` package above; `application-persistence.properties`;
`META-INF/spring.factories` (EnvironmentPostProcessor); one Flyway migration
`db/migration/V1__saved_scenario.sql`; new keys in `translations.properties`
(`menu.saved`, `landing.blurb.saved`, `save.*`, `saved.*`, `auth.*`).

**Change (small, additive):**
- `pom.xml` — add `spring-boot-starter-data-jpa`, `org.postgresql:postgresql`,
  `spring-boot-starter-oauth2-client`, `flyway-core` (+ `flyway-database-postgresql`).
  Hard deps but inert when the autoconfigs are excluded (feature off).
- `application.properties` — the default `spring.autoconfigure.exclude` line +
  `app.persistence.enabled=${PERSISTENCE_ENABLED:false}` and DB/OIDC env
  placeholders (unused when off).
- All 6 calculator views + `LandingView` — add `@AnonymousAllowed`.
- `BaseCalculatorView` — mount `SaveScenarioButton` in the header next to Share
  (renders only when enabled+authenticated).
- `MainLayout` — `LoginControls` (login/logout) + hide `saved` entry when off.
- Docker: README **privacy section rewrite** (off by default, opt-in, self-hoster
  owns data) + compose/Quadlet examples adding a `postgres` service, a volume,
  and the `PERSISTENCE_ENABLED` / OIDC / DB env; `REQUIREMENTS.md` (note the
  optional server path); `CODING_CONVENTIONS.md` §8.

---

## Phased delivery (each phase compiles, tests pass, its own commit)
1. **Gating scaffold** — deps (inert), `PersistenceProperties`,
   `PersistenceProfileActivator`, autoconfig-exclude wiring. Startup tests: app
   boots with flag **off** (no DB) and **on** (against a test Postgres). No user-
   visible change yet.
2. **Persistence module** — entity, repository, `SavedScenarioService`,
   `CurrentUser`, Flyway migration. Service unit tests (owner-scoping, mapping).
3. **OIDC security** — `SecurityConfig`, `@AnonymousAllowed` on public views,
   `LoginControls`. Verify anonymous calculators + forced login on `/saved`.
4. **Save + Saved UX** — `SaveScenarioButton`, `SavedView` (list/open/delete/
   share), nav/tile gating, i18n keys.
5. **Docs & deploy** — README privacy rewrite + compose/Quadlet/env, REQUIREMENTS,
   CODING_CONVENTIONS, a `docs/` note.
6. **Verify end-to-end** (below).

---

## Verification
- **Flag off (default):** app starts with **no** datasource/JPA/security beans;
  no Save button, no Saved nav/tile; all calculators + share work exactly as now.
  (Automated startup test + preview check.)
- **Flag on:** bring up app + Postgres (Testcontainers for tests; compose for
  manual). Anonymous user can use every calculator but `/saved` redirects to OIDC
  login. After login: Save a scenario → row in `saved_scenario` with `jsonb`
  inputs and the OIDC `sub`; Saved view lists only that user's scenarios; Open
  restores via `?s=` (currency + inputs correct, charts recompute); Delete is
  owner-scoped; a second user sees only their own.
- **Coverage:** `persistence/service` falls under the existing `*/service` 80%
  gate — covered by phase-2/3 tests.
- **Security:** confirm a logged-in user cannot open/delete another `sub`'s
  scenario (service filters by owner; no IDOR via the id).
- Per project conventions: build with JDK 21; `commit-msg` hook; commit per phase.

## Risks / call-outs
- **Boot 4 autoconfig exclusion names** — the riskiest detail; pin with the
  off/on startup tests.
- **Self-host complexity rises** (app + Postgres + OIDC app registration) — fully
  documented and entirely opt-in; the default one-container story is untouched.
- **jsonb now, analytics later** — store inputs as `jsonb` (not the opaque token)
  so future cross-scenario comparison can be done in-DB or in-app.
- **OIDC, not user management** — no passwords or user table; identity is the
  provider's `sub` + issuer.

## Open questions for when this is revived
- OIDC provider(s) the hosted instance will actually use (issuer URL).
- Flyway vs `ddl-auto` for schema management.
- Whether to add a thin `users` table later (only if we want to show richer
  profile info or pre-create allow-lists).
