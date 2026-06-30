# Buy vs Rent: Inflation Rate input has no visible effect

## Summary

The Buy vs Rent calculator has an **Inflation Rate** input, but changing it
produces no observable change in any output. The inflation-deflated net-worth
difference (`realNetDifference`) is computed and stored on every `BuyRentYear`,
yet nothing in the UI renders it — no chart series, no grid column, no summary
card.

## Where

- `src/main/java/io/binarycodes/calculators/buyrent/service/BuyRentCalculator.java`
  computes `realNetDiff` (and `BuyRentYear.realNetDifference`) but it is never read by a view.
- `src/main/java/io/binarycodes/calculators/buyrent/domain/BuyRentYear.java`
  carries `realNetDifference` (and `cumulativeBuyCost` / `cumulativeRentPaid`,
  which are likewise computed but not displayed).
- The input itself lives in the Buy vs Rent form; `REQUIREMENTS.md` (§"Buy vs
  Rent" inputs table) describes it as "express the net-worth difference in
  today's money."

## Why postponed

Discussed on 2026-06-30: surfacing it is a UI/product decision (a "Difference
(today's money)" grid column, or a real-vs-nominal chart toggle) rather than a
pure bug fix, and the headline comparison is already correct in nominal terms.
Deferred to avoid scope-creeping the chart/math correctness pass.

## When to revisit

When adding a real-vs-nominal view to Buy vs Rent (mirroring the Investment
calculator's real-value column), or when trimming dead inputs. At that point
either:

1. Surface `realNetDifference` (grid column or chart toggle) so the Inflation
   Rate input drives a visible output; or
2. Remove the Inflation Rate input plus the unused `realNetDifference` /
   `cumulativeBuyCost` / `cumulativeRentPaid` fields, and update REQUIREMENTS.
