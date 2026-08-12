package io.binarycodes.calculators.investment.service;

import io.binarycodes.calculators.base.common.TimeHorizon;
import io.binarycodes.calculators.base.math.Rates;
import io.binarycodes.calculators.base.common.Frequency;
import io.binarycodes.calculators.investment.domain.InvestmentInputs;
import io.binarycodes.calculators.investment.domain.InvestmentResult;
import io.binarycodes.calculators.investment.domain.InvestmentYear;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

/**
 * Projects a stream of contributions through an investment phase and a
 * subsequent hold phase, both compounding monthly.
 *
 * <ul>
 *   <li><b>Investment phase</b> — each period the user contributes {@code amount}
 *       (monthly or yearly), ramped by an annual step-up. Yearly contributions
 *       land at the start of each 12-month block.</li>
 *   <li><b>Hold phase</b> — no contributions; the corpus keeps compounding.</li>
 * </ul>
 *
 * <p>Tax applies to the gains portion only, once, at the end. The inflation rate
 * deflates the net maturity value to today's purchasing power, and each
 * projection year carries its balance deflated to today as well.</p>
 */
public final class InvestmentCalculator {

    private static final MathContext MC = Rates.CONTEXT;

    private InvestmentCalculator() {
    }

    public static InvestmentResult calculate(InvestmentInputs inputs) {
        final int investmentMonths = TimeHorizon.resolveTotalMonths(
                inputs.getHorizonMode(),
                inputs.getInvestYears(), inputs.getInvestMonths(),
                inputs.getCurrentAge(), inputs.getGoalAge(),
                inputs.getTargetYear(), inputs.getTargetMonth());
        if (investmentMonths < 1) {
            throw new IllegalArgumentException("Investment time must be at least one month.");
        }
        final int holdMonths = holdMonths(inputs);
        if (holdMonths < 0) {
            throw new IllegalArgumentException("Hold time cannot be negative.");
        }
        final int totalMonths = investmentMonths + holdMonths;

        final BigDecimal amount = required(inputs.getAmount(), "Amount");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Amount must be non-negative.");
        }
        final Frequency frequency = inputs.getFrequency() == null
                ? Frequency.MONTHLY
                : inputs.getFrequency();

        final BigDecimal monthlyGrowth = Rates.monthlyFromAnnual(Rates.pctToFraction(inputs.getGrowthRatePct()));
        final BigDecimal taxRate = Rates.pctToFraction(inputs.getTaxRatePct());
        final BigDecimal inflation = Rates.pctToFraction(inputs.getInflationRatePct());
        final BigDecimal stepUp = Rates.pctToFraction(inputs.getStepUpPct());

        final int currentYear = Year.now().getValue();
        final List<InvestmentYear> rows = new ArrayList<>();

        BigDecimal balance = BigDecimal.ZERO;
        BigDecimal principal = BigDecimal.ZERO;
        BigDecimal yearContribution = BigDecimal.ZERO;
        int monthsInYear = 0;
        int yearIndex = 0;

        for (int monthIndex = 0; monthIndex < totalMonths; monthIndex++) {
            final boolean investing = monthIndex < investmentMonths;
            if (investing) {
                final BigDecimal stepFactor = Rates.pow1plus(stepUp, monthIndex / 12);
                // A contribution lands at the start of each period: every month
                // when MONTHLY, every 3rd/6th/12th month for the wider cadences.
                final boolean contributeThisMonth = monthIndex % frequency.monthsPerPeriod() == 0;
                if (contributeThisMonth) {
                    final BigDecimal contribution = amount.multiply(stepFactor, MC);
                    balance = balance.add(contribution, MC);
                    principal = principal.add(contribution, MC);
                    yearContribution = yearContribution.add(contribution, MC);
                }
            }
            balance = balance.add(balance.multiply(monthlyGrowth, MC), MC);
            monthsInYear++;

            final boolean yearBoundary = monthsInYear == 12;
            final boolean horizonEnd = monthIndex == totalMonths - 1;
            if (yearBoundary || horizonEnd) {
                final int elapsedMonths = monthIndex + 1;
                final BigDecimal gains = balance.subtract(principal, MC);
                final BigDecimal deflator = BigDecimal.valueOf(
                        Math.pow(1.0 + inflation.doubleValue(), elapsedMonths / 12.0));
                final BigDecimal realValue = balance.divide(deflator, MC);
                final InvestmentYear.Phase phase = investing
                        ? InvestmentYear.Phase.INVESTING
                        : InvestmentYear.Phase.HOLDING;
                rows.add(new InvestmentYear(
                        yearIndex, currentYear + yearIndex + 1, monthsInYear, phase,
                        yearContribution, balance, principal, gains, realValue));
                yearIndex++;
                yearContribution = BigDecimal.ZERO;
                monthsInYear = 0;
            }
        }

        final BigDecimal maturityValue = balance;
        final BigDecimal gains = maturityValue.subtract(principal, MC);
        final BigDecimal taxAtExit = gains.signum() > 0 ? gains.multiply(taxRate, MC) : BigDecimal.ZERO;
        final BigDecimal netValue = maturityValue.subtract(taxAtExit, MC);
        final BigDecimal totalDeflator = BigDecimal.valueOf(
                Math.pow(1.0 + inflation.doubleValue(), totalMonths / 12.0));
        final BigDecimal buyingPowerToday = netValue.divide(totalDeflator, MC);

        return new InvestmentResult(
                investmentMonths, holdMonths,
                principal, maturityValue, gains, taxAtExit, netValue, buyingPowerToday,
                rows);
    }

    private static int holdMonths(InvestmentInputs inputs) {
        final int years = inputs.getHoldYears() == null ? 0 : inputs.getHoldYears();
        final int months = inputs.getHoldMonths() == null ? 0 : inputs.getHoldMonths();
        if (months < 0 || months > 11) {
            throw new IllegalArgumentException("Hold months must be between 0 and 11.");
        }
        return years * 12 + months;
    }

    private static BigDecimal required(BigDecimal value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required.");
        }
        return value;
    }
}
