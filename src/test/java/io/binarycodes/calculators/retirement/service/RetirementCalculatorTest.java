package io.binarycodes.calculators.retirement.service;

import io.binarycodes.calculators.retirement.domain.FutureExpense;
import io.binarycodes.calculators.retirement.domain.ProjectionRow;
import io.binarycodes.calculators.retirement.domain.RetirementInputs;
import io.binarycodes.calculators.retirement.domain.RetirementResult;

import java.time.Year;
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
        inputs.setMonthlyInvPre(bd(150_000));
        inputs.setSipGrowthPrePct(bd(12));
        inputs.setSipStepUpPrePct(bd(0));
        inputs.setMonthlyInvPost(bd(0));
        inputs.setSipGrowthPostPct(bd(0));
        inputs.setSipStepUpPostPct(bd(0));
        inputs.setTaxRatePct(bd(0));
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
        inputs.setMonthlyInvPre(bd(1));
        inputs.setSipGrowthPrePct(bd(1));
        inputs.setSipStepUpPrePct(bd(1));
        inputs.setMonthlyInvPost(bd(1));
        inputs.setSipGrowthPostPct(bd(1));
        inputs.setSipStepUpPostPct(bd(1));
        inputs.setTaxRatePct(bd(1));
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
    void rejects_currentAge_ge_retireAge() {
        assertThrows(IllegalArgumentException.class,
                () -> RetirementCalculator.calculate(allOnes(50, 50, 90)));
    }

    @Test
    void rejects_retireAge_ge_lifeExp() {
        assertThrows(IllegalArgumentException.class,
                () -> RetirementCalculator.calculate(allOnes(35, 90, 90)));
    }

    private static BigDecimal bd(long n) {
        return BigDecimal.valueOf(n);
    }
}
