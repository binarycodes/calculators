# Calculators

A small suite of personal-finance calculators — a [Vaadin](https://vaadin.com)
(Spring Boot) web app. Pick a calculator, enter your numbers, and get an instant
year-by-year projection with charts and a shareable link.

**Calculators included:**

| Calculator | What it answers |
|---|---|
| Retirement Planner | Will my corpus last through retirement? Projects cashflow to life expectancy. |
| Goal Planner | How much must I invest monthly to hit a post-tax goal by a deadline? |
| Investment | What does a regular contribution grow to over an invest-and-hold horizon? |
| Loan / EMI | What's my EMI, and how do prepayments cut the tenure or interest? |
| Inflation Projection | What will an amount be worth across a horizon — forward or backward? |
| Buy vs Rent | Is buying a home better than renting and investing the difference? |

**Your data stays in your browser.** Inputs and preferences are saved to the
browser's `localStorage` — there's no database and nothing is sent to a server
to be stored. A "Share" link encodes the scenario into the URL itself, so
sharing is opt-in and self-contained.

Supports three currencies (₹ INR, € EUR, $ USD) and a light/dark theme.

---

## Run it (self-hosting)

A prebuilt, multi-architecture image (`linux/amd64` + `linux/arm64`) is published
to Docker Hub at **[`binarycodes/calculators`](https://hub.docker.com/r/binarycodes/calculators)**.
No build, no license key, no configuration required.

```bash
docker run --rm -p 8080:8080 binarycodes/calculators:latest
```

Then open <http://localhost:8080>.

- Use `binarycodes/calculators:latest` for the newest build, or pin a version
  tag — images are also tagged with the project's Maven version.
- The app listens on port **8080**. Override it with the `PORT` environment
  variable: `-e PORT=9090 -p 9090:9090`.

### docker compose

```yaml
services:
  calculators:
    image: binarycodes/calculators:latest
    ports:
      - "8080:8080"
    restart: unless-stopped
```

### Serve over HTTPS (recommended)

Run the app behind a TLS-terminating reverse proxy (Caddy, Traefik, nginx, your
cloud load balancer, …). Beyond the usual security reasons, **two features only
work in a secure context (HTTPS, or `localhost`)**:

- the **Share** button's native share sheet (browser Web Share API), and
- copying the share link to the clipboard.

Over plain HTTP on a LAN address (e.g. `http://192.168.1.10:8080`) the browser
hides these APIs: the calculators work fully, but the Share button falls back to
"Copy link" and, on iOS, the clipboard write is suppressed by the browser. Put
the app behind HTTPS and the native share sheet appears automatically — no app
configuration needed.

A minimal Caddy reverse proxy (automatic HTTPS) looks like:

```
calc.example.com {
    reverse_proxy calculators:8080
}
```

---

## Develop locally

Requirements: **JDK 21** and Maven (a `./mvnw` wrapper is included).

```bash
./mvnw spring-boot:run
```

Open <http://localhost:8080>. `localhost` counts as a secure context, so the
Web Share / clipboard features work here too. The frontend is rebuilt on the
fly in development mode; the first start downloads npm dependencies and takes a
little longer.

> The charts use Vaadin Charts (commercial). Without a Vaadin subscription the
> app runs fine but the charts show a trial banner — see
> [Vaadin license](#build-your-own-image) below.

Run the tests:

```bash
./mvnw test
```

---

## Build your own image

Production build (fat jar in `target/`):

```bash
./mvnw clean package
```

Build a container — the repo ships a multi-stage `Dockerfile` and a
`docker-bake.hcl` for multi-arch builds:

```bash
# single-arch, local
docker build -t calculators:latest \
  --build-arg APP_NAME=calculators \
  --build-arg APP_VERSION=1.0.0-SNAPSHOT .

# multi-arch (amd64 + arm64) via Buildx Bake
docker buildx bake
```

> **Vaadin license:** this app uses **Vaadin Charts**, a commercial Vaadin
> component, so a valid [Vaadin subscription](https://vaadin.com/pricing) is
> required to build it. Provide your license to the build as the
> `VAADIN_SERVER_LICENSE` build arg (wired through to `-Dvaadin.offlineKey`), or
> mount your `proKey` as a Docker build secret. Without a license the app still
> builds and runs, but the charts render with a **trial / commercial-component
> banner**.
>
> The prebuilt Docker Hub image is built with a license, so **running it needs no
> key and shows no banner** — the license is checked at build time, not at
> runtime.

---

## How it's published

`binarycodes/calculators` on Docker Hub is built and pushed automatically by the
GitHub Actions **CI** workflow: on every push to `main`, the Docker image is
built and published **only after `mvn verify` passes** (the `docker` job is
gated on the `verify` job). Images are signed with [cosign](https://github.com/sigstore/cosign)
and ship with provenance + SBOM attestations.
