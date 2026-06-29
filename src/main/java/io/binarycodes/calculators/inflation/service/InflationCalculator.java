package io.binarycodes.calculators.inflation.service;

import io.binarycodes.calculators.base.common.TimeHorizon;
import io.binarycodes.calculators.base.math.Rates;
import io.binarycodes.calculators.inflation.domain.InflationBand;
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

        final BigDecimal variation = Rates.pctToFraction(inputs.getInflationVariationPct());
        final List<InflationBand> band = buildBand(amount, inputs.isAmountIsToday(), todayValue,
                inflation.subtract(variation), inflation.add(variation), totalMonths);

        return new InflationResult(totalMonths, amount, resultAmount,
                inputs.isAmountIsToday(), progression, band);
    }

    /**
     * Year-by-year low/high values for the uncertainty band, evaluated at the low
     * and high inflation rates so each point brackets the central value. Aligned
     * 1:1 with the central progression (same years, including any fractional final
     * point). Which end is fixed depends on the direction:
     *
     * <ul>
     *   <li><b>Forward</b>: the entered amount is today's money, so the band starts
     *       as a point and fans out toward the horizon as the rate compounds.</li>
     *   <li><b>Backward</b>: the entered amount is the fixed value at the horizon
     *       end, so the band converges there and fans out toward today — a higher
     *       inflation rate discounts to a smaller present value, forming the lower
     *       edge.</li>
     * </ul>
     */
    private static List<InflationBand> buildBand(BigDecimal amount, boolean amountIsToday,
                                                 BigDecimal todayValue, BigDecimal lowRate,
                                                 BigDecimal highRate, int totalMonths) {
        final int currentYear = Year.now().getValue();
        final int wholeYears = totalMonths / 12;
        final double totalYears = totalMonths / 12.0;
        final List<InflationBand> points = new ArrayList<>();

        if (amountIsToday) {
            for (int yearOffset = 0; yearOffset <= wholeYears; yearOffset++) {
                points.add(new InflationBand(currentYear + yearOffset,
                        todayValue.multiply(Rates.pow1plus(lowRate, yearOffset), MC),
                        todayValue.multiply(Rates.pow1plus(highRate, yearOffset), MC)));
            }
            if (totalMonths % 12 != 0) {
                points.add(new InflationBand(currentYear + wholeYears + 1,
                        todayValue.multiply(powYears(lowRate, totalYears), MC),
                        todayValue.multiply(powYears(highRate, totalYears), MC)));
            }
            return points;
        }

        for (int yearOffset = 0; yearOffset <= wholeYears; yearOffset++) {
            points.add(discountedBand(amount, lowRate, highRate,
                    currentYear + yearOffset, yearOffset - totalYears));
        }
        if (totalMonths % 12 != 0) {
            points.add(discountedBand(amount, lowRate, highRate, currentYear + wholeYears + 1, 0.0));
        }
        return points;
    }

    /**
     * Backward-mode band point: the fixed future {@code amount} discounted back by
     * {@code yearsFromEnd} (≤ 0) at each rate. The higher rate discounts more, so it
     * yields the lower edge; both edges meet the amount at the end ({@code yearsFromEnd == 0}).
     */
    private static InflationBand discountedBand(BigDecimal amount, BigDecimal lowRate,
                                                BigDecimal highRate, int year, double yearsFromEnd) {
        final BigDecimal atLowRate = amount.multiply(powYears(lowRate, yearsFromEnd), MC);
        final BigDecimal atHighRate = amount.multiply(powYears(highRate, yearsFromEnd), MC);
        return new InflationBand(year, atHighRate, atLowRate);
    }

    private static BigDecimal powYears(BigDecimal rate, double years) {
        return BigDecimal.valueOf(Math.pow(1.0 + rate.doubleValue(), years));
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
