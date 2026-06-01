# Retirement Calculator — Requirements

This document captures the current functionality of the calculator, focused on
inputs, calculation semantics, and the data model that drives the projection.
UI structure is described where it changes the meaning of an input.

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

Total: 36 tests at the time of writing.
