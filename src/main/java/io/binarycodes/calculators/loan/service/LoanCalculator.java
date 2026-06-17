package io.binarycodes.calculators.loan.service;

import io.binarycodes.calculators.base.math.Rates;
import io.binarycodes.calculators.loan.domain.LoanInputs;
import io.binarycodes.calculators.loan.domain.LoanResult;
import io.binarycodes.calculators.loan.domain.LoanYear;
import io.binarycodes.calculators.loan.domain.PrepaymentFrequency;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

/**
 * Reducing-balance EMI calculator with prepayment analysis.
 *
 * <p>The monthly rate is the <b>nominal</b> {@code annual/12} convention every
 * lender and EMI calculator uses (not effective compounding), so the EMI matches
 * what a bank would quote. Interest each month is {@code balance × r}; the
 * principal portion is {@code EMI − interest}. A zero interest rate is handled
 * specially ({@code EMI = principal / months}).</p>
 *
 * <p>Three scenarios are simulated: the plain <b>baseline</b>, <b>reduce
 * tenure</b> (prepayments shorten the loan, EMI fixed), and <b>reduce EMI</b>
 * (prepayments re-amortize to a lower EMI over the original tenure). EMI step-up
 * is a "pay more" lever, so it participates only in reduce-tenure.</p>
 *
 * <p>EMIs are rounded to the minor currency unit, so the schedule wouldn't clear
 * to exactly zero; the final installment is adjusted to settle the balance, and
 * totals come from the actual month-by-month schedule.</p>
 */
public final class LoanCalculator {

    private static final MathContext MC = Rates.CONTEXT;
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);
    private static final int MONEY_SCALE = 2;

    private LoanCalculator() {
    }

    public static LoanResult calculate(LoanInputs inputs) {
        final BigDecimal principal = required(inputs.getLoanAmount(), "Loan amount");
        if (principal.signum() <= 0) {
            throw new IllegalArgumentException("Loan amount must be positive.");
        }
        final int months = totalMonths(inputs);
        if (months < 1) {
            throw new IllegalArgumentException("Tenure must be at least one month.");
        }
        final BigDecimal annual = Rates.pctToFraction(inputs.getAnnualRatePct());
        if (annual.signum() < 0) {
            throw new IllegalArgumentException("Interest rate cannot be negative.");
        }
        final BigDecimal monthlyRate = annual.signum() == 0 ? BigDecimal.ZERO : annual.divide(TWELVE, MC);
        final BigDecimal inflation = Rates.pctToFraction(inputs.getInflationRatePct());

        final BigDecimal emi = computeEmi(principal, monthlyRate, months);

        final PrepayConfig prepay = PrepayConfig.from(inputs);

        final Schedule baseline = simulate(principal, monthlyRate, months, emi, PrepayConfig.NONE,
                Strategy.REDUCE_TENURE, inflation);
        final Schedule reduceTenure = simulate(principal, monthlyRate, months, emi, prepay,
                Strategy.REDUCE_TENURE, inflation);
        final Schedule reduceEmi = simulate(principal, monthlyRate, months, emi, prepay.withoutStepUp(),
                Strategy.REDUCE_EMI, inflation);

        final BigDecimal interestSavedTenure =
                baseline.totalInterest().subtract(reduceTenure.totalInterest(), MC).max(BigDecimal.ZERO);
        final BigDecimal interestSavedEmi =
                baseline.totalInterest().subtract(reduceEmi.totalInterest(), MC).max(BigDecimal.ZERO);

        return new LoanResult(
                emi,
                months,
                scale(baseline.totalInterest()),
                scale(principal.add(baseline.totalInterest(), MC)),
                reduceTenure.months(),
                scale(reduceTenure.totalInterest()),
                scale(principal.add(reduceTenure.totalInterest(), MC)),
                scale(interestSavedTenure),
                months - reduceTenure.months(),
                scale(reduceEmi.finalEmi()),
                scale(reduceEmi.totalInterest()),
                scale(interestSavedEmi),
                scale(reduceTenure.realInterest()),
                prepay.active(),
                reduceTenure.rows(),
                reduceEmi.rows(),
                baseline.rows());
    }

    private static Schedule simulate(BigDecimal principal, BigDecimal monthlyRate, int baseMonths,
                                     BigDecimal baseEmi, PrepayConfig prepay, Strategy strategy,
                                     BigDecimal inflation) {
        final List<LoanYear> rows = new ArrayList<>();
        final int currentYear = Year.now().getValue();
        final int safety = baseMonths * 2 + 24;

        BigDecimal balance = principal;
        BigDecimal emi = baseEmi;
        BigDecimal totalInterest = BigDecimal.ZERO;
        BigDecimal realInterest = BigDecimal.ZERO;

        BigDecimal yearEmi = BigDecimal.ZERO;
        BigDecimal yearInterest = BigDecimal.ZERO;
        BigDecimal yearPrincipal = BigDecimal.ZERO;
        BigDecimal yearPrepay = BigDecimal.ZERO;
        int monthsInYear = 0;
        int yearIndex = 0;
        int month = 0;

        while (balance.signum() > 0 && month < safety) {
            month++;

            if (strategy == Strategy.REDUCE_TENURE && prepay.stepUp().signum() > 0
                    && month > 1 && (month - 1) % 12 == 0) {
                emi = emi.multiply(BigDecimal.ONE.add(prepay.stepUp(), MC), MC);
            }

            final BigDecimal interest = balance.multiply(monthlyRate, MC);
            final BigDecimal prepayThisMonth = prepay.forMonth(month, baseEmi);

            // The nominal final month settles whatever is left, so EMI rounding
            // never spills the loan into an extra stub month — lenders do the same.
            final boolean forcedFinal = month == baseMonths;
            BigDecimal principalPart = emi.subtract(interest, MC);
            if (!forcedFinal && principalPart.signum() <= 0 && prepayThisMonth.signum() == 0) {
                throw new IllegalArgumentException(
                        "The EMI does not cover the monthly interest — increase the tenure or lower the rate.");
            }

            BigDecimal emiThisMonth = emi;
            if (forcedFinal || principalPart.compareTo(balance) >= 0) {
                principalPart = balance;
                emiThisMonth = balance.add(interest, MC);
            }
            balance = balance.subtract(principalPart, MC);

            BigDecimal appliedPrepay = prepayThisMonth.min(balance);
            if (appliedPrepay.signum() < 0) {
                appliedPrepay = BigDecimal.ZERO;
            }
            balance = balance.subtract(appliedPrepay, MC);

            totalInterest = totalInterest.add(interest, MC);
            realInterest = realInterest.add(deflate(interest, inflation, month), MC);

            yearEmi = yearEmi.add(emiThisMonth, MC);
            yearInterest = yearInterest.add(interest, MC);
            yearPrincipal = yearPrincipal.add(principalPart, MC);
            yearPrepay = yearPrepay.add(appliedPrepay, MC);
            monthsInYear++;

            if (strategy == Strategy.REDUCE_EMI && appliedPrepay.signum() > 0 && balance.signum() > 0) {
                final int remaining = baseMonths - month;
                if (remaining > 0) {
                    emi = computeEmi(balance, monthlyRate, remaining);
                }
            }

            final boolean paidOff = balance.signum() <= 0;
            if (monthsInYear == 12 || paidOff || month == safety) {
                rows.add(new LoanYear(yearIndex, currentYear + yearIndex + 1, monthsInYear,
                        scale(yearEmi), scale(yearInterest), scale(yearPrincipal),
                        scale(yearPrepay), scale(balance.max(BigDecimal.ZERO))));
                yearIndex++;
                monthsInYear = 0;
                yearEmi = BigDecimal.ZERO;
                yearInterest = BigDecimal.ZERO;
                yearPrincipal = BigDecimal.ZERO;
                yearPrepay = BigDecimal.ZERO;
            }
            if (paidOff) {
                break;
            }
        }

        return new Schedule(rows, totalInterest, realInterest, month, emi);
    }

    private static BigDecimal computeEmi(BigDecimal principal, BigDecimal monthlyRate, int months) {
        if (months <= 0) {
            throw new IllegalArgumentException("Tenure must be at least one month.");
        }
        if (monthlyRate.signum() == 0) {
            return principal.divide(BigDecimal.valueOf(months), MONEY_SCALE, RoundingMode.HALF_UP);
        }
        final BigDecimal factor = Rates.pow1plus(monthlyRate, months);
        final BigDecimal emi = principal.multiply(monthlyRate, MC).multiply(factor, MC)
                .divide(factor.subtract(BigDecimal.ONE, MC), MC);
        return emi.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal deflate(BigDecimal amount, BigDecimal inflation, int monthIndex) {
        if (inflation.signum() <= 0) {
            return amount;
        }
        final BigDecimal deflator = BigDecimal.valueOf(
                Math.pow(1.0 + inflation.doubleValue(), monthIndex / 12.0));
        return amount.divide(deflator, MC);
    }

    private static int totalMonths(LoanInputs inputs) {
        final int years = inputs.getTenureYears() == null ? 0 : inputs.getTenureYears();
        final int extraMonths = inputs.getTenureMonths() == null ? 0 : inputs.getTenureMonths();
        if (years < 0 || extraMonths < 0 || extraMonths > 11) {
            throw new IllegalArgumentException("Tenure months must be between 0 and 11.");
        }
        return years * 12 + extraMonths;
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal required(BigDecimal value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required.");
        }
        return value;
    }

    private enum Strategy {
        REDUCE_TENURE,
        REDUCE_EMI
    }

    /** One scenario's outcome: schedule rows plus the totals derived from them. */
    private record Schedule(
            List<LoanYear> rows,
            BigDecimal totalInterest,
            BigDecimal realInterest,
            int months,
            BigDecimal finalEmi) {
    }

    /** Resolved prepayment levers, with the per-month extra computed on demand. */
    private record PrepayConfig(
            BigDecimal extraPerPeriod,
            PrepaymentFrequency frequency,
            int extraEmisPerYear,
            BigDecimal stepUp) {

        static final PrepayConfig NONE =
                new PrepayConfig(BigDecimal.ZERO, PrepaymentFrequency.YEARLY, 0, BigDecimal.ZERO);

        static PrepayConfig from(LoanInputs inputs) {
            final BigDecimal extra = inputs.getExtraPerPeriod() == null
                    ? BigDecimal.ZERO : inputs.getExtraPerPeriod().max(BigDecimal.ZERO);
            final PrepaymentFrequency frequency = inputs.getExtraFrequency() == null
                    ? PrepaymentFrequency.YEARLY : inputs.getExtraFrequency();
            final int extraEmis = inputs.getExtraEmisPerYear() == null
                    ? 0 : Math.max(0, inputs.getExtraEmisPerYear());
            final BigDecimal stepUp = Rates.pctToFraction(inputs.getEmiStepUpPct());
            return new PrepayConfig(extra, frequency, extraEmis, stepUp);
        }

        PrepayConfig withoutStepUp() {
            return new PrepayConfig(extraPerPeriod, frequency, extraEmisPerYear, BigDecimal.ZERO);
        }

        boolean active() {
            return extraPerPeriod.signum() > 0 || extraEmisPerYear > 0 || stepUp.signum() > 0;
        }

        BigDecimal forMonth(int month, BigDecimal baseEmi) {
            BigDecimal extra = BigDecimal.ZERO;
            if (extraPerPeriod.signum() > 0 && isPeriodEnd(month)) {
                extra = extra.add(extraPerPeriod, MC);
            }
            if (extraEmisPerYear > 0 && month % 12 == 0) {
                extra = extra.add(baseEmi.multiply(BigDecimal.valueOf(extraEmisPerYear), MC), MC);
            }
            return extra;
        }

        private boolean isPeriodEnd(int month) {
            return switch (frequency) {
                case MONTHLY -> true;
                case QUARTERLY -> month % 3 == 0;
                case YEARLY -> month % 12 == 0;
            };
        }
    }
}
