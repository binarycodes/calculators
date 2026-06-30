# XIRR can mislabel a non-unique schedule when a second root exceeds the scan ceiling

## Summary

`XirrCalculator` derives determinacy from the number of roots found within the
scan range `[−99.9999%, +10000%]`: `status = roots.size() > 1 ? NON_UNIQUE :
UNIQUE`. If a schedule genuinely has a second real root **beyond +10000%**, only
the in-range root is found, so the result is labelled `UNIQUE` even though the
schedule is mathematically non-unique. The warning banner separately quotes the
Descartes sign-change count *and* the found-root count, so in this case those two
numbers can disagree.

## Where

- `src/main/java/io/binarycodes/calculators/irr/service/XirrCalculator.java`
  — `status` from `roots.size()` (line ~64); `signChanges` carried into the
    result (line ~77); scan ceiling `SCAN_HIGH` (= +10000%).
- `src/main/java/io/binarycodes/calculators/irr/ui/XirrView.java`
  — `irr.warning.multipleRoots` interpolates both `result.signChanges()` and
    `result.roots().size()` (lines ~115–116).

## Reproduce

Cash flows `-1 @2020-01-01`, `+202.1 @2021-01-01`, `-221.1 @2022-01-01` have
roots near 10% and ~20000%. Only the 10% root is within range, so the schedule is
labelled `UNIQUE` despite having two sign changes.

## Why postponed

Requires a second root above +10000% (a >100× annualised return), which is
effectively impossible for real-world cash flows. Deferred from the IRR
correctness pass as a theoretical edge case.

## When to revisit

When hardening the XIRR solver. Either raise / make `SCAN_HIGH` adaptive, or note
in the result when the root count is range-limited (so determinacy and the banner
wording can't quietly disagree). Keep the banner from implying a root count it
can't display. See also `irr-tangent-root-not-detected.md`.
