package io.binarycodes.calculators.irr.service;

import io.binarycodes.calculators.irr.domain.CashflowFrequency;
import io.binarycodes.calculators.irr.domain.DatedCashflow;
import io.binarycodes.calculators.irr.domain.NpvPoint;
import io.binarycodes.calculators.irr.domain.RecurringCashflow;
import io.binarycodes.calculators.irr.domain.XirrInputs;
import io.binarycodes.calculators.irr.domain.XirrResult;
import io.binarycodes.calculators.irr.domain.XirrStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XirrCalculatorTest {

    private static final double TOLERANCE = 1e-4;

    @Test
    void a_simple_invest_then_withdraw_resolves_to_a_unique_rate() {
        final XirrInputs inputs = new XirrInputs();
        inputs.setOneOffInvestments(List.of(oneOff("2021-01-01", 1000)));
        inputs.setOneOffWithdrawals(List.of(oneOff("2022-01-01", 1100)));

        final XirrResult result = XirrCalculator.calculate(inputs);

        assertEquals(XirrStatus.UNIQUE, result.status());
        assertEquals(1, result.roots().size());
        assertEquals(0.10, result.xirr().doubleValue(), TOLERANCE);
        assertEquals(0, result.totalInvested().compareTo(BigDecimal.valueOf(1000)));
        assertEquals(0, result.totalWithdrawn().compareTo(BigDecimal.valueOf(1100)));
        assertEquals(0, result.netCashflow().compareTo(BigDecimal.valueOf(100)));
    }

    @Test
    void recurring_investments_expand_into_one_cashflow_per_occurrence() {
        final XirrInputs inputs = new XirrInputs();
        inputs.setRecurringInvestments(List.of(
                recurring("2020-01-01", CashflowFrequency.MONTHLY, 12, 100)));
        inputs.setOneOffWithdrawals(List.of(oneOff("2021-01-01", 1300)));

        final XirrResult result = XirrCalculator.calculate(inputs);

        // 12 monthly premiums + one redemption.
        assertEquals(13, result.cashflows().size());
        assertEquals(0, result.totalInvested().compareTo(BigDecimal.valueOf(1200)));
        assertEquals(XirrStatus.UNIQUE, result.status());
        assertTrue(result.xirr().doubleValue() > 0, "a profitable schedule has a positive rate");
    }

    @Test
    void a_non_conventional_schedule_is_flagged_non_unique_with_every_root() {
        final XirrInputs inputs = new XirrInputs();
        inputs.setOneOffInvestments(List.of(
                oneOff("2020-01-01", 1000),
                oneOff("2022-01-01", 1320)));
        inputs.setOneOffWithdrawals(List.of(oneOff("2021-01-01", 2300)));

        final XirrResult result = XirrCalculator.calculate(inputs);

        assertEquals(XirrStatus.NON_UNIQUE, result.status());
        assertEquals(2, result.roots().size());
        assertEquals(2, result.signChanges());
    }

    @Test
    void payback_date_is_when_the_running_total_first_turns_non_negative() {
        final XirrInputs inputs = new XirrInputs();
        inputs.setOneOffInvestments(List.of(oneOff("2020-01-01", 1000)));
        inputs.setOneOffWithdrawals(List.of(
                oneOff("2020-07-01", 400),
                oneOff("2021-01-01", 800)));

        final XirrResult result = XirrCalculator.calculate(inputs);

        assertTrue(result.paybackDate().isPresent());
        assertEquals(LocalDate.parse("2021-01-01"), result.paybackDate().get());
    }

    @Test
    void fewer_than_two_cashflows_is_rejected() {
        final XirrInputs inputs = new XirrInputs();
        inputs.setOneOffInvestments(List.of(oneOff("2020-01-01", 1000)));

        assertThrows(IllegalArgumentException.class, () -> XirrCalculator.calculate(inputs));
    }

    @Test
    void one_directional_cashflows_cannot_break_even_and_are_rejected() {
        final XirrInputs inputs = new XirrInputs();
        inputs.setOneOffInvestments(List.of(
                oneOff("2020-01-01", 1000),
                oneOff("2021-01-01", 500)));

        assertThrows(IllegalArgumentException.class, () -> XirrCalculator.calculate(inputs));
    }

    @Test
    void blank_rows_are_ignored_rather_than_failing() {
        final XirrInputs inputs = new XirrInputs();
        final List<DatedCashflow> investments = new ArrayList<>();
        investments.add(oneOff("2021-01-01", 1000));
        investments.add(new DatedCashflow());
        inputs.setOneOffInvestments(investments);
        inputs.setOneOffWithdrawals(List.of(oneOff("2022-01-01", 1100)));

        final XirrResult result = XirrCalculator.calculate(inputs);

        assertEquals(2, result.cashflows().size());
        assertFalse(result.roots().isEmpty());
    }

    @Test
    void npv_curve_spans_a_root_beyond_the_old_high_cap() {
        // -1, +6.05, -5.25 (scaled ×100) has roots near 5% and 400%.
        final XirrInputs inputs = new XirrInputs();
        inputs.setOneOffInvestments(List.of(oneOff("2020-01-01", 100), oneOff("2022-01-01", 525)));
        inputs.setOneOffWithdrawals(List.of(oneOff("2021-01-01", 605)));

        final XirrResult result = XirrCalculator.calculate(inputs);

        final BigDecimal maxRoot = result.roots().get(result.roots().size() - 1);
        assertTrue(maxRoot.doubleValue() > 3.0,
                "scenario must exercise a root beyond the old 300% cap; got " + maxRoot);
        assertCurveSpansEveryRoot(result);
    }

    @Test
    void npv_curve_spans_a_deeply_negative_root() {
        // -1000 then +1 has a single root near -99.9%, left of the old -90% edge.
        final XirrInputs inputs = new XirrInputs();
        inputs.setOneOffInvestments(List.of(oneOff("2020-01-01", 1000)));
        inputs.setOneOffWithdrawals(List.of(oneOff("2021-01-01", 1)));

        final XirrResult result = XirrCalculator.calculate(inputs);

        assertTrue(result.roots().get(0).doubleValue() < -0.9,
                "scenario must exercise a root left of the old -90% edge; got " + result.roots().get(0));
        assertCurveSpansEveryRoot(result);
    }

    private static void assertCurveSpansEveryRoot(XirrResult result) {
        final List<NpvPoint> curve = result.npvCurve();
        final double low = curve.get(0).rate().doubleValue();
        final double high = curve.get(curve.size() - 1).rate().doubleValue();
        for (final BigDecimal root : result.roots()) {
            final double rate = root.doubleValue();
            assertTrue(rate >= low && rate <= high,
                    "every root must lie within the plotted curve range [" + low + ", " + high + "]; root " + rate);
        }
    }

    private static DatedCashflow oneOff(String date, long amount) {
        return new DatedCashflow(LocalDate.parse(date), null, BigDecimal.valueOf(amount));
    }

    private static RecurringCashflow recurring(String startDate, CashflowFrequency frequency, int count, long amount) {
        return new RecurringCashflow(LocalDate.parse(startDate), frequency, count, null, BigDecimal.valueOf(amount));
    }
}
