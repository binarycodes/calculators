# Coding Conventions

Project-wide rules. Once a pattern is established here, follow it without prompting.

## 1. Naming

- Full, meaningful identifiers everywhere (variables, params, lambda captures, locals). No abbreviations beyond well-known ones (`irr` ok, `infl` not).
- Methods named for what they do, not how.
- Lambda parameters get real names; a genuinely unused one is `ignored` / `unused`.
- Constants replace magic numbers.

## 2. Comments

- Default to none — names, structure, and types carry intent.
- Comment only the non-obvious, and only the *why*.
- No section-divider banners. Class-level Javadoc for one-paragraph orientation is fine.
- Delete stale narrative and TODOs.

## 3. Code structure

- One concern per class. Extract the form, each chart (its own class), and the grid; the view stays thin — composition, state transitions, and the one method that ties them together.
- Components extend the closest Vaadin primitive (`Card`, `Grid<T>`, `Chart`, `VerticalLayout`) and expose a focused API (`setX` / `update` / `getInputs` / `addXChangeListener`).
- Helpers live with the thing they help.
- Result/projection grids extend `ColumnChooserGrid<T>`, not raw `Grid`. Register each column with `track(header, addColumn(...))` in render order; the view places `createColumnChooser()` in the grid-card header (`H2` + menu in a `HorizontalLayout` with `JustifyContentMode.BETWEEN`).
- Prefer a named private method over a long inline block.

## 4. Java

- Records for immutable value types; mutable Lombok beans for `Binder` inputs, with null-defaulted fields.
- Lombok `@Getter @Setter @NoArgsConstructor @AllArgsConstructor`, declared `<optional>true</optional>`.
- Jackson 3 — import from `tools.jackson.*`; build mappers via `JsonMapper.builder().build()`.
- Don't fabricate fallback defaults in form-read paths; surface empties through binder validation.
- `BigDecimal` for money — `MathContext.DECIMAL64` for arithmetic, `RoundingMode.HALF_UP` for display.
- `var` when the right-hand side makes the type obvious.
- Multi-line strings use text blocks with `.formatted(...)`, never `+`.

## 5. Vaadin / form binding

- Bind every field through `Binder<T>` with explicit `bind(getter, setter)`.
- Validators where they belong: `asRequired`, the range validators, `withValidator` for cross-field rules (re-trigger via the dependent field's value-change listener).
- `ValueChangeMode.LAZY` on number/text inputs. `CustomField` wrappers propagate inner-field changes with `updateValue()`.
- `binder.writeBeanAsDraft(target)` for possibly-invalid reads; pair with `isValid()` / `validate()`.
- Use semantic components: `RadioButtonGroup`, `Badge`, `Card` with the `status` attribute.
- Composite widgets extend `CustomField<T>` (single value) or a `Card` subclass.

## 6. CSS

- One file per concern; the entry stylesheet holds only `@import` lines.
- Colors are semantic tokens in `colors.css`, referenced via `var(--color-…)`. No raw hex / rgb / `color-mix` / named colors elsewhere.
- Dark-mode overrides sit next to their tokens in `colors.css` (`html.dark { … }`).
- Prefer Vaadin / theme variables over hard-coded values.
- One-paragraph header comment per file; no section dividers.

## 7. Internationalization

- No user-facing string literals in code. Every label, title, hint, placeholder, tooltip, validation message, grid header, chart title/axis/series, notification, and aria-label is a key in `src/main/resources/vaadin-i18n/translations.properties`.
- Resolve via `getTranslation(key, args…)` on a `Component`, or `Translations.get(key, args…)` in a static helper.
- Reuse and parameterise shared keys; namespace calculator-specific ones (`summary.loan.*`, `chart.retirement.*`).
- Annotations take constants — translate titles via `HasDynamicTitle` and `MenuTitles`.
- Never `switch` on a translated string; switch on an enum or index.

## 8. Responsive layout

- Every form and result is usable from ~375px to wide desktop with no horizontal overflow.
- Field grids use `FormLayout` with responsive steps, collapsing to one column.
- Repeating rows add the `form-row` class; summary-card rows add `summary-row`.
- Containers fill width (`setWidthFull()`); no fixed pixel widths.
- Verify at mobile and desktop widths before declaring done.

## 9. Project layout

- Feature-based packaging: `base/` for shared infrastructure, feature packages with their own `domain/` / `service/` / `ui/`.
- One public class per file; helpers stay private static unless reused.

## 10. Persistence

- `@VaadinSessionScope` beans for per-session state, mirrored to browser localStorage via `WebStorage`. Defaults stay read-only on the classpath; persisted state is separate.

## 11. Build & CI

- Run build / test / frontend tasks through `./run.sh <task>` (`compile`, `bundle`, `styles`, `test`, `run`, `package`, `clean`), which pins JDK 21. After a `@CssImport(themeFor=…)` / `@JsModule` change run `./run.sh bundle`; after editing an `@import`-ed CSS partial run `./run.sh styles`.
- CI runs `mvn verify` on push (Temurin JDK 21).
- Tests live alongside the package they cover.
- Conventional Commits: `<type>[(scope)][!]: <description>`, type one of `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`. Subject ≤100 chars, single line, no body, no `Co-Authored-By`. A `commit-msg` hook in `.githooks/` enforces this; enable per clone with `git config core.hooksPath .githooks`.

## 12. Working principles

- Verify each change visually or by test before declaring done.
- Preserve look-and-feel during refactors; a pixel change is a separate task.
- Refactor in small focused steps — extract, rename, run tests, then move on.
