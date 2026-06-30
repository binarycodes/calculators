# Buy vs Rent computes cumulative cost/rent fields that are never displayed

## Summary

`BuyRentCalculator` accumulates `cumulativeBuyCost` (EMI + property tax +
maintenance to date) and `cumulativeRentPaid` (rent to date) and stores both on
every `BuyRentYear`, but neither is surfaced in any chart, grid column, or
summary card. They are dead output as far as the UI is concerned. (The separate
`realNetDifference` / Inflation-Rate gap is tracked in
`buyrent-inflation-input-not-surfaced.md`.)

Note: `cumulativeRentPaid` is now read by a unit test
(`BuyRentCalculatorTest.rent_steps_once_per_year…`) to assert annual rent
stepping, so it isn't entirely unreferenced — but it is still never shown to the
user.

## Where

- `src/main/java/io/binarycodes/calculators/buyrent/service/BuyRentCalculator.java`
  — `cumulativeBuyCost` / `cumulativeRentPaid` declared (lines ~79–80),
    accumulated (lines ~119, ~127), and passed into `BuyRentYear` (lines ~168–169).
- `src/main/java/io/binarycodes/calculators/buyrent/domain/BuyRentYear.java`
  — fields `cumulativeRentPaid`, `cumulativeBuyCost` (lines ~28–29).
- The REQUIREMENTS grid table does not list either column.

## Why postponed

No incorrect output — just unused computation. Whether to surface or drop is a
product decision; deferred alongside the related inflation-input gap.

## When to revisit

When deciding the Buy vs Rent grid/columns (e.g. adding a "total paid so far"
comparison). Either expose the two cumulative figures as grid columns / a chart
series, or remove the fields and their accumulation. If `cumulativeRentPaid` is
removed, adjust `BuyRentCalculatorTest` to assert annual rent stepping another
way.
