package io.binarycodes.calculators.loan.service;

import io.binarycodes.calculators.loan.domain.LoanInputs;
import io.binarycodes.calculators.loan.domain.LoanResult;
import io.binarycodes.calculators.loan.domain.LoanYear;
import io.binarycodes.calculators.loan.domain.PrepaymentFrequency;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoanCalculatorTest {

    private static LoanInputs loan(String amount, String ratePct, int years, int months) {
        final var inputs = new LoanInputs();
        inputs.setLoanAmount(new BigDecimal(amount));
        inputs.setAnnualRatePct(new BigDecimal(ratePct));
        inputs.setTenureYears(years);
        inputs.setTenureMonths(months);
        inputs.setInflationRatePct(BigDecimal.ZERO);
        return inputs;
    }

    @Test
    void emi_matches_standard_reducing_balance_formula() {
        // P=1,000,000, 12% annual (1%/month), 12 months → EMI ≈ 88,848.79.
        final LoanResult result = LoanCalculator.calculate(loan("1000000", "12", 1, 0));
        assertEquals(88_848.79, result.emi().doubleValue(), 0.01);
        assertEquals(12, result.baseMonths());
    }

    @Test
    void zero_interest_splits_principal_evenly() {
        final LoanResult result = LoanCalculator.calculate(loan("120000", "0", 2, 0));
        assertEquals(0, result.emi().compareTo(new BigDecimal("5000.00")));
        assertEquals(0, result.totalInterestBaseline().compareTo(BigDecimal.ZERO));
        assertEquals(0, result.totalPaymentBaseline().compareTo(new BigDecimal("120000.00")));
    }

    @Test
    void schedule_clears_to_zero_balance() {
        final LoanResult result = LoanCalculator.calculate(loan("2500000", "8.5", 20, 0));
        final LoanYear last = result.rows().get(result.rows().size() - 1);
        assertEquals(0, last.endBalance().compareTo(BigDecimal.ZERO));
        // Total payment = principal + interest, and interest is meaningful.
        assertTrue(result.totalInterestBaseline().signum() > 0);
    }

    @Test
    void no_prepayments_collapses_all_scenarios() {
        final LoanResult result = LoanCalculator.calculate(loan("2500000", "8.5", 20, 0));
        assertEquals(false, result.hasPrepayments());
        assertEquals(0, result.interestSavedTenure().compareTo(BigDecimal.ZERO));
        assertEquals(0, result.interestSavedEmi().compareTo(BigDecimal.ZERO));
        assertEquals(0, result.monthsSaved());
        assertEquals(result.baseMonths(), result.reducedMonths());
        // Reduce-EMI with no prepayment keeps the original EMI.
        assertEquals(0, result.finalEmiReduceEmi().compareTo(result.emi()));
    }

    @Test
    void recurring_prepayment_shortens_tenure_and_saves_interest() {
        final LoanInputs inputs = loan("2500000", "8.5", 20, 0);
        inputs.setExtraPerPeriod(new BigDecimal("100000")); // 1 lakh extra
        inputs.setExtraFrequency(PrepaymentFrequency.YEARLY);
        final LoanResult result = LoanCalculator.calculate(inputs);

        assertTrue(result.hasPrepayments());
        assertTrue(result.reducedMonths() < result.baseMonths(), "loan should finish early");
        assertTrue(result.monthsSaved() > 0);
        assertTrue(result.interestSavedTenure().signum() > 0, "should save interest");
        // Reduce-tenure interest is below baseline.
        assertTrue(result.totalInterestReduceTenure().compareTo(result.totalInterestBaseline()) < 0);
    }

    @Test
    void reduce_emi_lowers_the_installment_and_keeps_tenure() {
        final LoanInputs inputs = loan("2500000", "8.5", 20, 0);
        inputs.setExtraPerPeriod(new BigDecimal("100000"));
        inputs.setExtraFrequency(PrepaymentFrequency.YEARLY);
        final LoanResult result = LoanCalculator.calculate(inputs);

        // The re-amortized EMI ends lower than the original, and still saves interest
        // (though less than reduce-tenure).
        assertTrue(result.finalEmiReduceEmi().compareTo(result.emi()) < 0, "EMI should drop");
        assertTrue(result.interestSavedEmi().signum() > 0);
        assertTrue(result.interestSavedEmi().compareTo(result.interestSavedTenure()) < 0,
                "reduce-EMI saves less than reduce-tenure");
    }

    @Test
    void extra_emis_per_year_save_interest() {
        final LoanInputs inputs = loan("2500000", "8.5", 20, 0);
        inputs.setExtraEmisPerYear(1); // pay 13 EMIs a year
        final LoanResult result = LoanCalculator.calculate(inputs);
        assertTrue(result.interestSavedTenure().signum() > 0);
        assertTrue(result.reducedMonths() < result.baseMonths());
    }

    @Test
    void emi_step_up_shortens_tenure_but_not_reduce_emi() {
        final LoanInputs inputs = loan("2500000", "8.5", 20, 0);
        inputs.setEmiStepUpPct(new BigDecimal("10")); // raise EMI 10% a year
        final LoanResult result = LoanCalculator.calculate(inputs);
        assertTrue(result.hasPrepayments());
        assertTrue(result.reducedMonths() < result.baseMonths(), "step-up finishes earlier");
        // Step-up is a pay-more lever, so the reduce-EMI scenario is unaffected.
        assertEquals(0, result.interestSavedEmi().compareTo(BigDecimal.ZERO));
        assertEquals(0, result.finalEmiReduceEmi().compareTo(result.emi()));
    }

    @Test
    void combined_levers_compound_their_savings() {
        final LoanInputs inputs = loan("2500000", "8.5", 20, 0);
        inputs.setExtraPerPeriod(new BigDecimal("100000"));
        inputs.setExtraFrequency(PrepaymentFrequency.YEARLY);
        inputs.setExtraEmisPerYear(1);
        inputs.setEmiStepUpPct(new BigDecimal("10"));
        final LoanResult result = LoanCalculator.calculate(inputs);

        assertTrue(result.hasPrepayments());
        assertTrue(result.reducedMonths() < result.baseMonths());
        assertTrue(result.interestSavedTenure().signum() > 0);

        final LoanInputs extraOnly = loan("2500000", "8.5", 20, 0);
        extraOnly.setExtraPerPeriod(new BigDecimal("100000"));
        extraOnly.setExtraFrequency(PrepaymentFrequency.YEARLY);
        final LoanResult extraOnlyResult = LoanCalculator.calculate(extraOnly);
        assertTrue(result.reducedMonths() < extraOnlyResult.reducedMonths(),
                "all levers together finish earlier than the recurring extra alone");
        assertTrue(result.interestSavedTenure().compareTo(extraOnlyResult.interestSavedTenure()) > 0,
                "all levers together save more interest");
    }

    @Test
    void reduce_emi_schedule_clears_to_zero_balance() {
        final LoanInputs inputs = loan("2500000", "8.5", 20, 0);
        inputs.setExtraPerPeriod(new BigDecimal("100000"));
        inputs.setExtraFrequency(PrepaymentFrequency.YEARLY);
        final LoanResult result = LoanCalculator.calculate(inputs);

        final LoanYear last = result.reduceEmiRows().get(result.reduceEmiRows().size() - 1);
        assertEquals(0, last.endBalance().compareTo(BigDecimal.ZERO));
        // Re-amortizing only lowers the EMI; the recurring prepayments still reduce
        // principal, so the schedule never runs past the original tenure.
        final int reduceEmiMonths = result.reduceEmiRows().stream()
                .mapToInt(LoanYear::monthsInPeriod).sum();
        assertTrue(reduceEmiMonths <= result.baseMonths());
    }

    @Test
    void real_interest_is_below_nominal_under_inflation() {
        final LoanInputs inputs = loan("2500000", "8.5", 20, 0);
        inputs.setInflationRatePct(new BigDecimal("6"));
        final LoanResult result = LoanCalculator.calculate(inputs);
        assertTrue(result.realTotalInterest().compareTo(result.totalInterestReduceTenure()) < 0,
                "interest in today's money should be less than nominal");
    }

    @Test
    void rejects_non_positive_amount_and_zero_tenure() {
        assertThrows(IllegalArgumentException.class,
                () -> LoanCalculator.calculate(loan("0", "8.5", 20, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> LoanCalculator.calculate(loan("2500000", "8.5", 0, 0)));
    }
}
