# GoalGrowthChart monthly view double-maps the x-axis

## Summary

`GoalGrowthChart.renderMonthly` sets the x-axis categories *and* keys each
`DataSeriesItem` by the same label string. On a category axis only one mechanism
is needed; carrying both is belt-and-suspenders that renders fine today but can
drift if one list is changed without the other. The sibling
`GoalPerInvestmentChart.renderMonthly` uses the cleaner pattern (empty point
names with `setCategories`), so the two monthly renderers are inconsistent.

## Where

- `src/main/java/io/binarycodes/calculators/goal/ui/GoalGrowthChart.java`
  — `renderMonthly`: `xAxis.setCategories(labels)` (line ~94) plus
    `new DataSeriesItem(snapshot.label(), …)` (lines ~99–100).
- Contrast: `src/main/java/io/binarycodes/calculators/goal/ui/GoalPerInvestmentChart.java`
  — `renderMonthly` keys items by `""` and relies on `setCategories`.

## Why postponed

No visible defect — the chart renders correctly. Pure tidy-up to make the two
monthly renderers consistent. Deferred to avoid mixing cosmetic refactors into
the correctness pass.

## When to revisit

Next time either goal chart is touched. Align `GoalGrowthChart.renderMonthly`
with `GoalPerInvestmentChart` (categories carry the labels; series items use `""`
names), so there is one source of truth for the x positions.
