package io.binarycodes.calculators.retirement.ui;

import io.binarycodes.calculators.retirement.domain.ProjectionRow;
import io.binarycodes.calculators.retirement.domain.RetirementResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The "Investments at retirement" donut splits the corpus at retirement into
 * principal contributed vs. interest earned. The retirement-year contribution
 * is principal — it must not be counted as interest just because the corpus
 * snapshot is taken after that contribution lands.
 */
class InvestmentsChartTest {

    @Test
    void retirement_year_contribution_counts_as_principal_not_interest() {
        // Principal contributed before the retirement year = 1,000,000;
        // the retirement-year contribution = 200,000; corpus at retirement = 1,500,000.
        // Correct split: principal 1,200,000, interest 300,000 (NOT 500,000).
        final ProjectionRow retireRow = new ProjectionRow(
                2030, 45, true, true,
                BigDecimal.ZERO,                  // annualExp
                new BigDecimal("1500000"),        // startCorpus (after this year's contribution)
                BigDecimal.ZERO,                  // returns
                new BigDecimal("200000"),         // investment (retirement-year contribution)
                BigDecimal.ZERO,                  // withdrawal
                BigDecimal.ZERO,                  // taxPaid
                new BigDecimal("1500000"),        // endCorpus
                false);
        final RetirementResult result = new RetirementResult(
                List.of(retireRow), Optional.empty(), new BigDecimal("1000000"));

        final InvestmentsChart.Breakdown breakdown = InvestmentsChart.breakdown(result);

        assertEquals(0, breakdown.principal().compareTo(new BigDecimal("1200000")),
                "principal must include the retirement-year contribution");
        assertEquals(0, breakdown.interest().compareTo(new BigDecimal("300000")),
                "interest must exclude the retirement-year contribution");
        assertEquals(0, breakdown.total().compareTo(new BigDecimal("1500000")));
    }
}
