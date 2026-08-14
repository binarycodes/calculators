# Debt Payoff Planner — spec

**Status:** proposed (not yet built)
**Menu title:** Debt Planner · **Route:** `/debt` · **Menu order:** 8 (after IRR / XIRR)

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
   In v1.
4. **Interest-saved + months-saved** vs the minimums-only baseline, in money and
   in today's money.
5. **One-off extra payments** (a windfall in a specific month) on top of the
   recurring extra — **phase 2**.
6. **Custom priority order** in addition to avalanche/snowball (drag/reorder or
   an explicit rank) — **phase 2**.

## Inputs (domain)

Package `io.binarycodes.calculators.debt.domain`.

`Debt` (mutable Lombok bean, mirrors `RecurringIncome` shape):
```
String name;
BigDecimal balance;          // > 0
BigDecimal aprPct;           // annual %, nominal /12 like LoanCalculator
BigDecimal minimumPayment;   // fixed floor, optional; falls back to a
                             //   currency-scaled default floor when absent
BigDecimal minimumPct;       // % of current balance, optional (credit cards)
BigDecimal promoAprPct;      // optional intro rate
Integer    promoMonths;      // optional intro-window length
```
Effective monthly minimum for a debt = `max(minimumFloor, minimumPct% ×
balance)`, capped at the remaining balance + its interest — where `minimumFloor`
is the debt's `minimumPayment` if set, else a small **currency-scaled default
floor**. The floor guarantees the payment eventually exceeds the month's
interest as the balance falls, so **every debt strictly amortizes and
terminates**; without it a percent-only minimum shrinks forever and never
clears. The *same* effective-minimum rule is applied in every run — each strategy
and the baseline — so the comparison stays apples-to-apples and the baseline
always reaches payoff (keeping `interestSaved` / `monthsSaved` finite).

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

**Target ordering — fixed and promo-aware.** Each strategy ranks the debts
**once, up front**, and keeps that order for the whole run; the only thing that
changes month to month is which debts are already cleared. This keeps the target
stable (no month-to-month thrashing) and makes the schedule easy to reason about
and test.
- **Avalanche** ranks by the **ongoing (post-promo) APR**, highest first — so a
  debt sitting in a temporary 0% window is *not* pushed to the back just because
  it is momentarily cheap; it is prioritised for the rate it will carry once the
  promo ends.
- **Snowball** ranks by **original balance**, smallest first.
- Tie-break: input order (stable), so the ranking is deterministic.

  *Known simplification:* funnelling extra into a debt while it is still at 0%
  saves no interest that month; the interest-optimal move is to attack the
  highest *current* rate and switch as promos expire. We deliberately trade that
  for a predictable, stable order in v1. A dynamic re-sort variant is a possible
  phase-2 refinement.

Each month `m` (1-based):
1. **Budget** = `Σ initial effective minimums + extraPerMonth`, held **constant**
   as debts clear (that constancy is the rollover). Guard: if the budget is less
   than the first month's `Σ` effective minimums, the plan is **infeasible** →
   surface a warning, no schedule.
2. For each unpaid debt: rate = promo rate if `m ≤ promoMonths` else `aprPct`;
   `interest = balance × rate/12`; `balance += interest`.
3. Pay the **effective minimum** on every unpaid debt (principal = payment −
   interest), capped at that debt's outstanding balance.
4. Funnel the **remaining budget** (budget − minimums paid this month) to the
   **first unpaid debt in the fixed order**. If that payment would more than
   clear the debt, the leftover **cascades within the same month** to the next
   unpaid debt in order, and so on until the budget is exhausted or every debt is
   clear. (So a large surplus can retire more than one debt in a single month.)
5. When a debt hits zero its minimum is naturally reabsorbed — the budget is
   constant, so the surplus flowing to the next target grows.
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
`interestSaved`, `monthsSaved`, and an `infeasible` flag. Each `DebtPlanMonth`
also carries the **set of debts that received surplus** that month, so the grid's
Target(s) column and any year roll-up can list them.

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
Principal Paid, **Target(s)** (every debt that received surplus beyond its
minimum during the period, comma-separated — a year can touch several because of
the same-month cascade and mid-year payoffs), Cumulative Interest. Highlight the
row where the last debt clears (reuse the `setPartNameGenerator` + legend
pattern). To feed this column, each `DebtPlanMonth` records the set of debts that
took surplus that month; the year roll-up unions them in strategy order.

## Wiring & files

Mirror an existing calculator (Buy vs Rent is the closest: view + form + grid +
chart + calculator + store + defaults). New files:
- `debt/domain/`: `Debt`, `PayoffStrategy`, `DebtPlanInputs`, `DebtPlanMonth`/`Year`, `DebtPlanResult`.
- `debt/service/`: `DebtPlanCalculator`, `DebtInputsStore` (implements `InputsStore`, key `debt_inputs`), `DebtDefaultsProvider` (implements `CalculatorDefaults`).
- `debt/ui/`: `DebtView` (`@Route("debt")`, `@Menu(title="Debt Planner", order=8)`, `@AnonymousAllowed`), `DebtPlanForm`, `DebtComparisonChart`, `DebtProjectionGrid`.
- `src/main/resources/debt-defaults.json` — per-currency sample (2–3 debts, e.g. a card at a high APR + a personal loan), plus a modest `extraPerMonth`. Also carries the per-currency **default minimum floor** used when a debt omits `minimumPayment`.
- i18n keys in `translations.properties` (labels, strategy names, card titles, grid columns, the infeasible warning, `debt.addDebt`).
- Share links: automatic — `ScenarioCodec` reuses `DebtInputsStore.toJsonNode`.
- Landing tile blurb (`landing` view) + `REQUIREMENTS.md` section.

## Edge cases
- **Budget < minimums** → infeasible; show a clear message, no chart/grid.
- **Percentage-only minimum** → the currency-scaled default floor guarantees the
  balance strictly decreases, so it still clears; the hard-cap guard + "not paid
  off within 100 years" remains as a backstop for a genuinely too-small budget.
- **APR = 0** (promo or genuine) → no interest; principal = payment.
- **Single debt** → still valid; avalanche == snowball == the loan schedule.
- **Rounding** to the minor currency unit; settle the final payment to zero like
  `LoanCalculator` does.

## Tests
- `DebtPlanCalculatorTest`: avalanche targets the highest **post-promo** APR
  first; snowball the smallest **original** balance; **order stays fixed** across
  months (no thrashing); **rollover** (a cleared debt's minimum accelerates the
  next); **same-month cascade** (a large surplus retires more than one debt in a
  single month); interest saved > 0 with extra; **percentage minimum shrinks** as
  balance falls; a **percent-only minimum still clears** via the default floor
  and the **baseline always reaches payoff** (so `interestSaved`/`monthsSaved`
  stay finite); **promo APR** applies only within the window *and* a 0%-promo
  debt with a high post-promo APR is still prioritised; **infeasible budget**
  flagged; single-debt equals the loan schedule; zero-APR handled.
- `DebtInputsStoreTest`: debts-list round-trip; empty list; null fields.
- `DebtDefaultsJsonTest`: `debt-defaults.json` parses; every currency present.
- Browserless `DebtPlanFormBrowserlessTest`: add/remove debt rows; values
  round-trip through the form.
- Playwright `DebtPlanIT`: add a debt / raise the extra payment → the debt-free
  card recomputes; switching Avalanche↔Snowball changes the totals.

## Resolved decisions
1. **Menu name / route** — "Debt Planner" at `/debt`, menu order 8.
2. **Minimum model** — support **both** a fixed floor *and* a percent-of-balance
   minimum in v1 (`max(minimumPayment, minimumPct% × balance)`); the headline
   differentiator.
3. **Promo APR** — **v1**. Optional `promoAprPct` + `promoMonths` per debt.
4. **Target ordering** — **fixed and promo-aware** (see Calculation model):
   Avalanche by post-promo APR, Snowball by original balance, computed once.
5. **Same-month cascade** — a surplus that over-pays the target rolls to the next
   debt within the same month.
6. **Budget convention** — fixed total with rollover (`Σ minimums + extra`, held
   constant as debts clear).
7. **Minimum floor** — a currency-scaled default floor backs every debt's
   effective minimum, applied in all runs; guarantees termination and keeps the
   baseline finite and comparable.
8. **Grid Target(s) column** — lists every debt that received surplus during the
   period (comma-separated), per-month sets unioned into the year roll-up.
9. **Deferred to phase 2** — one-off windfall payments, custom priority ordering,
   dynamic (interest-optimal) target re-sort, extra-payment step-up over time.

## Suggested phasing
- **Phase 1:** debts list (fixed + %-of-balance minimums), **promo/intro APR**,
  extra/month, Avalanche vs Snowball vs baseline, summary cards + comparison
  chart + grid, store/defaults/i18n, today's-money totals, full tests.
- **Phase 2:** one-off windfalls, custom ordering, dynamic target re-sort,
  extra-payment step-up over time.
