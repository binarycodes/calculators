# Debt Payoff Planner — spec

**Status:** proposed (not yet built)
**Route:** `/debt` · **Menu order:** 8 (after IRR / XIRR)

## Context

We have seven calculators, each filling a gap where mainstream online tools
over-simplify. A **multi-debt payoff planner** is a strong next candidate: the
online ones treat "snowball vs avalanche" as a toy, use a single fixed minimum
payment, ignore credit-card percentage-of-balance minimums and intro-APR
windows, and rarely show the real month-by-month interest cost. It also reuses
almost everything we already have — the reducing-balance amortization from
`LoanCalculator`, the recurring-row UI from the retirement tabs, `MoneyField`,
the comparison-chart + projection-grid patterns, per-currency store/defaults,
and today's-money deflation.

The core question a user should be able to answer: *"Given all my debts and a
fixed monthly budget, when am I debt-free, how much interest do I pay, and how
much do I save by ordering payments avalanche vs snowball?"*

## Goals / non-goals

**Goals**
- Model **multiple debts** at once, each with its own balance, rate, and minimum.
- Compare **Avalanche** (highest rate first) vs **Snowball** (smallest balance
  first) vs a **minimums-only baseline**, side by side.
- Show the real month-by-month schedule: total balance, interest, principal,
  which debt is the current target, and each debt's payoff date.
- Report **interest saved** and **time saved** vs paying minimums only.
- Multi-currency; today's-money (real) total cost via an optional inflation rate.

**Non-goals (v1)**
- Credit-score modelling, fees/penalties, minimum-payment traps beyond the
  interest math, or tax effects.
- Balance-transfer / consolidation optimization (a possible later calculator).

## The details that make it better than the online ones

These are the "almost-perfect-but-missing" features; the first three are the
differentiators:

1. **Percentage-of-balance minimums.** Credit cards bill `max(floor, x% of
   balance)`, which *shrinks* as the balance falls. Online planners use a flat
   minimum and get the payoff curve wrong. Support both: a fixed minimum and/or
   a percent-of-balance minimum with a floor.
2. **Correct snowball rollover.** When a debt clears, its freed-up minimum
   cascades into the payment pool for the next target — the whole point of the
   method. The total monthly outlay stays constant until everything is paid.
3. **Intro / promo APR window per debt.** "0% for 12 months, then 19.9%" is
   everywhere and almost never modelled. Optional `promoAprPct` + `promoMonths`.
4. **Interest-saved + months-saved** vs the minimums-only baseline, in money and
   in today's money.
5. **One-off extra payments** (a windfall in a specific month) on top of the
   recurring extra — optional, can be a later phase.
6. **Custom priority order** in addition to avalanche/snowball (drag/reorder or
   an explicit rank) — optional, later phase.

## Inputs (domain)

Package `io.binarycodes.calculators.debt.domain`.

`Debt` (mutable Lombok bean, mirrors `RecurringIncome` shape):
```
String name;
BigDecimal balance;          // > 0
BigDecimal aprPct;           // annual %, nominal /12 like LoanCalculator
BigDecimal minimumPayment;   // fixed floor, optional
BigDecimal minimumPct;       // % of current balance, optional (credit cards)
BigDecimal promoAprPct;      // optional intro rate
Integer    promoMonths;      // optional intro-window length
```
Effective monthly minimum for a debt = `max(minimumPayment, minimumPct% ×
balance)`, never more than the remaining balance + its interest.

`PayoffStrategy` enum: `AVALANCHE`, `SNOWBALL` (+ `CUSTOM` later).

`DebtPlanInputs`:
```
List<Debt> debts;
BigDecimal extraPerMonth;    // added on top of the summed minimums
PayoffStrategy strategy;     // the "primary" strategy shown as headline
BigDecimal inflationRatePct; // optional, for today's-money totals
```

## Calculation model

Month-by-month simulation (reuse `Rates`, monthly rate = `apr/12` nominal, the
same convention as `LoanCalculator`). Run it once per strategy plus the
minimums-only baseline.

Each month `m` (1-based):
1. **Budget** = `Σ initial minimums + extraPerMonth`, held **constant** as debts
   clear (that constancy is the rollover). Guard: if the budget is less than the
   first month's `Σ` effective minimums, the plan is **infeasible** → surface a
   warning, no schedule.
2. For each unpaid debt, in strategy order:
   - rate = promo rate if `m ≤ promoMonths` else `aprPct`.
   - `interest = balance × rate/12`; `balance += interest`.
3. Pay the **effective minimum** on every debt (principal = payment − interest).
4. Funnel the **remaining budget** (budget − minimums paid this month) entirely
   to the **single target debt**:
   - Avalanche → highest current rate; Snowball → smallest current balance;
     tie-break stable.
5. When a debt hits zero, its minimum is naturally reabsorbed because the budget
   is constant — the surplus to the next target grows.
6. Snapshot the month; record each debt's payoff month the first time it clears.
7. Stop when all balances are zero, or at a hard cap (e.g. 1200 months) → flag
   "not paid off within 100 years" (guards a too-small budget with %-minimums).

**Baseline** = same simulation with `extraPerMonth = 0` and each debt paying only
its effective minimum (no funneling). `interestSaved = baseline.totalInterest −
strategy.totalInterest`; `monthsSaved = baseline.payoffMonth −
strategy.payoffMonth`. Today's-money totals divide each month's interest by
`(1 + inflation)^(m/12)` and sum (mirror `LoanCalculator.realTotalInterest`).

`DebtPlanResult`: for each of {avalanche, snowball, baseline} a schedule
(`List<DebtPlanMonth>` or year-rolled `List<DebtPlanYear>`), `payoffMonth`,
`totalInterest`, `totalPaid`, `realTotalInterest`, per-debt payoff months,
`interestSaved`, `monthsSaved`, and an `infeasible` flag.

## Outputs (UI)

Package `io.binarycodes.calculators.debt.ui`, extends
`BaseCalculatorView<DebtPlanInputs, DebtPlanForm>`.

**Form** (`DebtPlanForm`):
- A **debts section** built like the retirement recurring-row list
  (`InvestmentsTab`/`FutureExpensesTab` pattern): add/remove `DebtRow`s, each a
  `HorizontalLayout` with Name (`TextField`), Balance (`MoneyField`), APR (%),
  Minimum (`MoneyField`), Min % (optional), and — behind an "advanced"
  disclosure — Promo APR / Promo months; a `RowControls.removeButton`.
- **Strategy** selector (`RadioButtonGroup<PayoffStrategy>`, `segmented-toggle`).
- **Extra per month** (`MoneyField`) and optional **Inflation Rate** (%).
- Per-row `Binder` requiring name + positive balance; the tab exposes a
  `ValueSignal<List<Debt>>` folded into the form's computed inputs signal.

**Summary cards** (`SummaryCard` row):
- Debt-free date (primary strategy) · Total interest · Interest saved vs
  minimums-only · Months saved. Optionally a "vs other strategy" delta.
  Total-interest card carries a today's-money subtitle (the `setSecondaryText`
  we added for Buy-vs-Rent).

**Comparison chart:** total outstanding balance over time — Avalanche vs
Snowball vs minimums-only (three lines), mirroring the Loan "Outstanding
Balance" / Buy-vs-Rent comparison chart.

**Projection grid** (`BaseGrid`): year-by-year — Total Balance, Interest Paid,
Principal Paid, Current Target debt, Cumulative Interest. Highlight the row
where the last debt clears (reuse the `setPartNameGenerator` + legend pattern).

## Wiring & files

Mirror an existing calculator (Buy vs Rent is the closest: view + form + grid +
chart + calculator + store + defaults). New files:
- `debt/domain/`: `Debt`, `PayoffStrategy`, `DebtPlanInputs`, `DebtPlanMonth`/`Year`, `DebtPlanResult`.
- `debt/service/`: `DebtPlanCalculator`, `DebtInputsStore` (implements `InputsStore`, key `debt_inputs`), `DebtDefaultsProvider` (implements `CalculatorDefaults`).
- `debt/ui/`: `DebtView` (`@Route("debt")`, `@Menu(title="Debt Payoff", order=8)`, `@AnonymousAllowed`), `DebtPlanForm`, `DebtComparisonChart`, `DebtProjectionGrid`.
- `src/main/resources/debt-defaults.json` — per-currency sample (2–3 debts, e.g. a card at a high APR + a personal loan), plus a modest `extraPerMonth`.
- i18n keys in `translations.properties` (labels, strategy names, card titles, grid columns, the infeasible warning, `debt.addDebt`).
- Share links: automatic — `ScenarioCodec` reuses `DebtInputsStore.toJsonNode`.
- Landing tile blurb (`landing` view) + `REQUIREMENTS.md` section.

## Edge cases
- **Budget < minimums** → infeasible; show a clear message, no chart/grid.
- **Percentage-only minimum** that never fully clears a balance → the hard-cap
  guard + "not paid off within 100 years".
- **APR = 0** (promo or genuine) → no interest; principal = payment.
- **Single debt** → still valid; avalanche == snowball == the loan schedule.
- **Rounding** to the minor currency unit; settle the final payment to zero like
  `LoanCalculator` does.

## Tests
- `DebtPlanCalculatorTest`: avalanche targets the highest APR first; snowball the
  smallest balance; **rollover** (a cleared debt's minimum accelerates the next);
  interest saved > 0 with extra; **percentage minimum shrinks** as balance falls;
  **promo APR** applies only within the window; **infeasible budget** flagged;
  single-debt equals the loan schedule; zero-APR handled.
- `DebtInputsStoreTest`: debts-list round-trip; empty list; null fields.
- `DebtDefaultsJsonTest`: `debt-defaults.json` parses; every currency present.
- Browserless `DebtPlanFormBrowserlessTest`: add/remove debt rows; values
  round-trip through the form.
- Playwright `DebtPlanIT`: add a debt / raise the extra payment → the debt-free
  card recomputes; switching Avalanche↔Snowball changes the totals.

## Open decisions (confirm before building)
1. **Menu name / route** — "Debt Payoff" at `/debt`? (vs "Debt Planner".)
2. **Minimum model** — support both fixed *and* percent-of-balance in v1?
   (Recommended — it's the headline differentiator.)
3. **Promo APR** — v1 or a later phase? (Leaning v1; it's cheap once the loop
   handles a per-month rate.)
4. **Custom ordering** and **one-off windfalls** — defer to phase 2?
5. **Budget convention** — fixed total with rollover (recommended) vs
   pay-actual-minimums + extra.

## Suggested phasing
- **Phase 1:** debts list (fixed + %-of-balance minimums), extra/month,
  Avalanche vs Snowball vs baseline, summary cards + comparison chart + grid,
  store/defaults/i18n, today's-money totals, full tests.
- **Phase 2:** promo/intro APR, one-off windfalls, custom ordering, extra-payment
  step-up over time.
