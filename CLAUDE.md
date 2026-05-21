# Project instructions for Claude

## Coding conventions

**Before writing or editing any code in this repository, read
[`CODING_CONVENTIONS.md`](./CODING_CONVENTIONS.md) and follow every rule in
it.** The conventions cover naming, comments, code structure, Java idioms,
Vaadin form bindings, CSS organisation, persistence patterns, and CI.

In particular, recurring expectations:

- Identifiers use full, meaningful words — no single-letter or abbreviated
  names anywhere (variables, parameters, lambda captures, loop indices).
- Add a comment **only** when the code is not self-explanatory; explain
  *why*, never *what*. No section-divider comments.
- Keep view / orchestrator classes thin. Extract forms, charts, grids, and
  other distinct concerns into their own component classes with a focused
  public API.
- Colors live in `colors.css` as semantic tokens. Component CSS files
  reference them through `var(--color-…)`; never inline raw hex / rgb /
  named colors.
- Form bindings go through `Binder<T>` with `bind(getter, setter)` and
  appropriate validators. Use `ValueChangeMode.LAZY` for live recalculation.
- Mutable beans use Lombok (`@Getter @Setter @NoArgsConstructor
  @AllArgsConstructor`). Spring Boot 4 / Jackson 3 imports come from
  `tools.jackson.*`.

If a request would violate a convention, push back and propose the
convention-compliant alternative before proceeding.

## When in doubt

Re-read `CODING_CONVENTIONS.md` first. If a situation isn't covered, match
the style of the surrounding code and propose adding the new rule to the
conventions file.
