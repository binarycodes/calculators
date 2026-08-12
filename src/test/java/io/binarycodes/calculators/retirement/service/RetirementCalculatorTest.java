package io.binarycodes.calculators.retirement.service;

import io.binarycodes.calculators.base.common.Frequency;
import io.binarycodes.calculators.retirement.domain.FutureExpense;
import io.binarycodes.calculators.retirement.domain.FutureIncome;
import io.binarycodes.calculators.retirement.domain.ProjectionRow;
import io.binarycodes.calculators.retirement.domain.RecurringExpense;
import io.binarycodes.calculators.retirement.domain.RecurringIncome;
import io.binarycodes.calculators.retirement.domain.RetirementBenefit;
import io.binarycodes.calculators.retirement.domain.RetirementInputs;
import io.binarycodes.calculators.retirement.domain.RetirementResult;

import java.math.MathContext;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetirementCalculatorTest {

    /**
     * INR defaults from defaults.json — matches what loadCurrentCurrencyValues() applies.
     */
    private static RetirementInputs inrDefaults() {
        final var inputs = new RetirementInputs();
        inputs.setCurrentAge(38);
        inputs.setRetireAge(45);
        inputs.setLifeExp(90);
        inputs.setCorpus(bd(15_000_000));
        inputs.setMonthlyExpenses(bd(100_000));
        inputs.setInflationPct(bd(8));
        inputs.setGrowthPrePct(bd(12));
        inputs.setGrowthPostPct(bd(8));
        inputs.setCorpusTaxRatePct(bd(0));
        inputs.setMonthlyInvPre(bd(150_000));
        inputs.setSipGrowthPrePct(bd(12));
        inputs.setSipStepUpPrePct(bd(0));
        inputs.setTaxRatePrePct(bd(0));
        inputs.setMonthlyInvPost(bd(0));
        inputs.setSipGrowthPostPct(bd(0));
        inputs.setSipStepUpPostPct(bd(0));
        inputs.setTaxRatePostPct(bd(0));
        return inputs;
    }

    private static RetirementInputs allOnes(int currentAge, int retireAge, int lifeExp) {
        final var inputs = new RetirementInputs();
        inputs.setCurrentAge(currentAge);
        inputs.setRetireAge(retireAge);
        inputs.setLifeExp(lifeExp);
        inputs.setCorpus(bd(1));
        inputs.setMonthlyExpenses(bd(1));
        inputs.setInflationPct(bd(1));
        inputs.setGrowthPrePct(bd(1));
        inputs.setGrowthPostPct(bd(1));
        inputs.setCorpusTaxRatePct(bd(1));
        inputs.setMonthlyInvPre(bd(1));
        inputs.setSipGrowthPrePct(bd(1));
        inputs.setSipStepUpPrePct(bd(1));
        inputs.setTaxRatePrePct(bd(1));
        inputs.setMonthlyInvPost(bd(1));
        inputs.setSipGrowthPostPct(bd(1));
        inputs.setSipStepUpPostPct(bd(1));
        inputs.setTaxRatePostPct(bd(1));
        return inputs;
    }

    @Test
    void inr_defaults_produce_rows_through_lifeExp_or_depletion() {
        final RetirementResult r = RetirementCalculator.calculate(inrDefaults());

        // Either we ran out OR we reached life expectancy.
        final ProjectionRow last = r.rows().get(r.rows().size() - 1);
        assertTrue(last.age() <= 90, "should not go past life expectancy");
        if (r.corpusDepletedAt().isPresent()) {
            assertEquals(last.age(), r.corpusDepletedAt().get(),
                    "depletion year should be the last row");
            assertTrue(last.endCorpus().signum() < 0, "depletion row endCorpus < 0");
        } else {
            assertEquals(90, last.age(), "ran to life expectancy");
        }
    }

    @Test
    void retirement_year_is_marked_exactly_once() {
        final RetirementResult r = RetirementCalculator.calculate(inrDefaults());
        final long retireYears = r.rows().stream().filter(ProjectionRow::isRetireYear).count();
        assertEquals(1, retireYears);
        final ProjectionRow retire = r.rows().stream().filter(ProjectionRow::isRetireYear).findFirst().orElseThrow();
        assertEquals(45, retire.age());
        assertTrue(retire.isPost(), "retirement year is the start of post-retirement");
    }

    @Test
    void pre_retirement_rows_have_no_withdrawal() {
        final RetirementResult r = RetirementCalculator.calculate(inrDefaults());
        r.rows().stream().filter(row -> !row.isPost()).forEach(row ->
                assertEquals(0, row.withdrawal().signum(), "pre-retirement row " + row.age()));
    }

    @Test
    void post_retirement_rows_have_positive_withdrawal() {
        final RetirementResult r = RetirementCalculator.calculate(inrDefaults());
        r.rows().stream().filter(ProjectionRow::isPost).forEach(row ->
                assertTrue(row.withdrawal().signum() > 0, "post row " + row.age()));
    }

    @Test
    void expenses_grow_with_inflation_year_over_year() {
        final RetirementResult r = RetirementCalculator.calculate(inrDefaults());
        for (int i = 1; i < r.rows().size(); i++) {
            final ProjectionRow prev = r.rows().get(i - 1);
            final ProjectionRow cur = r.rows().get(i);
            assertTrue(cur.annualExp().compareTo(prev.annualExp()) > 0,
                    "annualExp should grow year-over-year");
        }
    }

    @Test
    void invested_at_retirement_includes_initial_corpus_plus_pre_sips() {
        final RetirementResult r = RetirementCalculator.calculate(inrDefaults());
        // initial 1.5 Cr + 7 yrs * 12 mo * 1.5 L/mo = 1.5 Cr + 1.26 Cr = 2.76 Cr
        assertEquals(0,
                r.investedAtRetirement().compareTo(bd(27_600_000)),
                "1.5 Cr corpus + (45-38)*12*150_000 = 2.76 Cr");
    }

    @Test
    void lasts_until_row_is_last_fully_covered_year() {
        final RetirementResult r = RetirementCalculator.calculate(inrDefaults());
        final ProjectionRow lasts = r.lastsUntilRow();
        assertTrue(lasts.endCorpus().signum() >= 0, "lastsUntil row must be non-negative");
        if (r.corpusDepletedAt().isPresent()) {
            assertEquals(r.corpusDepletedAt().get() - 1, lasts.age());
        } else {
            assertEquals(90, lasts.age());
        }
    }

    @Test
    void step_up_zero_does_not_grow_investments() {
        // With step-up = 0, every pre-retirement row's investment equals the
        // base annual SIP (the default inrDefaults has post-SIP = 0, so we
        // only need to check the pre-retirement window).
        final RetirementResult result = RetirementCalculator.calculate(inrDefaults());
        final BigDecimal baseAnnualSip = bd(150_000).multiply(BigDecimal.valueOf(12));
        result.rows().stream().filter(row -> !row.isPost()).forEach(row ->
                assertEquals(0, row.investment().compareTo(baseAnnualSip),
                        "pre-retirement investment should be constant at age " + row.age()));
    }

    @Test
    void pre_step_up_compounds_year_over_year() {
        // 10% annual step-up on a 150,000 monthly SIP: each successive
        // pre-retirement year's investment is 1.1x the previous one.
        final RetirementInputs inputs = inrDefaults();
        inputs.setSipStepUpPrePct(bd(10));
        final RetirementResult result = RetirementCalculator.calculate(inputs);

        final java.util.List<ProjectionRow> preRows = result.rows().stream()
                .filter(row -> !row.isPost()).toList();
        final BigDecimal stepUp = new BigDecimal("1.10");
        for (int i = 1; i < preRows.size(); i++) {
            final BigDecimal expected = preRows.get(i - 1).investment().multiply(stepUp);
            final BigDecimal actual = preRows.get(i).investment();
            // Use small tolerance because BigDecimal DECIMAL64 carries float-ish drift.
            final BigDecimal diff = expected.subtract(actual).abs();
            final BigDecimal tolerance = expected.movePointLeft(6);
            assertTrue(diff.compareTo(tolerance) <= 0,
                    "year " + preRows.get(i).age() + " expected ≈" + expected + " got " + actual);
        }
    }

    @Test
    void post_step_up_resets_at_retirement() {
        // Aggressive pre step-up (20%) plus a non-zero post-retirement SIP.
        // The first post-retirement row's investment must equal the base
        // post-SIP × 1 (no inherited compounding from the pre phase).
        final RetirementInputs inputs = inrDefaults();
        inputs.setSipStepUpPrePct(bd(20));
        inputs.setMonthlyInvPost(bd(50_000));
        inputs.setSipStepUpPostPct(bd(0));
        final RetirementResult result = RetirementCalculator.calculate(inputs);

        final ProjectionRow firstPostRow = result.rows().stream()
                .filter(ProjectionRow::isPost).findFirst().orElseThrow();
        final BigDecimal baseAnnualPostSip = bd(50_000).multiply(BigDecimal.valueOf(12));
        assertEquals(0, firstPostRow.investment().compareTo(baseAnnualPostSip),
                "post phase must start at base SIP — pre step-up factor must not bleed over");

        // And with post step-up = 0, every later post row also equals base.
        result.rows().stream().filter(ProjectionRow::isPost).forEach(row ->
                assertEquals(0, row.investment().compareTo(baseAnnualPostSip),
                        "post row " + row.age() + " should stay at base SIP"));
    }

    @Test
    void step_up_increases_invested_at_retirement() {
        final BigDecimal baseInvested = RetirementCalculator
                .calculate(inrDefaults()).investedAtRetirement();

        final RetirementInputs withStepUp = inrDefaults();
        withStepUp.setSipStepUpPrePct(bd(10));
        final BigDecimal stepUpInvested = RetirementCalculator
                .calculate(withStepUp).investedAtRetirement();

        assertTrue(stepUpInvested.compareTo(baseInvested) > 0,
                "10% step-up must raise total invested at retirement; base=" + baseInvested
                        + " step-up=" + stepUpInvested);
    }

    @Test
    void future_expense_inflates_and_adds_to_withdrawal_in_target_year() {
        final int currentYear = Year.now().getValue();
        final int targetYear = currentYear + 4;

        final RetirementInputs inputs = inrDefaults();
        final FutureExpense car = new FutureExpense();
        car.setYear(targetYear);
        car.setDescription("Buy a car");
        car.setAmount(bd(1_000_000));
        car.setInflationPct(bd(7));
        inputs.setFutureExpenses(List.of(car));

        final RetirementResult result = RetirementCalculator.calculate(inputs);

        final ProjectionRow targetRow = result.rows().stream()
                .filter(row -> row.year() == targetYear).findFirst().orElseThrow();
        // Expense at target year = 1,000,000 × (1.07)^4 ≈ 1,310,796.
        final BigDecimal expectedInflated = bd(1_000_000)
                .multiply(new BigDecimal("1.07").pow(4));
        // Pre-retirement year (age 42, since current is 38 + 4 = 42, retire is 45),
        // so the only withdrawal that year is the future expense.
        final BigDecimal diff = targetRow.withdrawal().subtract(expectedInflated).abs();
        final BigDecimal tolerance = expectedInflated.movePointLeft(4);
        assertTrue(diff.compareTo(tolerance) <= 0,
                "withdrawal at " + targetYear + " expected ≈" + expectedInflated
                        + " got " + targetRow.withdrawal());
    }

    @Test
    void retirement_benefit_after_tax_adds_to_investment_in_retirement_year() {
        final RetirementInputs baseline = inrDefaults();
        final ProjectionRow baselineRetireRow = RetirementCalculator.calculate(baseline).rows().stream()
                .filter(ProjectionRow::isRetireYear).findFirst().orElseThrow();
        final BigDecimal baselineInvestment = baselineRetireRow.investment();
        final BigDecimal baselineWithdrawal = baselineRetireRow.withdrawal();

        final RetirementInputs withBenefit = inrDefaults();
        final RetirementBenefit pension = new RetirementBenefit();
        pension.setDescription("Gratuity");
        pension.setAmount(bd(2_000_000));
        pension.setTaxRatePct(bd(20));
        withBenefit.setRetirementBenefits(List.of(pension));

        final ProjectionRow withBenefitRetireRow = RetirementCalculator.calculate(withBenefit).rows().stream()
                .filter(ProjectionRow::isRetireYear).findFirst().orElseThrow();

        // Net benefit = 2,000,000 × (1 − 0.20) = 1,600,000. It should land in
        // that year's investment column, not change the withdrawal column.
        final BigDecimal expectedNet = bd(1_600_000);
        final BigDecimal expectedInvestment = baselineInvestment.add(expectedNet);
        final BigDecimal diff = withBenefitRetireRow.investment().subtract(expectedInvestment).abs();
        final BigDecimal tolerance = expectedNet.movePointLeft(4);
        assertTrue(diff.compareTo(tolerance) <= 0,
                "investment expected ≈" + expectedInvestment + " got " + withBenefitRetireRow.investment());
        assertEquals(0, withBenefitRetireRow.withdrawal().compareTo(baselineWithdrawal),
                "withdrawal must be unchanged when benefits flow through investment");
    }

    @Test
    void future_income_after_tax_adds_to_investment_in_target_year() {
        final int currentYear = Year.now().getValue();
        final int targetYear = currentYear + 10; // post-retirement (retire at 45 = +7)

        final RetirementInputs baseline = inrDefaults();
        final ProjectionRow baselineRow = RetirementCalculator.calculate(baseline).rows().stream()
                .filter(r -> r.year() == targetYear).findFirst().orElseThrow();

        final RetirementInputs withIncome = inrDefaults();
        final FutureIncome houseSale = new FutureIncome();
        houseSale.setYear(targetYear);
        houseSale.setDescription("House sale");
        houseSale.setAmount(bd(5_000_000));
        houseSale.setTaxRatePct(bd(20));
        withIncome.setFutureIncomes(List.of(houseSale));

        final ProjectionRow withIncomeRow = RetirementCalculator.calculate(withIncome).rows().stream()
                .filter(r -> r.year() == targetYear).findFirst().orElseThrow();

        // Net income = 5,000,000 × (1 − 0.20) = 4,000,000.
        final BigDecimal expectedNet = bd(4_000_000);
        final BigDecimal expectedInvestment = baselineRow.investment().add(expectedNet);
        final BigDecimal diff = withIncomeRow.investment().subtract(expectedInvestment).abs();
        final BigDecimal tolerance = expectedNet.movePointLeft(4);
        assertTrue(diff.compareTo(tolerance) <= 0,
                "investment expected ≈" + expectedInvestment + " got " + withIncomeRow.investment());
        assertEquals(0, withIncomeRow.withdrawal().compareTo(baselineRow.withdrawal()),
                "withdrawal must be unchanged when income flows through investment");
    }

    @Test
    void lowest_yield_first_outlasts_proportional_drain() {
        // Configure a scenario where the main corpus grows much faster than
        // the SIP corpus post-retirement, so the SIP bucket should be drained
        // first. With pre-retirement bucket growth as well, the SIP corpus
        // ends up with a significant balance at retirement, but its zero
        // post-retirement growth means it's the natural sink for withdrawals
        // until it empties. As long as the main corpus survives life
        // expectancy, the calculator should not flag depletion.
        final RetirementInputs inputs = inrDefaults();
        inputs.setGrowthPostPct(bd(10));      // fat main bucket post-retirement
        inputs.setSipGrowthPostPct(bd(0));    // SIP earns nothing post-retirement
        inputs.setMonthlyInvPost(bd(0));      // no further SIP contributions
        // Initial corpus is generous — under proportional-drain we'd be eating
        // into the high-yielding main from day one. Under lowest-first the SIP
        // bucket goes first and main keeps compounding at 10%.
        inputs.setCorpus(bd(30_000_000));

        final RetirementResult result = RetirementCalculator.calculate(inputs);

        assertTrue(result.corpusDepletedAt().isEmpty(),
                "lowest-yield-first must let the higher-yielding bucket compound "
                        + "untouched until the slower bucket empties; depleted at "
                        + result.corpusDepletedAt().orElse(null));
    }

    @Test
    void rejects_currentAge_ge_retireAge() {
        assertThrows(IllegalArgumentException.class,
                () -> RetirementCalculator.calculate(allOnes(50, 50, 90)));
    }

    @Test
    void rejects_retireAge_ge_lifeExp() {
        assertThrows(IllegalArgumentException.class,
                () -> RetirementCalculator.calculate(allOnes(35, 90, 90)));
    }

    // -------------------------------------------------------------------------
    // Recurring income
    // -------------------------------------------------------------------------

    @Test
    void recurring_monthly_income_adds_net_amount_within_active_window() {
        // MONTHLY 50,000 @ 10% tax → net = 540,000/year; only active between startYear..stopYear.
        final int currentYear = Year.now().getValue();
        final int startYear = currentYear + 2;  // age 40
        final int stopYear = currentYear + 4;   // age 42

        final RecurringIncome partTimeWork = new RecurringIncome();
        partTimeWork.setYear(startYear);
        partTimeWork.setStopYear(stopYear);
        partTimeWork.setFrequency(Frequency.MONTHLY);
        partTimeWork.setAmount(bd(50_000));
        partTimeWork.setTaxRatePct(bd(10));

        final RetirementInputs withIncome = inrDefaults();
        withIncome.setRecurringIncomes(List.of(partTimeWork));

        final List<ProjectionRow> base = RetirementCalculator.calculate(inrDefaults()).rows();
        final List<ProjectionRow> with = RetirementCalculator.calculate(withIncome).rows();

        final BigDecimal expectedNetAnnual = bd(50_000)
                .multiply(BigDecimal.valueOf(12))
                .multiply(new BigDecimal("0.90"), MathContext.DECIMAL64);

        for (int index = 0; index < base.size(); index++) {
            final int year = base.get(index).year();
            final BigDecimal delta = with.get(index).investment()
                    .subtract(base.get(index).investment()).abs();
            if (year >= startYear && year <= stopYear) {
                assertTrue(delta.subtract(expectedNetAnnual).abs()
                                .compareTo(BigDecimal.ONE) < 0,
                        "inside window: investment delta should be " + expectedNetAnnual + " at year " + year);
            } else if (!base.get(index).isPost()) {
                assertEquals(0, delta.compareTo(BigDecimal.ZERO),
                        "outside window: investment unchanged at year " + year);
            }
        }
    }

    @Test
    void recurring_yearly_income_is_not_multiplied_by_twelve() {
        // YEARLY 50,000 @ 0% tax → net delta = 50,000/year.
        // MONTHLY 50,000 @ 0% tax → net delta = 600,000/year.
        final int currentYear = Year.now().getValue();
        final int targetYear = currentYear + 1;

        final RetirementInputs withYearly = inrDefaults();
        final RecurringIncome yearly = new RecurringIncome();
        yearly.setYear(targetYear);
        yearly.setFrequency(Frequency.YEARLY);
        yearly.setAmount(bd(50_000));
        yearly.setTaxRatePct(bd(0));
        withYearly.setRecurringIncomes(List.of(yearly));

        final RetirementInputs withMonthly = inrDefaults();
        final RecurringIncome monthly = new RecurringIncome();
        monthly.setYear(targetYear);
        monthly.setFrequency(Frequency.MONTHLY);
        monthly.setAmount(bd(50_000));
        monthly.setTaxRatePct(bd(0));
        withMonthly.setRecurringIncomes(List.of(monthly));

        final ProjectionRow baseRow = rowForYear(RetirementCalculator.calculate(inrDefaults()), targetYear);
        final ProjectionRow yearlyRow = rowForYear(RetirementCalculator.calculate(withYearly), targetYear);
        final ProjectionRow monthlyRow = rowForYear(RetirementCalculator.calculate(withMonthly), targetYear);

        final BigDecimal yearlyDelta = yearlyRow.investment().subtract(baseRow.investment());
        assertTrue(yearlyDelta.subtract(bd(50_000)).abs().compareTo(BigDecimal.ONE) < 0,
                "YEARLY income should contribute 50,000; delta=" + yearlyDelta);

        final BigDecimal monthlyDelta = monthlyRow.investment().subtract(baseRow.investment());
        // Monthly contributes 12× the yearly amount.
        assertTrue(monthlyDelta.compareTo(yearlyDelta.multiply(BigDecimal.valueOf(10))) > 0,
                "MONTHLY income should be >> YEARLY; monthly=" + monthlyDelta + " yearly=" + yearlyDelta);
    }

    @Test
    void quarterly_and_half_yearly_income_annualise_by_periods_per_year() {
        // annualise(amount, f) = amount × (12 / monthsPerPeriod): QUARTERLY ×4,
        // HALF_YEARLY ×2, against the YEARLY ×1 baseline.
        final int currentYear = Year.now().getValue();
        final int targetYear = currentYear + 1;

        final ProjectionRow baseRow = rowForYear(RetirementCalculator.calculate(inrDefaults()), targetYear);

        final BigDecimal quarterlyDelta = incomeDeltaFor(Frequency.QUARTERLY, targetYear, baseRow);
        assertTrue(quarterlyDelta.subtract(bd(200_000)).abs().compareTo(BigDecimal.ONE) < 0,
                "QUARTERLY income should contribute 4×50,000; delta=" + quarterlyDelta);

        final BigDecimal halfYearlyDelta = incomeDeltaFor(Frequency.HALF_YEARLY, targetYear, baseRow);
        assertTrue(halfYearlyDelta.subtract(bd(100_000)).abs().compareTo(BigDecimal.ONE) < 0,
                "HALF_YEARLY income should contribute 2×50,000; delta=" + halfYearlyDelta);
    }

    private BigDecimal incomeDeltaFor(Frequency frequency, int targetYear, ProjectionRow baseRow) {
        final RetirementInputs inputs = inrDefaults();
        final RecurringIncome income = new RecurringIncome();
        income.setYear(targetYear);
        income.setFrequency(frequency);
        income.setAmount(bd(50_000));
        income.setTaxRatePct(bd(0));
        inputs.setRecurringIncomes(List.of(income));
        final ProjectionRow row = rowForYear(RetirementCalculator.calculate(inputs), targetYear);
        return row.investment().subtract(baseRow.investment());
    }

    @Test
    void recurring_income_not_active_before_start_year_or_after_stop_year() {
        final int currentYear = Year.now().getValue();
        final int startYear = currentYear + 3;
        final int stopYear = currentYear + 5;

        final RecurringIncome income = new RecurringIncome();
        income.setYear(startYear);
        income.setStopYear(stopYear);
        income.setFrequency(Frequency.MONTHLY);
        income.setAmount(bd(100_000));
        income.setTaxRatePct(bd(0));

        final RetirementInputs withIncome = inrDefaults();
        withIncome.setRecurringIncomes(List.of(income));

        final List<ProjectionRow> base = RetirementCalculator.calculate(inrDefaults()).rows();
        final List<ProjectionRow> with = RetirementCalculator.calculate(withIncome).rows();

        for (int index = 0; index < base.size(); index++) {
            final int year = base.get(index).year();
            if (year < startYear || year > stopYear) {
                assertEquals(0, with.get(index).investment()
                                .compareTo(base.get(index).investment()),
                        "investment must be unchanged outside active window at year " + year);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Recurring expense
    // -------------------------------------------------------------------------

    @Test
    void recurring_expense_active_window_adds_to_withdrawal() {
        // A pre-retirement window (ages 40-42) means baseline withdrawal is 0.
        // With the recurring expense, withdrawal should exceed 0 in that window.
        final int currentYear = Year.now().getValue();
        final int startYear = currentYear + 2;  // age 40
        final int stopYear = currentYear + 4;   // age 42

        final RecurringExpense schoolFees = new RecurringExpense();
        schoolFees.setYear(startYear);
        schoolFees.setStopYear(stopYear);
        schoolFees.setFrequency(Frequency.MONTHLY);
        schoolFees.setAmount(bd(30_000));
        schoolFees.setInflationPct(bd(5));  // custom rate

        final RetirementInputs withExpense = inrDefaults();
        withExpense.setRecurringExpenses(List.of(schoolFees));

        final List<ProjectionRow> base = RetirementCalculator.calculate(inrDefaults()).rows();
        final List<ProjectionRow> with = RetirementCalculator.calculate(withExpense).rows();

        for (int index = 0; index < base.size(); index++) {
            final int year = base.get(index).year();
            if (year >= startYear && year <= stopYear) {
                assertTrue(with.get(index).withdrawal()
                                .compareTo(base.get(index).withdrawal()) > 0,
                        "withdrawal should increase within window at year " + year);
            } else if (year < startYear && !base.get(index).isPost()) {
                assertEquals(0, with.get(index).withdrawal()
                                .compareTo(base.get(index).withdrawal()),
                        "withdrawal unchanged before window at year " + year);
            }
        }
    }

    @Test
    void recurring_expense_custom_inflation_differs_from_general_inflation() {
        // Same expense, different inflation rates → different withdrawal amounts
        // at a future year where compound differences are visible.
        final int currentYear = Year.now().getValue();
        final int targetYear = currentYear + 3;  // age 41, pre-retirement

        final RetirementInputs withCustom = inrDefaults();
        final RecurringExpense custom = new RecurringExpense();
        custom.setYear(targetYear);
        custom.setFrequency(Frequency.MONTHLY);
        custom.setAmount(bd(10_000));
        custom.setInflationPct(bd(4));  // 4% custom vs 8% general
        withCustom.setRecurringExpenses(List.of(custom));

        final RetirementInputs withGeneral = inrDefaults();
        final RecurringExpense general = new RecurringExpense();
        general.setYear(targetYear);
        general.setFrequency(Frequency.MONTHLY);
        general.setAmount(bd(10_000));
        general.setInflationPct(null);  // falls back to general 8%
        withGeneral.setRecurringExpenses(List.of(general));

        final ProjectionRow customRow = rowForYear(RetirementCalculator.calculate(withCustom), targetYear);
        final ProjectionRow generalRow = rowForYear(RetirementCalculator.calculate(withGeneral), targetYear);

        // General 8% > custom 4% → larger withdrawal.
        assertTrue(generalRow.withdrawal().compareTo(customRow.withdrawal()) > 0,
                "8% general inflation should produce higher withdrawal than 4% custom");
    }

    // -------------------------------------------------------------------------
    // Null / zero guard paths
    // -------------------------------------------------------------------------

    @Test
    void null_and_zero_retirement_benefits_are_silently_skipped() {
        // List with a null element, a zero-amount element, and one valid 500,000 entry at 0% tax.
        // Only the valid entry should affect the retirement year investment column.
        final RetirementBenefit zero = new RetirementBenefit();
        zero.setAmount(BigDecimal.ZERO);
        zero.setTaxRatePct(BigDecimal.ZERO);

        final RetirementBenefit valid = new RetirementBenefit();
        valid.setAmount(bd(500_000));
        valid.setTaxRatePct(BigDecimal.ZERO);

        final List<RetirementBenefit> benefits = new ArrayList<>();
        benefits.add(null);      // null element
        benefits.add(zero);      // zero-amount element
        benefits.add(valid);     // only this should contribute

        final RetirementInputs withBenefits = inrDefaults();
        withBenefits.setRetirementBenefits(benefits);

        final ProjectionRow baseRetire = retirementRow(RetirementCalculator.calculate(inrDefaults()));
        final ProjectionRow withRetire = retirementRow(RetirementCalculator.calculate(withBenefits));

        final BigDecimal delta = withRetire.investment().subtract(baseRetire.investment());
        assertTrue(delta.subtract(bd(500_000)).abs().compareTo(BigDecimal.ONE) < 0,
                "only valid benefit should contribute; null and zero silently skipped; delta=" + delta);
    }

    // -------------------------------------------------------------------------
    // Corpus depletion
    // -------------------------------------------------------------------------

    @Test
    void corpus_depletion_terminates_projection_and_sets_depletion_age() {
        // Tiny corpus (1,000) with large monthly expenses guarantees depletion at retirement.
        final var inputs = new RetirementInputs();
        inputs.setCurrentAge(35);
        inputs.setRetireAge(36);
        inputs.setLifeExp(40);
        inputs.setCorpus(bd(1_000));
        inputs.setMonthlyExpenses(bd(100_000));
        inputs.setInflationPct(bd(0));
        inputs.setGrowthPrePct(bd(0));
        inputs.setGrowthPostPct(bd(0));
        inputs.setCorpusTaxRatePct(bd(0));
        inputs.setMonthlyInvPre(bd(0));
        inputs.setSipGrowthPrePct(bd(0));
        inputs.setSipStepUpPrePct(bd(0));
        inputs.setTaxRatePrePct(bd(0));
        inputs.setMonthlyInvPost(bd(0));
        inputs.setSipGrowthPostPct(bd(0));
        inputs.setSipStepUpPostPct(bd(0));
        inputs.setTaxRatePostPct(bd(0));

        final RetirementResult result = RetirementCalculator.calculate(inputs);

        assertTrue(result.corpusDepletedAt().isPresent(), "corpus depletion must be flagged");
        assertEquals(36, result.corpusDepletedAt().get(),
                "must deplete at retirement (age 36)");
        // Loop breaks at depletion: 2 rows for ages 35 and 36.
        assertEquals(2, result.rows().size(), "loop must break immediately after depletion");
        assertTrue(result.rows().get(1).endCorpus().signum() < 0,
                "depletion row must have negative end corpus");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static ProjectionRow rowForYear(RetirementResult result, int year) {
        return result.rows().stream().filter(row -> row.year() == year)
                .findFirst().orElseThrow();
    }

    private static ProjectionRow retirementRow(RetirementResult result) {
        return result.rows().stream().filter(ProjectionRow::isRetireYear)
                .findFirst().orElseThrow();
    }

    private static BigDecimal bd(long n) {
        return BigDecimal.valueOf(n);
    }
}
