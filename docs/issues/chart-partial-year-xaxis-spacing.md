# Value charts space a partial final year a full tick apart

## Summary

The Investment and Goal value charts plot one point per projection row against
the row's calendar year on a numeric/linear x-axis. When the horizon is not a
whole number of years, the calculator emits a final partial-year row labelled
`currentYear + wholeYears + 1` — a full year past its predecessor — even though
it covers fewer than 12 months. The point therefore sits a full year-tick to the
right, implying the last segment spans a whole year when it is shorter. The bar
heights / values are correct; only the horizontal spacing is misleading.

## Where

- `src/main/java/io/binarycodes/calculators/investment/ui/InvestmentGrowthChart.java`
  — `DataSeriesItem(row.year(), …)` at lines ~50–51 on a linear x-axis.
- `src/main/java/io/binarycodes/calculators/goal/ui/GoalGrowthChart.java`
  — `renderYearly` at lines ~80–81 (`DataSeriesItem(row.year(), …)`).
- `src/main/java/io/binarycodes/calculators/goal/ui/GoalPerInvestmentChart.java`
  — `renderYearly` at line ~80 (same pattern).
- The partial final-row year originates in each calculator's projection builder
  (`year = currentYear + wholeYears + 1` for the leftover months).

## Why postponed

Cosmetic only — the plotted values are correct and the discrepancy appears just
for the single partial final year, and only when the horizon isn't whole years.
Deferred from the chart/math correctness pass to avoid scope-creep into axis
rendering.

## When to revisit

When polishing chart presentation. Options: plot the partial row at its true
fractional x (e.g. `wholeYears + months/12`) on the linear axis, or switch the
yearly views to a category axis with an explicit "+Nm" label on the final
category so the spacing reads as a partial period. The monthly views already use
category axes and are unaffected.
