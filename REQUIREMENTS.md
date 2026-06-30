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
- **Buy vs Rent** — compares buying a home against renting and investing the
  difference; projects net worth for both paths and reports the break-even year.
- **IRR / XIRR** — computes the annualised internal rate of return across dated
  cashflows (investments and withdrawals), and flags schedules whose rate is not
  unique (multiple roots).

The landing route (`/`) shows a tile per calculator, populated automatically
from the `@Menu`-annotated views (no manual registration). Each calculator
owns a route segment (`/retirement`, `/goal`, `/inflation`, `/investment`,
`/loan`, `/buyrent`, `/xirr`).

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

## Form validation messages

Every calculator's inputs are grouped into **form-section cards** (`FormCard`).
Validation feedback is shown consistently, in three layers:

- **Field-level** — invalid fields keep their inline Vaadin error (e.g.
  "Required", range messages).
- **Card-level** — each `FormCard` shows a danger **chip at its top-right**
  (the header-suffix slot) describing why *that card* blocks calculation: a
  generic "Fix the highlighted fields" when any of its own fields are invalid,
  or a card-specific rule (e.g. Goal's "Allocations must sum to 100%"). A
  whole-calculation failure (an exception thrown by the calculator) is attached
  to the form's primary/first card. The chip is hidden when the card is valid.
- **Tab-level** — for tabbed forms, a small dot on each tab marks tabs that hold
  data (primary colour) or contain an invalid field (danger colour).

Every card that hosts inputs participates — including the otherwise card-less
list tabs (e.g. Retirement's Future Expenses / Incomes / **Benefits**), which
are wrapped in a section card so they carry the same top-right message. Summary
/ result cards stay neutral placeholders ("—") while a form is invalid; the
message lives on the input card, never on a result card.

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
| Timeline | Timeline | Major life/financial events along the age axis (see 5.3.1) |

#### 5.3.1 Plan Timeline

A timeline chart marking the plan's major events on the age axis. All events
falling in the same year are **clubbed into a single marker** whose inline label
is the event count; the hover tooltip lists every event for that year (with the
amount where relevant). Retirement, drawdown-begins and depletion years are
coloured distinctly from ordinary years.

Events marked:
- **Today** — the current age and starting corpus.
- **Retirement** — the retirement-age year, plus any retirement benefits received.
- **One-off future incomes / expenses** — at their year.
- **Recurring income / expense start and stop** — at the start year and, when set,
  the stop year.
- **Wealth milestones** — the first year the **nominal** corpus crosses each
  threshold. Thresholds are per-currency: INR ₹10L, ₹50L, ₹1cr, ₹5cr, ₹10cr,
  ₹50cr; USD/EUR $1M, $10M, $50M, $100M, $500M, $1B.
- **Drawdown begins** — the first year the corpus shrinks year-on-year
  (`endCorpus < startCorpus`, i.e. outflows exceed returns plus inflows).
- **Corpus depletion** — the year the corpus is exhausted, if it is.


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

**Investments card** — repeating rows; user can add or remove buckets.

| Field | Notes |
| --- | --- |
| Label | Free text (e.g. "Equity", "Bonds"). |
| Current | Money, ≥ 0. Current balance of this bucket (treated as 100% principal). |
| Growth (%) | Annual return on this bucket. |
| Tax (%) | Applied to this bucket's gains portion at exit. |
| Step-Up (%) | Optional. Per-bucket annual compounding bump on this bucket's share of the SIP. Blank reads as zero. |
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
is applied annually and **per bucket** (bucket *i*'s year-2 share becomes
`M · a_i · (1+s_i)`, year 3 becomes `M · a_i · (1+s_i)^2`, and so on).

With per-bucket monthly rate `g_m,i = (1 + g_i)^(1/12) − 1`, allocation
fraction `a_i`, exit tax `t_i`, per-bucket step-up `s_i`, and total months `N_m`:

```
corpus_FV_i      = C_i · (1 + g_m,i)^N_m
net_corpus_FV_i  = corpus_FV_i − (corpus_FV_i − C_i) · t_i

α_i              = Σ_{m=0..N_m-1} (1 + s_i)^floor(m/12)                     (principal per unit M·a_i)
β_i              = Σ_{m=0..N_m-1} (1 + s_i)^floor(m/12) · (1 + g_m,i)^(N_m − m)
netPerM_i        = β_i · (1 − t_i) + α_i · t_i

M = max(0, (Goal − Σ_i net_corpus_FV_i) / Σ_i (a_i · netPerM_i))
```

The contribution at each month is `M · a_i · (1+s_i)^floor(m/12)` into bucket
*i*. Each bucket compounds at its own monthly rate and applies its own step-up;
final balance and gains are summed across buckets and each bucket's gains taxed
at its own rate.

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

### 3.2 Growth charts

Two tabs:

- **Corpus Build-Up** — stacked column of corpus build-up across the *N*
  projected years (or per month for horizons under 36 months): principal at the
  bottom, gains stacked on top.
- **By Investment** — one line per investment bucket, showing each bucket's
  end-of-period balance over the same timeline. Lines are labelled by the
  bucket's label (falling back to "Investment *n*" when blank). Backed by
  `GoalResult.investmentSeries` (per-bucket balances aligned to the yearly rows
  and, for short horizons, the monthly snapshots).

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
| Inflation Variation (±%) | Optional, 0–20. Uncertainty band around the rate (e.g. 2 ⇒ ±2%). Drives the area-range chart only; does not affect the headline result. |
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

Two chart tabs:

- **Value Over Time** — area-spline of the central projection year by year.
- **Variation Range** — area-range band between the `rate − variation` and
  `rate + variation` trajectories, with the central "Expected" line on top.
  Forward: the entered amount is fixed today, so the band starts as a point and
  fans out toward the horizon. Backward: the entered amount is the fixed value at
  the horizon end, so the band converges there and fans out toward today (a higher
  rate discounts to a smaller present value, forming the lower edge). Collapses
  onto the line when the variation is zero. Backed by `InflationResult.band`.

## 4. Persistence

| Storage | Contents |
| --- | --- |
| `inflation-defaults.json` (classpath) | Per-currency baseline inputs. |
| `ip_inputs` (localStorage) | Per-currency snapshot of the user's edited inputs. |

## 5. Test coverage

| Suite | Purpose |
| --- | --- |
| `InflationCalculatorTest` | Forward/backward correctness, inverse round-trip, zero-inflation no-op, fractional-year compounding, horizon-mode resolution, validation rejections, variation band brackets the central line, fans out forward but converges at the fixed horizon end when backward, and collapses at zero variation. |
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
`netValue = balance − taxAtExit`, and `buyingPowerToday = netValue / (1+inflation)^totalYears`,
where `totalYears = totalMonths / 12` is fractional, so a partial-year horizon deflates proportionally.
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
| Extra EMIs / year | Additional full EMIs paid once a year (e.g. 1 → effectively 13 EMIs/year), each valued at the installment then in force (the re-amortized EMI under reduce-EMI). |
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
  is re-amortized to a lower EMI over the *remaining* original tenure (step-up
  excluded). Because the prepayments keep cutting the balance, the loan may still
  clear before the original tenure ends.
  Headline: interest saved. (The EMI ratchets down a little after every
  prepayment rather than at a single point, so the headline reports the saving,
  not a single lowered installment; the descent is visible in the grid.) An
  extra-EMI prepayment is valued at the re-amortized EMI of that year, so it
  shrinks alongside the installment rather than staying pinned to the original.

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

---

# Buy vs Rent Calculator (`/buyrent`)

Compares the financial outcome of buying a home versus renting over a
user-defined horizon. Both paths are projected month-by-month; net worth is
snapshotted at each year boundary.

## 1. Inputs

| Field | Section | Constraint | Meaning |
| --- | --- | --- | --- |
| Home Price | Home Purchase | > 0, Required | Purchase price of the property. |
| Down Payment | Home Purchase | 0–99 %, Required | Percentage of home price paid upfront. |
| Loan Term | Home Purchase | 1–40 yrs, Required | Mortgage duration in years. |
| Mortgage Rate | Home Purchase | 0–30 %, Required | Annual interest rate on the loan. |
| Property Tax Rate | Costs & Appreciation | 0–5 % | Annual property tax as % of current home value. |
| Maintenance Rate | Costs & Appreciation | 0–5 % | Annual maintenance cost as % of current home value. |
| Home Appreciation | Costs & Appreciation | 0–20 % | Annual rate at which the home gains value. |
| Buying Costs | Costs & Appreciation | 0–15 % | Upfront transaction costs (stamp duty, registration) as % of home price. |
| Selling Costs | Costs & Appreciation | 0–10 % | Sale-time costs (agent fees) as % of sale price. |
| Monthly Rent | Renting | > 0, Required | Starting monthly rent. |
| Annual Rent Increase | Renting | 0–20 % | Step-up applied once per year (rent is flat within a year). |
| Investment Return | Analysis | 0–30 %, Required | Annual return on the rent-path portfolio. |
| Inflation Rate | Analysis | 0–20 % | Intended to express the net-worth difference in today's money. NOTE: the real (deflated) difference is computed but not yet surfaced in any chart/grid/card, so this input currently has no visible effect — see `docs/issues/buyrent-inflation-input-not-surfaced.md`. |
| Analysis Horizon | Analysis | 1–50 yrs, Required | How many years to project. |
| Property Capital Gains Tax | Analysis | 0–60 % | Tax rate on the profit when the home is sold (sale proceeds − cost basis). |
| Investment Gains Tax | Analysis | 0–60 % | Tax rate on the investment portfolio profit at exit (portfolio − net contributions). |

## 2. Financial Model

### Buy path
- Down payment + buying costs are paid upfront.
- Monthly EMI is computed using standard reducing-balance amortisation for the
  full loan term; once the loan is paid off there is no more EMI.
- Property tax + maintenance accrue monthly as a fraction of the current home
  value (which appreciates at the stated rate).
- Net worth at year Y (pre-tax) = home value × (1 − selling cost %) − outstanding mortgage balance.
- Property capital-gains tax on exit = max(0, sale proceeds − cost basis) × property CGT rate,
  where cost basis = home price × (1 + buying cost %).
- Net worth after tax = pre-tax equity − property capital-gains tax.

### Rent path
- The down payment + buying costs (capital not spent on the purchase) are
  invested immediately at the investment return rate.
- Each month the **surplus** (buy monthly cost − rent this month) is added to
  the portfolio; if rent exceeds buy costs the deficit is withdrawn.
- Rent is flat within each year and steps up once per completed year at the
  rent-increase rate (e.g. 5 % ⇒ year 2's monthly rent = year 1 × 1.05).
- Portfolio net worth (pre-tax) at year Y = investment portfolio balance.
- Investment capital-gains tax on exit = max(0, portfolio − net contributions) × investment CGT rate,
  where net contributions = initial investment + cumulative monthly surpluses (positive or negative).
- Net worth after tax = portfolio − investment capital-gains tax.

### Break-even
The first year where after-tax buy equity ≥ after-tax rent portfolio. Reported as "Not in
horizon" if buy never catches up within the analysis period.

## 3. Outputs

### Summary row (5 cards)
| Card | Value |
| --- | --- |
| Monthly Cost: Buy | EMI + first-month property tax + maintenance |
| Monthly Cost: Rent | First month's rent |
| Break-Even | First year buy is ahead, or "Not in horizon" |
| Net Worth: Buy | After-tax equity at end of horizon |
| Net Worth: Rent | After-tax portfolio at end of horizon |

All net-worth values are after capital-gains tax. The winning path at the horizon is highlighted in success green.

### Comparison chart
Line chart showing buy equity (blue) and rent portfolio (green) from year 1 to
the analysis horizon. Where they cross is the break-even year.

### Year-by-year projection grid
| Column | Description |
| --- | --- |
| Year | Year number |
| Home Value | Appreciated home price |
| Mortgage Balance | Outstanding loan (0 after payoff) |
| Buy Net Worth | Equity after sell costs |
| Rent Portfolio | Accumulated investment |
| Difference (Buy − Rent) | Positive = buy ahead |

The break-even row is highlighted with a success background.

## 4. Persistence & sharing
| Key | Content |
| --- | --- |
| `buyrent-defaults.json` (classpath) | Per-currency baseline inputs. |
| `bvr_inputs` (localStorage) | Per-currency snapshot of the user's edited inputs. |

# IRR / XIRR Calculator (`/xirr`)

Computes the money-weighted annualised return (XIRR) over a set of dated
cashflows, and surfaces the case the calculation cares most about: a
non-conventional schedule whose rate is **not unique**.

## 1. Inputs

The form has two tabs — **Investments** (money paid in) and **Withdrawals**
(money received) — each split into a one-off card and a recurring card. Amounts
are entered as positive magnitudes; the calculator assigns the sign
(investments negative, withdrawals positive). Every row exposes its list as a
signal, folded into one inputs signal for live recalculation.

### 1.1 One-off cashflow (per row)

| Field | Notes |
| --- | --- |
| Date | Required calendar date the money moves. |
| Description | Optional label, carried through to the schedule grid. |
| Amount | Required money in today's currency, > 0. |

### 1.2 Recurring cashflow (per row)

| Field | Notes |
| --- | --- |
| Start Date | Required date of the first payment. |
| Frequency | Monthly / Quarterly / Half-Yearly / Yearly (1 / 3 / 6 / 12 months per period). |
| Payments | Required count ≥ 1; expands to one cashflow per occurrence at `startDate + n × period`. |
| Description | Optional label. |
| Amount | Required per-payment amount, > 0. |

## 2. Calculation Model

`irr.service.XirrCalculator.calculate(inputs)` expands the four lists into a
signed, date-sorted `List<Cashflow>` and delegates the maths to
`irr.service.Xirr`.

### 2.1 NPV and the rate

Time is measured in years from the earliest cashflow on an **Actual/365**
day-count (matching spreadsheet `XIRR`):

```
NPV(r) = Σ CF_i / (1 + r)^((d_i − d_0) / 365)
```

The IRR is any rate `r > −100%` with `NPV(r) = 0`.

### 2.2 Finding every root (not just one)

Roots are found by **scanning** NPV across `[−99.9999%, +10000%]` in fine steps and
**bisecting** each sign-change bracket. Bisection is used deliberately over
Newton-Raphson: it cannot diverge and it returns *all* roots, which is what a
multiple-IRR schedule needs. Roots within `1e-4` of each other are de-duplicated.

### 2.3 Determinacy

The number of **sign changes** in the date-ordered amounts is reported
(Descartes' rule of signs). The result status is:

- **`UNIQUE`** — exactly one root; the XIRR is well defined.
- **`NON_UNIQUE`** — more than one root; no single rate is meaningful. The
  headline XIRR is the root closest to zero, reported **with a warning** that
  lists every root and renders the NPV-vs-rate curve so the crossings are
  visible.

Invalid input is rejected with a message (translation key) shown in the view's
banner: fewer than two cashflows (`needTwoCashflows`), one-directional cashflows
that can never break even (`needBothDirections`), or no root in the searched
range (`noRate`). Blank rows are ignored rather than failing.

### 2.4 Derived figures

| Figure | Meaning |
| --- | --- |
| Total Invested | Sum of outflow magnitudes. |
| Total Withdrawn | Sum of inflows. |
| Net Cashflow | `Total Withdrawn − Total Invested` (undiscounted). |
| Payback date | First date the running (undiscounted) total turns non-negative. |
| NPV curve | NPV sampled across a display range for the chart. |

## 3. Output

- **Summary cards**: XIRR (annualised, tinted as a warning when non-unique),
  Total Invested, Total Withdrawn, Net Cashflow (tinted by sign).
- **Warning banner**: shown for a non-unique rate (lists the roots) or an input
  error.
- **Charts** (tabbed): Cashflow Timeline (columns by date, investments down /
  withdrawals up), NPV vs Rate (curve with the zero baseline and a marked line
  per root; the curve range stretches to enclose every root so each marked line
  lands on a visible zero-crossing), Cumulative Cashflow (running balance with
  the payback date marked).
- **Schedule grid**: every expanded cashflow — date, description, signed amount,
  running cumulative.

## 4. Currency

Amounts and chart axes format via `MoneyFormatter` for the active currency;
switching currency reloads that currency's persisted/default schedule.

## 5. Persistence & sharing

| Key | Content |
| --- | --- |
| `xirr-defaults.json` (classpath) | Per-currency sample schedule (a three-year monthly SIP redeemed at a current value). |
| `xirr_inputs` (localStorage) | Per-currency snapshot of the user's edited cashflows (dates as ISO-8601 strings). |

Both reuse `XirrInputsStore`'s `toJsonNode` / `fromJsonNode`, which also back the
shareable-link codec.

## 6. Test coverage

`Xirr` (NPV, root finding, sign changes, multiple roots), `XirrCalculator`
(expansion, totals, payback, status, validation), `XirrInputsStore` (JSON
round-trip), and `XirrDefaultsJsonTest` (defaults parse and resolve) — all under
the `*/service` 80% coverage gate.
