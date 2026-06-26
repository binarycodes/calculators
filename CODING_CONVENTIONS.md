# Coding Conventions

These conventions apply across the whole codebase. They were established
incrementally while building this app — once a pattern is in place here, new
code should follow it without being prompted.

## 1. Naming

- **Use full, meaningful words for every identifier.** Variables, methods,
  parameters, lambda captures, and even single-line locals all deserve real
  names.
  - Not `r`, `c`, `in`, `f`, `s`, `e`, `v`, `nf`, `dl`, `cfg`.
  - Yes `result`, `currency`, `inputs`, `field`, `suffix`, `event`, `value`,
    `numberField`, `dataLabels`, `configuration`.
- **Methods are named for what they do, not how.** `populateFormFromPersistedOrDefault`
  rather than `applyForCurrency`; `buildSectionCard` rather than `section`.
- **Lambda parameters get real names** — `event`, `row`, `field`, `value`. If
  the parameter is genuinely unused (e.g. a change listener that doesn't read
  the event), name it `ignored` or `unused`.
- **Constants replace magic numbers.** `LOW_CORPUS_MULTIPLIER = BigDecimal.TEN`
  instead of `multiply(BigDecimal.TEN)` sprinkled across files.
- **No abbreviations beyond well-known ones.** `inputs` not `in`,
  `inflationPercentage` (or `inflationPct` if the context is unambiguous), not
  `infl`.

## 2. Comments

- **The default is zero comments.** Names, structure, and types should
  communicate intent on their own.
- Add a comment **only when the code is not self-explanatory** — typically
  to explain *why* something is done (a non-obvious workaround, an async
  ordering constraint, a license note, a framework quirk). Never to
  paraphrase *what* the next line does.
- **No section-divider comments.** Drop `// ---- foo ----` banners; if a
  class is large enough to need them, split it.
- **Class-level Javadoc is welcome** for one-paragraph orientation — what
  the class does, what its public API is, and any non-obvious composition
  details.
- Don't keep stale narrative ("now we …", "TODO from old version"); delete it.

## 3. Code structure

- **One concern per class.** When a view starts to own input fields, chart
  config, grid columns, and orchestration logic, extract:
  - The form into its own component class
  - Each chart into its own class (`CorpusChart`, `ExpensesChart`,
    `InvestmentsChart` — never one ChartView with three render methods)
  - The grid into its own class
  - Leaves the orchestrator (the "view") thin: composition + state
    transitions + the one non-trivial method that ties everything together.
- **Components extend the closest Vaadin primitive** that fits (`Card`,
  `Grid<T>`, `Chart`, `VerticalLayout`) and expose a focused public API —
  typically `setX(...)` / `update(...)` / `getInputs()` /
  `addXChangeListener(...)`.
- **Helpers belong with the thing they help.** Don't park column formatters
  or part-name generators inside the view if they only exist to support the
  grid — move them into the grid class.
- **Prefer extracting a private method over a multi-branch inline
  expression.** The view's `recalculate()` reads as a sequence of named
  steps (`updateRetirementYearSummaries`, `updateLastsUntilSummary`,
  `updateFinalCorpusSummary`) rather than one 40-line block.

## 4. Java

- **Records** for immutable value types (results, projection rows).
- **Mutable beans** for things that are written through a Vaadin `Binder`
  — null-defaulted fields so an empty form yields nulls rather than
  fabricated defaults.
- **Lombok** for boilerplate on data classes:
  `@Getter @Setter @NoArgsConstructor @AllArgsConstructor`. Add Lombok as
  an `<optional>true</optional>` dependency so it doesn't leak transitively.
- **Spring Boot 4 uses Jackson 3** — imports come from `tools.jackson.*`,
  not `com.fasterxml.jackson.*`. Instantiate an `ObjectMapper` via
  `JsonMapper.builder().build()`.
- **Don't fabricate fallback defaults inside form-read paths.** If a
  required field is empty, surface that through binder validation rather
  than silently substituting `35` or `BigDecimal.ZERO`.
- `BigDecimal` is the right type for money. Use `MathContext.DECIMAL64` for
  intermediate arithmetic, `RoundingMode.HALF_UP` for display rounding.
- Use `var` for locals when the right-hand side makes the type obvious.
- **Multi-line strings use Java text blocks (`""" … """`), never string
  concatenation with `+`.** Applies to anything that spans more than one
  line — HTML / JS / Lit / SQL templates, formatted error messages, etc.
  Combine with `.formatted(...)` for interpolation rather than `+`.

## 5. Vaadin / form binding

- **Bind every form field through `Binder<T>`** with explicit
  `bind(getter, setter)` calls — not the bean-name string overload.
- **Add validators where they logically belong:**
  - `asRequired("…")` for mandatory fields
  - `IntegerRangeValidator` / `DoubleRangeValidator` /
    `BigDecimalRangeValidator` for ranges
  - `withValidator(lambda, message)` for cross-field rules; re-trigger
    them on the dependent fields via
    `dependentField.addValueChangeListener(e -> binder.validate())`.
- **Live recalculation:** set `ValueChangeMode.LAZY` on number/text inputs
  so the binder's value-change fires after a typing pause (no need to
  blur). For `CustomField` wrappers, propagate inner-field value changes
  explicitly with `updateValue()` — `CustomField` only auto-syncs on the
  host `change` DOM event (i.e. on blur).
- **Read with `binder.writeBeanAsDraft(target)`** when the caller needs
  the current (possibly-invalid) bean — pair with a public `isValid()` /
  `validate()` so callers can decide what to do.
- **Use semantic Vaadin components** instead of hand-rolling.
  `RadioButtonGroup` (not a row of buttons) for mutually-exclusive
  choices. `Badge` for status pills. `Card` with `status` attribute for
  tinted info tiles. Get the accessibility, keyboard navigation, and
  theming for free.
- **Composite widgets**: when a component visually contains other Vaadin
  primitives, extend `CustomField<T>` if it has a single value, or
  `Composite<Card>` / a plain `Card` subclass otherwise.

## 6. CSS

- **One file per concern.** `colors.css`, `typography.css`, `layout.css`,
  `grid.css`, `summary-card.css`, `segmented-toggle.css`, etc.
- **The entry stylesheet only contains `@import` lines** — no
  declarations.
- **Colors live in `colors.css` as semantic CSS custom properties.**
  Tokens like `--color-primary`, `--color-danger-text`,
  `--color-surface-raised`. Component CSS files reference them via
  `var(--color-…)`; raw hex / rgb / `color-mix` / named colors do not
  appear outside `colors.css`.
- **Dark-mode overrides live next to the tokens they override** —
  typically in `colors.css` under `html.dark { … }`. No
  `html.dark .my-component { … }` blocks scattered through component
  files unless the component genuinely needs structural changes in dark
  mode.
- **Use Vaadin / theme CSS variables** (`--vaadin-padding-m`,
  `--vaadin-input-field-border-color`, `--aura-font-family`) over
  hard-coded values where possible.
- **Each CSS file gets a one-paragraph header comment** describing what
  it owns. No section dividers inside files.

## 6b. Internationalization

- **No user-facing string literals in code.** Every label, button, title,
  tab caption, hint, placeholder, tooltip, validation message, grid column
  header, chart title/axis/series name, notification, and aria-label is a
  key in `src/main/resources/vaadin-i18n/translations.properties`
  (Vaadin's `DefaultI18NProvider` loads this folder automatically — no
  custom provider). The suffix-less file is the en_GB / fallback bundle;
  the app pins the locale to en_GB (`base/i18n/AppLocaleConfig`) and does
  not yet detect or switch languages.
- **Resolve via `getTranslation(key, args…)`** on a `Component`, or
  `io.binarycodes.calculators.base.i18n.Translations.get(key, args…)` in a
  static helper / renderer lambda / factory.
- **Reuse shared keys and parameterise** rather than duplicating text:
  `validation.between=Must be between {0} and {1}`, `field.years`,
  `grid.col.principal`, `frequency.monthly`, `unit.percent`. Calculator-
  specific text is namespaced (`summary.loan.*`, `chart.retirement.*`).
- **Annotations can't be translated** — `@PageTitle`/`@Menu(title=…)` take
  constants. Use `HasDynamicTitle.getPageTitle()` for the page title
  (`BaseCalculatorView` does this from a title key), and translate menu
  labels where they render (`MenuTitles` maps `@Menu` paths to `menu.*`).
- **Never `switch` on a translated display string.** Switch on an enum or
  tab index instead, so logic survives a locale change.

## 7. Responsive layout

**Every form and result layout must be responsive** — usable from a phone
(~375px) up to a wide desktop, with no horizontal overflow. Concretely:

- **Field grids use `FormLayout` with responsive steps**, collapsing to a
  single column on narrow screens, e.g.
  `new FormLayout.ResponsiveStep("0", 1)`, `("36em", 2)`, `("64em", 3)`.
  Don't hand-place inputs in a fixed-width `HorizontalLayout`.
- **Repeating record rows** (one entry per row — expenses, incomes,
  investment buckets, …) add the shared **`form-row`** class. Its rule in
  `layout.css` makes the row `flex-wrap` and gives each field a
  `min-width`, so fields reflow onto multiple lines instead of overflowing.
- **Summary-card rows use the shared `summary-row` class**, which wraps the
  cards (4-across on desktop → 1-per-row on mobile).
- **Page containers fill the available width** (`setWidthFull()`); avoid
  fixed pixel widths on layouts. Let content and the grid/flex rules decide
  wrapping.
- **Verify at mobile and desktop widths** (the preview's `mobile`/`desktop`
  presets) before declaring a UI change done.

## 7. Project layout

- **Feature-based packaging** (per the Vaadin 25 primer):
  `base/` for shared infrastructure (currency, prefs, money-formatting,
  layout, MoneyField), feature packages alongside (`retirement/`) with
  their own `domain/`, `service/`, `ui/` sub-packages.
- **One public class per file.** Helpers stay private static inside the
  same file unless reused elsewhere.

## 8. Persistence

- **Vaadin session-scoped beans** (`@VaadinSessionScope`) for per-session
  state. **Mirror to browser localStorage via `WebStorage`** for
  cross-session persistence on the same browser. Defaults stay
  read-only (classpath resource), persisted state is separate.

## 9. Build & CI

- **Maven** with the project's `mvnw` wrapper checked in; build with
  Java 21.
- **CI runs `mvn verify` on push to any branch** via GitHub Actions —
  Temurin JDK 21, Maven dependency cache via `actions/setup-java`.
- **Tests live alongside the package they cover.** Golden-string /
  golden-number assertions for porting work.
- **Commit messages follow Conventional Commits:**
  `<type>[(scope)][!]: <description>`, with `type` one of `feat`, `fix`,
  `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`,
  `revert`. Keep the subject within 100 characters, write the message as a
  **single line with no body**, and do **not** add `Co-Authored-By` trailers.
- **A `commit-msg` hook enforces the above.** It lives in `.githooks/`
  (version-controlled). Enable it once per clone with:

  ```bash
  git config core.hooksPath .githooks
  ```

## 10. Working principles

- **Verify each change visually or by test** before declaring done —
  screenshot or run the relevant test class.
- **Preserve look-and-feel during refactors.** If a refactor changes
  pixel-output, that's a separate task; flag it and discuss before
  shipping.
- **Refactor in small focused steps.** Extract a class, rename, then run
  tests, then move on. Don't combine extraction + behaviour change in
  the same diff.
