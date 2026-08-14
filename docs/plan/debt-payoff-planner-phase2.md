# Debt Payoff Planner — Phase 2 spec

**Status:** proposed (Phase 1 shipped)
**Builds on:** [`debt-payoff-planner.md`](./debt-payoff-planner.md)

## Context

Phase 1 shipped the multi-debt planner: fixed promo-aware avalanche/snowball vs a
minimums-only baseline, a constant monthly budget with same-month cascade, a
comparison chart, and a year-by-year grid. Phase 2 adds the three "pay more,
smarter" levers we deferred, each reusing the existing month-by-month engine and
the debts row-list UI.

## Scope

**In:**
1. **Extra-payment step-up** — the recurring extra grows a set % each year.
2. **One-off windfalls** — a lump extra payment in a specific month, on top of
   the recurring extra, funnelled in the current strategy order.
3. **Custom ordering** — a third strategy that pays debts in a user-arranged
   order, set by moving rows up/down.

**Won't do:**
- **Dynamic (interest-optimal) target re-sort** — re-picking the target each
  month. Dropped for good: the fixed promo-aware order is the model, and the
  extra month-to-month complexity isn't worth it.

**Out of scope:**
- Balance-transfer / consolidation optimisation (a possible later calculator).

## Resolved decisions
1. **Windfall targeting** — a windfall joins that month's surplus and funnels to
   the current target(s) in strategy order, cascading same-month. It is *not*
   assigned to a specific debt.
2. **Custom ordering UX** — up/down move buttons on each debt row; the `CUSTOM`
   strategy pays debts top-to-bottom in the on-screen order.
3. **Step-up scope** — the annual step-up applies to the **extra** only, never to
   the minimums baseline, so `interestSaved` / `monthsSaved` still measure the
   whole plan's advantage over paying minimums.
4. **Dynamic re-sort** — dropped for good (see Won't do); not part of the model.

## Inputs (domain)

Package `io.binarycodes.calculators.debt.domain`.

`DebtPlanInputs` gains:
```
BigDecimal extraStepUpPct;   // optional, annual % increase of extraPerMonth
List<Windfall> windfalls;    // optional, defaults to empty
```

New `Windfall` (mutable Lombok bean, mirrors `Debt`'s shape):
```
Integer    month;            // 1-based month from the plan start, ≥ 1
BigDecimal amount;           // > 0
```

`PayoffStrategy` gains `CUSTOM` (alongside `AVALANCHE`, `SNOWBALL`).

## Calculation model changes

All three fold into the existing `DebtCalculator.simulate` loop; the baseline run
is untouched (no extra, no windfalls, fixed minimum order), so savings stay
apples-to-apples.

- **Step-up.** The budget is no longer a scalar. Define
  `budget(month) = Σ initial minimums + extra × (1 + stepUp)^floor((month-1)/12)`.
  It only ever increases, so the month-1 feasibility guard (lowest budget) still
  binds — no new infeasible case. The rollover is unchanged: surplus =
  `budget(month) − minimums paid`.
- **Windfalls.** Pre-index windfalls by month. Each month, after computing the
  surplus, add that month's windfall total to the surplus pool *before* the
  cascade, so a windfall funnels to the current target and can clear more than
  one debt that month. Windfalls apply to strategy runs only (the baseline has no
  funnelling). A windfall dated after full payoff is a no-op. Windfalls do not
  affect the month-1 feasibility guard.
- **Custom order.** `rank(debts, CUSTOM)` returns the debts in input order (no
  sort) — the same list the UI reorders. Everything else (interest, minimums,
  cascade, floor, promo) is identical.

`DebtScheduleResult` is unchanged in shape. `DebtPlanResult` carries an explicit
`primary` schedule (the chosen strategy's run) in addition to `avalanche`,
`snowball`, and `baseline`:
- Chosen AVALANCHE/SNOWBALL → `primary` is that same run.
- Chosen CUSTOM → `primary` is a separately simulated custom run; `avalanche` and
  `snowball` are still computed as canonical references for the chart and the
  "vs" card.
- `interestSaved` / `monthsSaved` = `primary` vs `baseline` (unchanged).
- The "vs other strategy" delta: AVALANCHE↔SNOWBALL as today; for CUSTOM it
  compares against **Avalanche** (the interest-optimal canonical reference) —
  "vs Avalanche: ₹X more interest".

## Outputs (UI)

**Form** (`DebtCalculatorForm`):
- **Plan card** gains an **Extra step-up** (%) field beside Extra per month, and
  a **Custom** option on the strategy toggle. A hint under the toggle: "Custom
  pays debts top-to-bottom in the order above."
- **Debt rows** gain **up/down move buttons** (`RowControls` additions or a small
  reorder control). Moving a row swaps it in the section's list and re-adds the
  existing row instances to the container in the new order (state preserved),
  then republishes the debts signal. First-row up and last-row down are disabled.
- **Windfalls card** — a new optional row-list section like the debts list:
  each `WindfallRow` is Month (`IntegerField`) + Amount (`MoneyField`) +
  `RowControls.removeButton`; an "Add windfall" button; empty by default. Exposed
  as a `ValueSignal<List<Windfall>>` folded into the inputs signal.

**Comparison chart:** default lines stay Avalanche / Snowball / minimums-only.
When CUSTOM is selected, add a fourth **Custom** line (warning-colour palette
entry), so the user sees their order against the canonical ones.

**Projection grid:** unchanged — it renders the `primary` schedule, so it already
reflects step-up, windfalls, and custom order. Windfall months naturally show up
as larger principal + more targets in that year's row.

**Summary cards:** unchanged set; the debt-free / interest / saved figures now
reflect the new levers automatically.

## Persistence, defaults, sharing

- `DebtInputsStore.toJsonNode` / `fromJsonNode`: add `extraStepUpPct`, a
  `windfalls` array (`{month, amount}` objects, same helper pattern as `debts`),
  and the `strategy` enum already round-trips `CUSTOM`. Debt list order is already
  preserved, so custom order persists for free. Share links reuse this codec.
- `debt-defaults.json`: add a modest `extraStepUpPct` and one sample windfall to
  a currency or two to showcase the features (kept small).

## i18n keys (translations.properties)

`field.debt.extraStepUp`, `section.debt.windfalls`, `debt.windfalls.intro`,
`debt.addWindfall`, `field.debt.windfallMonth`, `field.debt.windfallAmount`,
`debt.strategy.custom`, `debt.customOrderHint`, `chart.debt.seriesCustom`,
`row.moveUp`, `row.moveDown` (aria-labels), and any new validation messages.

## Edge cases
- **Windfall month < 1 or amount ≤ 0** → binder validation, like a debt row.
- **Windfall after payoff** → ignored (no target); no error.
- **Multiple windfalls in the same month** → summed into that month's surplus.
- **Step-up with extra = 0** → no effect (0 × anything).
- **Reordering under Avalanche/Snowball** → no effect on results (those sort
  internally); it only drives the `CUSTOM` run. The hint makes this explicit.
- **Single debt** → all strategies (incl. custom) coincide, as in Phase 1.

## Tests
- `DebtCalculatorTest` (additions): **step-up shortens payoff** and step-up = 0
  reproduces the Phase-1 result; **a windfall accelerates payoff / cuts interest**
  and **follows the strategy order** (lands on the current target) and **cascades
  same-month**; a **windfall after payoff is a no-op**; **custom order funnels in
  input order**, distinct from avalanche and snowball on a dataset where all three
  disagree; **custom equals the reordered list** (reordering changes the run).
- `DebtInputsStoreTest`: round-trip `extraStepUpPct`, the `windfalls` list, and
  `strategy = CUSTOM`.
- `DebtCalculatorFormBrowserlessTest`: a windfall row round-trips; **moving a debt
  row up reorders the debts list** the form reports.
- `DebtPlanIT`: adding a windfall (or raising the step-up) recomputes the
  debt-free card; selecting **Custom** and reordering changes the schedule.

## Suggested sequencing
1. **Extra step-up** — one field + one line in the loop + tests. Smallest, lands
   first.
2. **One-off windfalls** — domain bean, store, windfalls row-list, loop
   integration, tests.
3. **Custom ordering** — `CUSTOM` strategy + result wiring + chart line + row
   reorder UI + tests. Largest; do last so the result/chart changes land once.
