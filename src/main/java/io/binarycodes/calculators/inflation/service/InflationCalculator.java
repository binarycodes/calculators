package io.binarycodes.calculators.inflation.service;

import io.binarycodes.calculators.base.common.TimeHorizon;
import io.binarycodes.calculators.base.math.Rates;
import io.binarycodes.calculators.inflation.domain.InflationInputs;
import io.binarycodes.calculators.inflation.domain.InflationPoint;
import io.binarycodes.calculators.inflation.domain.InflationResult;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

/**
 * Projects an amount across a horizon at a fixed inflation rate, in either
 * direction:
 *
 * <ul>
 *   <li><b>Forward</b> (amount is today's money): {@code result = amount · (1+i)^years}.</li>
 *   <li><b>Backward</b> (amount is a future value): {@code result = amount / (1+i)^years}.</li>
 * </ul>
 *
 * <p>{@code years} is the fractional horizon ({@code totalMonths / 12}), so a
 * 2-year-6-month horizon compounds over 2.5 years.</p>
 */
public final class InflationCalculator {

    private static final MathContext MC = Rates.CONTEXT;

    private InflationCalculator() {
    }

    public static InflationResult calculate(InflationInputs inputs) {
        final int totalMonths = TimeHorizon.resolveTotalMonths(
                inputs.getHorizonMode(),
                inputs.getYearsToGoal(), inputs.getMonthsToGoal(),
                inputs.getCurrentAge(), inputs.getGoalAge(),
                inputs.getTargetYear(), inputs.getTargetMonth());
        if (totalMonths < 1) {
            throw new IllegalArgumentException("Time horizon must be at least one month.");
        }
        final BigDecimal amount = required(inputs.getAmount(), "Amount");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Amount must be non-negative.");
        }
        final BigDecimal inflation = Rates.pctToFraction(inputs.getInflationRatePct());

        final BigDecimal growthFactor = BigDecimal.valueOf(
                Math.pow(1.0 + inflation.doubleValue(), totalMonths / 12.0));

        final BigDecimal resultAmount = inputs.isAmountIsToday()
                ? amount.multiply(growthFactor, MC)
                : amount.divide(growthFactor, MC);

        // Anchor the curve at today's value (the smaller of the two ends) and
        // let it grow with inflation across the horizon, so both directions
        // render the same rising trajectory.
        final BigDecimal todayValue = inputs.isAmountIsToday() ? amount : resultAmount;
        final List<InflationPoint> progression = buildProgression(todayValue, inflation, totalMonths);

        return new InflationResult(totalMonths, amount, resultAmount,
                inputs.isAmountIsToday(), progression);
    }

    private static List<InflationPoint> buildProgression(BigDecimal todayValue,
                                                         BigDecimal inflation, int totalMonths) {
        final int currentYear = Year.now().getValue();
        final int wholeYears = totalMonths / 12;
        final List<InflationPoint> points = new ArrayList<>();
        for (int yearOffset = 0; yearOffset <= wholeYears; yearOffset++) {
            points.add(new InflationPoint(currentYear + yearOffset,
                    todayValue.multiply(Rates.pow1plus(inflation, yearOffset), MC)));
        }
        // A non-whole-year horizon gets a final fractional point at the exact end.
        if (totalMonths % 12 != 0) {
            final BigDecimal endFactor = BigDecimal.valueOf(
                    Math.pow(1.0 + inflation.doubleValue(), totalMonths / 12.0));
            points.add(new InflationPoint(currentYear + wholeYears + 1,
                    todayValue.multiply(endFactor, MC)));
        }
        return points;
    }

    private static BigDecimal required(BigDecimal value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required.");
        }
        return value;
    }
}
