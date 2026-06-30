# XIRR rejects a tangent (even-multiplicity) root as "no rate"

## Summary

`Xirr.roots` finds roots by scanning for **sign changes** of NPV and bisecting
each bracket. A root where the NPV curve only *touches* zero without crossing
(an even-multiplicity / tangent root) produces no sign change, so it is not
found. Such a schedule passes the `signChanges != 0` input gate (its cash-flow
amounts do change sign) yet yields zero NPV-roots, and `XirrCalculator` then
throws `irr.validation.noRate` — reporting "no rate" for a schedule that has a
genuine break-even IRR.

## Where

- `src/main/java/io/binarycodes/calculators/irr/service/Xirr.java`
  — `roots(...)` (from line ~58) detects sign changes only.
- `src/main/java/io/binarycodes/calculators/irr/service/XirrCalculator.java`
  — empty roots → `throw new IllegalArgumentException("irr.validation.noRate")`
    (line ~61).

## Reproduce

Cash flows `-1 @2020-01-01`, `+2 @2021-01-01`, `-1 @2022-01-01`. NPV(r) =
`-(v-1)^2` with `v = 1/(1+r)`, whose only root is a tangent at **r = 0%**. The
calculator reports "no rate in range" instead of 0%.

## Why postponed

Genuinely degenerate input — a real cash-flow schedule almost never lands exactly
on a tangent. Detecting touch-roots adds solver complexity (local-extremum
search with an epsilon) for a rare case. Deferred from the IRR correctness pass.

## When to revisit

When hardening the XIRR solver. After the sign-change scan, also flag local NPV
extrema where `|NPV| < ε` as touch-roots (reporting the rate, perhaps with a
"borderline" note), or at minimum return a clearer message than the generic
`noRate` for break-even-only schedules. See also
`irr-nonunique-root-beyond-scan-ceiling.md`.
