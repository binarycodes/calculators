# Calculators — Requirements

This document captures the functionality of every calculator in the app,
focused on inputs, calculation semantics, and the data model that drives each
projection. UI structure is described where it changes the meaning of an
input. The app currently ships:

- **Retirement Planner** — full retirement projection across life expectancy.
- **Goal Planner** — solves for the monthly SIP required to hit a post-tax goal.
- **Inflation Projection** — projects an amount forward or backward at a fixed
  inflation rate over a horizon.
- **Investment** — grows regular contributions through an investment phase and a
  subsequent hold phase; reports maturity, net-of-tax, and real value.
- **Loan / EMI** — reducing-balance EMI for a loan, plus a prepayment analysis
  that shows the impact of paying extra (reduce tenure vs reduce EMI).

The landing route (`/`) shows a tile per calculator, populated automatically
from the `@Menu`-annotated views (no manual registration). Each calculator
owns a route segment (`/retirement`, `/goal`, `/inflation`, `/investment`,
`/loan`).

The Years / Ages / Target-Year horizon selector is shared infrastructure:
`base.common.TimeHorizonMode` + `base.common.TimeHorizon.resolveTotalMonths`,
used by both the Goal Planner and Inflation Projection.

## Shareable links

Every calculator has a **Share** button beside its title that encodes the
current inputs plus the active currency into one opaque query token and copies
an absolute URL (`…/retirement?s=<token>`) to the clipboard. The token is
base64url of a small JSON envelope `{"v":1,"currency":…,"inputs":{…}}`, built by
`base.common.ScenarioCodec` from each store's `toJsonNode`. Opening a `?s=` URL
sets the session currency, loads the decoded inputs (overriding the
persisted/default values), saves them as the per-currency snapshot, recomputes,
and strips the `s` parameter from the address bar. The token is treated as
untrusted: oversized tokens, malformed/garbage payloads, an unknown schema
version or currency, and numerically absurd values are rejected, and an invalid
link falls back to the normal persisted/default load with an "Invalid share
link" notice.

# Retirement Planner

## 1. Inputs

The form is split across five tabs sharing a single `Binder` (so the
projection always sees the full set of fields regardless of which tab is
visible). The shape of each tab is below; persistence and defaults round-trip
through `defaults.json` and browser localStorage (`rc_inputs`).

### 1.1 Basic

| Field | Notes |
| --- | --- |
| Current Age | Integer, ≥ 1, must be `< Retirement Age` |
| Retirement Age | Integer, `> Current Age`, `< Life Expectancy` |
| Life Expectancy | Integer, `> Retirement Age` |
| Current Corpus | Money in today's currency, ≥ 0 |
| Monthly Expenses (today) | Money in today's currency, > 0 |
| Inflation Rate | Annual % — drives annual expense growth and is the fallback rate for recurring expenses |

### 1.2 Investments

**Existing Corpus Returns** (one card):

| Field | Meaning |
| --- | --- |
| Before Retirement (%) | Growth rate applied to the main corpus while `age < retireAge` |
| After Retirement (%) | Growth rate applied to the main corpus while `age ≥ retireAge` |
| Tax Rate (%) | Applied to gains-portion of any withdrawal that comes out of the main corpus |

**Monthly Contributions → Before Retirement** and **→ After Retirement**
(separate cards, identical fields):

| Field | Meaning |
| --- | --- |
| Amount | Money in today's currency, per month |
| Growth Percentage (%) | Annual return on the SIP corpus while in this phase |
| Step Up Percentage (Yearly) (%) | Annual increment applied compoundingly to the SIP contribution; resets at retirement |
| Tax Rate (%) | Applied to gains-portion of any withdrawal from the SIP corpus while in this phase |

### 1.3 Future Expenses

Two cards.

**Fixed** — one-off expenses at a specific year (car, knee surgery, wedding):

| Field | Meaning |
| --- | --- |
| Year | Target year |
| Description | Free text |
| Amount (today) | Money in today's currency |
| Inflation (%) | Per-item annual inflation, applied from current year to target year |

**Recurring** — repeating cashflows (rent, school fees, club dues):

| Field | Meaning |
| --- | --- |
| Start Year | First year the cashflow occurs |
| Stop Year | Optional — blank means continue indefinitely |
| Description | Free text |
| Frequency | `Monthly` or `Yearly` |
| Amount (today) | Money in today's currency, per period |
| Inflation (%) | Optional per-item rate — blank ⇒ use overall inflation |

### 1.4 Future Incomes

Two cards mirroring Future Expenses.

**Fixed** — one-off inflows (house sale, business liquidation, inheritance):

| Field | Meaning |
| --- | --- |
| Year | Target year |
| Description | Free text |
| Amount | Nominal value at the target year (not in today's money) |
| Tax Rate (%) | Applied immediately on receipt |

**Recurring** — repeating inflows (rental income, side-gig):

| Field | Meaning |
| --- | --- |
| Start Year | First year the inflow occurs |
| Stop Year | Optional — blank means continue indefinitely |
| Description | Free text |
| Frequency | `Monthly` or `Yearly` |
| Amount | Nominal value per period; no inflation projection |
| Tax Rate (%) | Per-period, applied immediately |

### 1.5 Retirement Benefits

One card with rows for each one-off benefit received in the retirement year
(gratuity, provident fund payout, etc.).

| Field | Meaning |
| --- | --- |
| Description | Free text |
| Amount | Gross amount received in the retirement year |
| Tax Rate (%) | Applied immediately on receipt |

No year field — by definition these are received in the retirement-age year.

## 2. Calculation Model

### 2.1 Buckets

The corpus is modelled as **two persistent buckets**. They are *not* merged
at retirement.

| Bucket | Seeded with | Growth rate | Tax rate |
| --- | --- | --- | --- |
| **Main** | `Current Corpus` as both balance and principal | `Existing Corpus Returns` (Before / After) | `Existing Corpus Tax Rate` |
| **SIP** | 0 / 0 | `Monthly Contributions Growth` (Before / After) | `Monthly Contributions Tax Rate` (Before / After) |

Each bucket tracks `balance` and `principal`. Gains are derived as
`balance − principal`. Contributions raise both; growth raises only the
balance.

### 2.2 Per-year loop

For each `age` from `currentAge` to `lifeExp`:

1. **Phase indicators** — `isPost = age ≥ retireAge`,
   `isRetireYear = age == retireAge`,
   `yearsFromNow = age − currentAge`,
   `year = currentYear + yearsFromNow`.

2. **Annual expenses** — `annualExp = monthlyExp × 12 × (1 + inflation)^yearsFromNow`.
   Withdrawn only when `isPost`.

3. **Snapshot `investedAtRetirement`** — set to `totalInvested` once, at
   the start of the retirement year, *before* this year's contribution.

4. **SIP step-up factor** — `yearsInPhase = isPost ? age − retireAge : age − currentAge`;
   `stepUpFactor = (1 + sipStepUp_currentPhase)^yearsInPhase`.
   The factor resets at retirement so pre-phase compounding does not bleed
   into post.

5. **This year's investment** (lands in the SIP bucket as new principal):
   ```
   sipContribution     = monthlyInv_currentPhase × 12 × stepUpFactor
   benefitsThisYear    = isRetireYear ? Σ (amount × (1 − taxRate))  : 0   (retirement benefits)
   futureIncomeThisYr  = Σ (amount × (1 − taxRate))                       (fixed future incomes whose year == this year)
   recurringIncomeThis = Σ (annualise(amount, frequency) × (1 − taxRate)) (recurring incomes with year ≤ this year ≤ stopYear)
   investment          = sipContribution + benefitsThisYear + futureIncomeThisYr + recurringIncomeThis
   ```
   `totalInvested += investment`; SIP bucket: `principal += investment`,
   `balance += investment`. `annualise(x, MONTHLY) = x × 12`,
   `annualise(x, YEARLY) = x`.

6. **`startCorpus` snapshot** = `main.balance + sip.balance` *(before*
   this year's growth).

7. **Apply growth**:
   ```
   mainReturns = main.balance × growth_currentPhase  ;  main.balance += mainReturns
   sipReturns  = sip.balance  × sipGrowth_currentPhase ; sip.balance  += sipReturns
   returns     = mainReturns + sipReturns
   ```

8. **Withdrawal need**:
   ```
   futureExpenses    = Σ (amount × (1 + itemInflation)^yearsFromNow)             (fixed expenses whose year == this year)
   recurringExpenses = Σ (annualise(amount, frequency) × (1 + effInflation)^yearsFromNow)
   withdrawal        = (isPost ? annualExp : 0) + futureExpenses + recurringExpenses
   ```
   For recurring expenses, `effInflation` is the per-item rate if set
   (positive), otherwise the overall inflation rate.

9. **Drain buckets — lowest-yield-first.** Sort buckets ascending by their
   *current-phase* growth rate; tie-break in favour of main. While
   `remaining > 0`, draw from each bucket in order until either the bucket
   empties or the remaining need is satisfied. For each draw of `X` from a
   bucket with balance `B` and principal `P`:
   ```
   gainsDrawn     = X × (B − P) / B
   principalDrawn = X × P / B
   P             −= principalDrawn
   B             −= X
   taxPaid       += gainsDrawn × bucket.taxRate_currentPhase
   ```
   If the buckets cannot satisfy the withdrawal, the remainder is the
   shortfall.

10. **End-of-year** — `endCorpus = main.balance + sip.balance`
    (or `−remaining` when shortfall).
    `depleted = remaining > 0 ∨ endCorpus < 0`.

11. **Emit `ProjectionRow`** with: `year, age, isRetireYear, isPost,
    annualExp, startCorpus, returns, investment, withdrawal, taxPaid,
    endCorpus, depleted`.

12. **Halt on depletion** — break out of the loop once a year is marked
    depleted.

### 2.3 Semantics worth being explicit about

- **No retirement-year fold.** Main and SIP buckets keep their own growth
  rate, principal-vs-gains split, and tax rate throughout the projection.
- **Withdrawal column = gross corpus drawdown.** Tax is reported separately
  in `Tax Paid`; the user effectively receives `withdrawal − taxPaid` net of
  tax, but the corpus drops by the gross `withdrawal`.
- **Step-up resets at retirement.** Pre-retirement step-up factor never
  bleeds into post-retirement contributions.
- **Benefits and incomes are folded into investment**, *not* subtracted from
  withdrawal. The net (after immediate tax) lands as new principal in the
  SIP bucket and earns at the SIP growth rate from then on.
- **Recurring expenses inflate.** Per-item rate if provided, otherwise the
  overall inflation rate. The entered amount is in today's money.
- **Recurring incomes do not inflate.** The entered amount is the nominal
  cashflow received each period; tax applied per period.
- **Fixed future income amount is at the target year.** No inflation
  projection; it's the realized value in the receipt year.
- **Fixed future expense amount is in today's money.** Inflated per-item
  from current year to the target year.
- **Retirement benefits are received in the retirement-age year** — no
  year input; tax applied immediately on receipt; net lands in SIP bucket.
- **`investedAtRetirement` snapshots `totalInvested` at the start of the
  retirement year, before that year's contribution.**
- **Tax rate is applied to the gains portion of withdrawals only**, not to
  principal returns. The gains/principal split per bucket is tracked
  proportionally on every draw.

### 2.4 Validations

- `currentAge < retireAge < lifeExp`.
- `currentCorpus ≥ 0`.
- `monthlyExpenses > 0`.
- Monthly investments (pre and post) ≥ 0.
- Percentages are `0–100`.
- Recurring `stopYear` is inclusive. Blank ⇒ no end.

## 3. Currency

INR, EUR, USD are supported. Currency is a session-level preference
(localStorage `rc_prefs`). Money fields render the selected currency's
symbol and a helper text spelling out the amount (Indian numbering for
INR, Western for the others). Switching currency replaces the form values
with that currency's persisted snapshot or defaults from `defaults.json`.

## 4. Persistence

| Storage | Contents |
| --- | --- |
| `defaults.json` (classpath) | Per-currency baseline inputs, including all rates set to 0 by default for the new fields |
| `rc_prefs` (localStorage) | Selected currency + theme |
| `rc_inputs` (localStorage) | Per-currency snapshot of the user's edited inputs (all fields, including the recurring/future lists) |

`DefaultsJsonTest` enforces the file is parseable, every supported currency
is present, every documented field key exists with a parseable value, and
the loaded defaults produce a valid projection end-to-end.

## 5. Output

### 5.1 Projection grid

Per year: `Year | Age | Phase | Expenses | Corpus (Start) | Returns |
Investment | Withdrawal | Tax Paid | Corpus (End)`. Rows are themed by
status (retirement-year, depleted, low-corpus). Monthly/yearly amounts are
shown for the cashflow columns; hovering exposes the amount in words.

A column-chooser icon (cog) at the top-right of the grid opens a checkable
menu listing every column; toggling an item hides or shows that column
without recomputing the projection.

### 5.2 Summary cards

`Corpus at Retirement`, `Annual Expenses at Retirement`,
`Corpus Lasts Until` (life-expectancy or depletion age), and
`Final Corpus` (status-coloured by depletion vs. low vs. healthy).

### 5.3 Charts (tabs)

| Tab | Type | What it shows |
| --- | --- | --- |
| Corpus | Area-spline | Yearly corpus with retirement and depletion lines |
| Annual Expenses | Area-spline | Inflation-grown yearly expenses |
| Investments | Donut | Invested principal vs. interest at retirement |
| Return on Investments | Stacked column | Yearly principal + interest until depletion or life expectancy |
| Withdrawal vs Returns | Grouped column | Post-retirement withdrawal vs. returns by year |
| Real Corpus | Split-pane area-spline | Nominal vs. inflation-deflated corpus, on independent y-scales |


## 6. Test coverage

| Suite | Purpose |
| --- | --- |
| `RetirementCalculatorTest` | Bucket math, step-up behaviour, future-expense inflation, retirement-benefit / future-income contribution, lowest-yield-first drain, depletion detection, validation rejections. |
| `DefaultsJsonTest` | `defaults.json` parses, every currency entry has every required field, values are parseable, projection round-trips |
| `MoneyFormatterTest`, `NumberToWordsTest` | Currency formatting (Indian vs Western groupings, words for amounts) |
| `RetirementCalculatorFormBrowserlessTest` | Form smoke test through the binder; catches binding-level regressions in the SIP step-up fields |

# Goal Planner

## 1. Inputs

A single form across one card for the main inputs and a second card for the
time horizon. A segmented radio toggle (`Years` / `Ages` / `Target Year`)
selects which sub-field drives the deadline; non-selected sub-fields are still
present on the bean so switching modes preserves prior entries. Persistence
keys: `gp_inputs` (browser localStorage, per currency); defaults from
`goal-defaults.json` on the classpath.

**Goal card**

| Field | Notes |
| --- | --- |
| Goal Amount (post-tax) | Money in today's currency, > 0. The amount the user wants *in hand* after taxes on gains. |
| Step-Up (%) | Optional. Annual compounding bump on the SIP. Blank reads as zero. |

**Investments card** — repeating rows; user can add or remove buckets.

| Field | Notes |
| --- | --- |
| Label | Free text (e.g. "Equity", "Bonds"). |
| Current | Money, ≥ 0. Current balance of this bucket (treated as 100% principal). |
| Growth (%) | Annual return on this bucket. |
| Tax (%) | Applied to this bucket's gains portion at exit. |
| Allocation (%) | Share of every monthly SIP that flows into this bucket. All rows must sum to 100%; the form shows a live total tinted green/red. |

**Time Horizon card**
| Time-horizon mode | One of `YEARS`, `AGES`, `TARGET_YEAR`. Drives which sub-fields below resolve to *N* total months. |
| ↳ Years + Months | Integer years `0–80` plus integer months `0–11`; their sum must be at least one month (when mode = `YEARS`). |
| ↳ Current Age + Goal Age | Integers, `goalAge > currentAge` (when mode = `AGES`); year resolution only. |
| ↳ Target Year + Target Month | Integer year + month dropdown; must resolve to a future month (when mode = `TARGET_YEAR`). |

## 2. Calculation Model

The required monthly SIP `M` is linear in the goal-vs-corpus gap, so the
calculator solves it in closed form (no iterative root-find). The corpus
compounds monthly; contributions land at the start of each month; step-up
is applied annually (the year-2 contribution becomes `M · (1+s)`, year 3
becomes `M · (1+s)^2`, and so on).

With per-bucket monthly rate `g_m,i = (1 + g_i)^(1/12) − 1`, allocation
fraction `a_i`, exit tax `t_i`, and total months `N_m`:

```
corpus_FV_i      = C_i · (1 + g_m,i)^N_m
net_corpus_FV_i  = corpus_FV_i − (corpus_FV_i − C_i) · t_i

α                = Σ_{m=0..N_m-1} (1 + s)^floor(m/12)                     (principal per unit M)
β_i              = Σ_{m=0..N_m-1} (1 + s)^floor(m/12) · (1 + g_m,i)^(N_m − m)
netPerM_i        = β_i · (1 − t_i) + α · t_i

M = max(0, (Goal − Σ_i net_corpus_FV_i) / Σ_i (a_i · netPerM_i))
```

The contribution at each month is `M · (1+s)^floor(m/12) · a_i` into bucket
*i*. Each bucket compounds at its own monthly rate; final balance and gains
are summed across buckets and each bucket's gains taxed at its own rate.

The projection emits one row per calendar year (`monthsInPeriod = 12`); a
non-whole-year horizon adds a final row with `monthsInPeriod < 12` covering
the leftover months.

Edges:

- **Goal already covered.** If `net_corpus_FV ≥ Goal`, `M` is set to zero and
  the result flags `goalAlreadyCovered = true`. The UI shows a status banner
  on the summary cards and suppresses the chart and projection grid.
- **Validation.** `N ≥ 1`, `Goal > 0`, `C ≥ 0`. Percentages are clamped to
  `0–100` at the form layer.

## 3. Output

### 3.1 Summary cards

`Monthly Investment`, `First-Year Investment` (`= monthly × 12`),
`Final Corpus (gross)`, `Tax at Exit` (`= gains × tax rate`). The "goal
already covered" banner takes over the first two cards when `M = 0`.

### 3.2 Growth chart

Area-spline of corpus build-up across the *N* projected years, with two
series: total balance and cumulative principal (so the gains stack is visible).

### 3.3 Projection grid

Per year: `Year | Age? | Yearly Investment | Balance | Principal | Gains`.
The Age column is shown only when the `AGES` horizon mode supplied a current
age; in the other modes the column is hidden.

## 4. Persistence

| Storage | Contents |
| --- | --- |
| `goal-defaults.json` (classpath) | Per-currency baseline inputs for every field, including each horizon sub-field. |
| `gp_inputs` (localStorage) | Per-currency snapshot of the user's edited inputs. |

## 5. Test coverage

| Suite | Purpose |
| --- | --- |
| `GoalCalculatorTest` | Closed-form solve correctness, monotonicity in growth / step-up / horizon, projection-row reconciliation, validation rejections, edge cases (zero corpus, goal-already-covered, 100% tax). |
| `GoalDefaultsJsonTest` | `goal-defaults.json` parses, every currency has every required field, projection round-trips. |
| `GoalCalculatorFormBrowserlessTest` | Horizon toggle swaps the visible sub-field; binder round-trip preserves values. |

# Inflation Projection

## 1. Inputs

A single card.

| Field | Notes |
| --- | --- |
| Amount | Money, ≥ 0. Interpreted as today's money or a future value per the toggle below. |
| Inflation Rate (%) | Annual rate, 0–100. |
| Amount is in today's money | Checkbox. Checked ⇒ forward projection; unchecked ⇒ backward (discount to today). |
| Time horizon | Shared Years (+ months) / Ages / Target Year (+ month) selector. |

## 2. Calculation Model

With fractional years `y = totalMonths / 12` and rate `i`:

- **Forward** (amount is today's money): `result = amount · (1 + i)^y`.
- **Backward** (amount is a future value): `result = amount / (1 + i)^y`.

`(1 + i)^y` uses `Math.pow` so partial-year horizons compound proportionally.
Zero inflation leaves the amount unchanged; forward and backward are exact
inverses.

## 3. Output

Two summary cards. Forward: "Amount Today" → "Value at Horizon". Backward:
"Amount at Horizon" → "Value in Today's Money". The projected card is
success-tinted.

## 4. Persistence

| Storage | Contents |
| --- | --- |
| `inflation-defaults.json` (classpath) | Per-currency baseline inputs. |
| `ip_inputs` (localStorage) | Per-currency snapshot of the user's edited inputs. |

## 5. Test coverage

| Suite | Purpose |
| --- | --- |
| `InflationCalculatorTest` | Forward/backward correctness, inverse round-trip, zero-inflation no-op, fractional-year compounding, horizon-mode resolution, validation rejections. |
| `InflationDefaultsJsonTest` | `inflation-defaults.json` parses, every currency has every required field, projection round-trips. |

# Investment

## 1. Inputs

A single card.

| Field | Notes |
| --- | --- |
| Amount | Money, > 0. Contributed each period during the investment phase. |
| Contribution frequency | Monthly or Yearly (segmented toggle). Yearly contributions land at the start of each 12-month block. |
| Growth Rate (%) | Annual return; compounded monthly. |
| Tax Rate (%) | Applied to the gains portion once, at the end. |
| Inflation Rate (%) | Deflates the net maturity value (and each year's balance) to today's money. |
| Step-Up (%) | Optional annual ramp on the contribution. |
| Investment time | Shared Years (+ months) / Ages / Target-Year selector — how long contributions continue. |
| Hold time | Plain Years + Months duration after contributions stop. No starting corpus; contributions only. |

## 2. Calculation Model

Monthly compounding (`g_m = (1+g)^(1/12) − 1`). For each month:

- **Investment phase** (`month < investmentMonths`): contribute
  `amount · (1 + stepUp)^floor(month/12)` — every month if Monthly, or only at
  each 12-month boundary if Yearly. Both `balance` and `principal` rise.
- **Hold phase**: no contribution.
- Either way, `balance += balance · g_m`.

At the end: `gains = balance − principal`, `taxAtExit = gains · taxRate`,
`netValue = balance − taxAtExit`, and `buyingPowerToday = netValue / (1+inflation)^totalYears`.
Each projection year also carries its balance deflated to today (`realValue`).

## 3. Output

Four summary cards: Total Invested, Maturity Value (gross), Net After Tax,
Buying Power Today. A stacked column chart (principal + gains) where principal
flattens once contributions stop — making the invest/hold split visible. A
year-by-year grid (Year | Phase | Contribution | Balance | Principal | Gains |
Real Value) with a column chooser; the Phase badge marks Investing vs Holding.

## 4. Persistence

| Storage | Contents |
| --- | --- |
| `investment-defaults.json` (classpath) | Per-currency baseline inputs. |
| `iv_inputs` (localStorage) | Per-currency snapshot of the user's edited inputs. |

## 5. Test coverage

| Suite | Purpose |
| --- | --- |
| `InvestmentCalculatorTest` | Invested-total math, monthly vs yearly cadence, hold-phase growth, gains/tax/net reconciliation, buying-power discounting, step-up, phase split across rows, horizon-mode resolution, validation. |
| `InvestmentDefaultsJsonTest` | `investment-defaults.json` parses, every currency has every required field, projection round-trips. |

# Loan / EMI

## 1. Inputs

Two cards. The prepayment levers all default to zero, so the calculator opens as
a plain EMI calculator.

| Field | Notes |
| --- | --- |
| Loan Amount | Money, > 0. |
| Interest Rate (%) | Annual, reducing balance. |
| Tenure | Years + Months (at least one month total). |
| Inflation Rate (%) | Used only to express the cost in today's money. |
| Extra Payment + frequency | Recurring prepayment paid Monthly / Quarterly / Yearly on top of the EMI. |
| Extra EMIs / year | Additional full EMIs paid once a year (e.g. 1 → effectively 13 EMIs/year). |
| EMI Step-Up (%) | Annual increase of the EMI itself — a "pay more" lever, so it only shortens the tenure. |

## 2. Calculation Model

Reducing balance with the **nominal** monthly rate `r = annual / 12` (the bank
convention, not effective compounding). EMI `= P·r·(1+r)ⁿ / ((1+r)ⁿ − 1)`; a
**zero rate** gives `EMI = P / n`. Each month: `interest = balance · r`,
`principal = EMI − interest`, then any prepayment is applied to the balance.

EMIs are rounded to the minor currency unit, so the **final scheduled
installment settles the remaining balance** (no spill into an extra stub month);
totals are summed from the actual schedule. A loan is rejected if the EMI cannot
cover the monthly interest.

Three scenarios are computed:

- **Baseline** — no prepayments; runs the full tenure.
- **Reduce tenure** — recurring extra + extra-EMIs + step-up are paid on top of a
  fixed EMI, so the loan finishes early. Drives the grid and the chart's
  with-prepayment curve. Headline: interest saved + months saved.
- **Reduce EMI** — recurring extra + extra-EMIs prepay the principal and the loan
  is re-amortized to a lower EMI over the *original* tenure (step-up excluded).
  Headline: interest saved. (The EMI ratchets down a little after every
  prepayment rather than at a single point, so the headline reports the saving,
  not a single lowered installment; the descent is visible in the grid.)

`realTotalInterest` discounts each month's interest to today's money at the
inflation rate.

## 3. Output

Six summary cards: Monthly EMI, Total Interest, Total Payment, Interest Saved ·
Reduce Tenure (with months saved), Interest Saved · Reduce EMI, and Interest in
today's money. Two charts in a tab sheet: an **Outstanding Balance** line chart
— baseline vs with-prepayment when prepayments are active — and a **Principal vs
Interest** stacked-column chart of where each year's outgo goes (principal,
interest, and prepayment when active), tracking the reduce-tenure schedule. A
year-by-year amortization grid
(Year | EMI Paid | Principal | Interest | Prepayment | Balance) with a column
chooser, plus a Reduce Tenure / Reduce EMI toggle beside the heading (shown only
when prepayments are active) that switches the grid between the two schedules.

## 4. Persistence

| Storage | Contents |
| --- | --- |
| `loan-defaults.json` (classpath) | Per-currency baseline inputs (prepayments zero). |
| `ln_inputs` (localStorage) | Per-currency snapshot of the user's edited inputs. |

## 5. Test coverage

| Suite | Purpose |
| --- | --- |
| `LoanCalculatorTest` | EMI formula vs known value, zero-interest split, schedule clears to zero, no-prepayment collapses scenarios, recurring/extra-EMI/step-up prepayments save interest & shorten tenure, reduce-EMI lowers the installment, real interest under inflation, validation rejections. |
| `LoanDefaultsJsonTest` | `loan-defaults.json` parses, every currency has every required field, schedule round-trips. |
