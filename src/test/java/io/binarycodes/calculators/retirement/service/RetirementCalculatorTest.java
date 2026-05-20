package io.binarycodes.calculators.retirement.service;

import io.binarycodes.calculators.retirement.domain.ProjectionRow;
import io.binarycodes.calculators.retirement.domain.RetirementInputs;
import io.binarycodes.calculators.retirement.domain.RetirementResult;
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
        return new RetirementInputs(
                38, 45, 90,
                bd(15_000_000), bd(100_000),
                bd(8),
                bd(12), bd(8),
                bd(150_000), bd(12),
                bd(0), bd(0));
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
    void rejects_currentAge_ge_retireAge() {
        final var bad = new RetirementInputs(50, 50, 90,
                bd(1), bd(1), bd(1), bd(1), bd(1), bd(1), bd(1), bd(1), bd(1));
        assertThrows(IllegalArgumentException.class, () -> RetirementCalculator.calculate(bad));
    }

    @Test
    void rejects_retireAge_ge_lifeExp() {
        final var bad = new RetirementInputs(35, 90, 90,
                bd(1), bd(1), bd(1), bd(1), bd(1), bd(1), bd(1), bd(1), bd(1));
        assertThrows(IllegalArgumentException.class, () -> RetirementCalculator.calculate(bad));
    }

    private static BigDecimal bd(long n) {
        return BigDecimal.valueOf(n);
    }
}
