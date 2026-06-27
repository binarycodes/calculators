package io.binarycodes.calculators.irr.service;

import io.binarycodes.calculators.irr.domain.Cashflow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XirrTest {

    private static final double TOLERANCE = 1e-4;

    @Test
    void npv_is_zero_at_the_internal_rate_of_return() {
        // -1000 today, +1100 exactly one (365-day) year later → 10%.
        final List<Cashflow> cashflows = List.of(
                flow("2021-01-01", -1000),
                flow("2022-01-01", 1100));

        assertEquals(0.0, Xirr.npv(cashflows, 0.10), 1e-6);
    }

    @Test
    void single_sign_change_yields_one_root() {
        final List<Cashflow> cashflows = List.of(
                flow("2021-01-01", -1000),
                flow("2022-01-01", 1100));

        final List<BigDecimal> roots = Xirr.roots(cashflows);

        assertEquals(1, roots.size());
        assertEquals(0.10, roots.get(0).doubleValue(), TOLERANCE);
    }

    @Test
    void sign_changes_counts_flips_in_date_order_ignoring_order_of_input() {
        final List<Cashflow> outOfOrder = List.of(
                flow("2022-01-01", -1320),
                flow("2020-01-01", -1000),
                flow("2021-01-01", 2300));

        assertEquals(2, Xirr.signChanges(outOfOrder));
    }

    @Test
    void same_sign_cashflows_have_no_sign_change_and_no_root() {
        final List<Cashflow> cashflows = List.of(
                flow("2021-01-01", -1000),
                flow("2022-01-01", -500));

        assertEquals(0, Xirr.signChanges(cashflows));
        assertTrue(Xirr.roots(cashflows).isEmpty());
    }

    @Test
    void non_conventional_cashflows_expose_multiple_roots() {
        // -1, +2.3, -1.32 (a year apart) is the classic two-IRR schedule: ~10% and ~20%.
        final List<Cashflow> cashflows = List.of(
                flow("2020-01-01", -1000),
                flow("2021-01-01", 2300),
                flow("2022-01-01", -1320));

        final List<BigDecimal> roots = Xirr.roots(cashflows);

        assertEquals(2, roots.size());
        assertTrue(roots.get(0).doubleValue() < roots.get(1).doubleValue(), "roots ascending");
        assertTrue(roots.get(0).doubleValue() > 0.08 && roots.get(0).doubleValue() < 0.12);
        assertTrue(roots.get(1).doubleValue() > 0.18 && roots.get(1).doubleValue() < 0.22);
    }

    private static Cashflow flow(String date, long amount) {
        return new Cashflow(LocalDate.parse(date), BigDecimal.valueOf(amount), null);
    }
}
