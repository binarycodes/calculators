package io.binarycodes.calculators.retirement.service;

import io.binarycodes.calculators.base.math.Rates;
import io.binarycodes.calculators.base.common.Frequency;
import io.binarycodes.calculators.retirement.domain.Contribution;
import io.binarycodes.calculators.retirement.domain.FutureExpense;
import io.binarycodes.calculators.retirement.domain.FutureIncome;
import io.binarycodes.calculators.retirement.domain.ProjectionRow;
import io.binarycodes.calculators.retirement.domain.RecurringExpense;
import io.binarycodes.calculators.retirement.domain.RecurringIncome;
import io.binarycodes.calculators.retirement.domain.RetirementBenefit;
import io.binarycodes.calculators.retirement.domain.RetirementInputs;
import io.binarycodes.calculators.retirement.domain.RetirementResult;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Arithmetic uses {@link BigDecimal} under {@link MathContext#DECIMAL64} —
 * matches IEEE 754 double precision while avoiding rounding drift on equality
 * assertions.
 *
 * <p>The calculator models several buckets that each track their own
 * principal and gains:</p>
 *
 * <ul>
 *     <li><b>Main corpus</b> — seeded with the user's existing corpus, grows
 *         at {@code growthPre}/{@code growthPost}, taxed at
 *         {@code corpusTaxRate} on gains. Retirement benefits and future /
 *         recurring incomes are added here.</li>
 *     <li><b>Contribution streams</b> — one bucket per contribution row. A
 *         pre-retirement stream deposits during the working years and grows at
 *         its own rate, then derisks to {@code growthPost} once retired; a
 *         post-retirement stream deposits during retirement and grows at its
 *         own rate. Each stream's gains are taxed at its own rate.</li>
 * </ul>
 *
 * <p>Each year's withdrawal is satisfied by draining the bucket with the
 * lower current growth rate first, so the higher-yielding buckets compound
 * longer. When a bucket is drawn down, the gains portion of the draw
 * incurs tax (reported separately; it doesn't reduce the gross corpus
 * drawdown).</p>
 */
public final class RetirementCalculator {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

    private RetirementCalculator() {
    }

    public static RetirementResult calculate(RetirementInputs in) {
        if (in.getCurrentAge() >= in.getRetireAge()) {
            throw new IllegalArgumentException("Retirement age must be greater than current age.");
        }
        if (in.getRetireAge() >= in.getLifeExp()) {
            throw new IllegalArgumentException("Life expectancy must be greater than retirement age.");
        }

        final BigDecimal inflation = pctToFraction(in.getInflationPct());
        final BigDecimal growthPre = pctToFraction(in.getGrowthPrePct());
        final BigDecimal growthPost = pctToFraction(in.getGrowthPostPct());
        final BigDecimal corpusTaxRate = pctToFraction(in.getCorpusTaxRatePct());
        final BigDecimal annualExp0 = in.getMonthlyExpenses().multiply(TWELVE, MC);

        final Bucket main = new Bucket(in.getCorpus(), in.getCorpus());
        // Each contribution row is its own accumulating stream. Pre-retirement
        // streams contribute during the working years; post-retirement streams
        // during retirement.
        final List<ContributionBucket> preBuckets = buildBuckets(in.getPreRetirementContributions(), false);
        final List<ContributionBucket> postBuckets = buildBuckets(in.getPostRetirementContributions(), true);
        final List<ContributionBucket> allBuckets = new ArrayList<>(preBuckets.size() + postBuckets.size());
        allBuckets.addAll(preBuckets);
        allBuckets.addAll(postBuckets);

        final List<ProjectionRow> rows = new ArrayList<>();
        BigDecimal totalInvested = in.getCorpus();
        BigDecimal investedAtRetirement = BigDecimal.ZERO;
        Integer corpusDepletedAt = null;
        final int currentYear = Year.now().getValue();

        for (int age = in.getCurrentAge(); age <= in.getLifeExp(); age++) {
            final int yearsFromNow = age - in.getCurrentAge();
            final int year = currentYear + yearsFromNow;
            final boolean isRetireYear = age == in.getRetireAge();
            final boolean isPost = age >= in.getRetireAge();
            final BigDecimal annualExp = annualExp0.multiply(pow1plus(inflation, yearsFromNow), MC);

            if (isRetireYear) {
                investedAtRetirement = totalInvested;
            }

            final BigDecimal mainRate = isPost ? growthPost : growthPre;
            final int yearsInPhase = isPost ? age - in.getRetireAge() : age - in.getCurrentAge();

            // This year's contributions: every active stream deposits its
            // stepped-up amount into its own bucket (as principal).
            BigDecimal contributionsThisYear = BigDecimal.ZERO;
            for (final ContributionBucket contribution : allBuckets) {
                if (contribution.activeAt(isPost)) {
                    final BigDecimal deposit = contribution.annualAmount
                            .multiply(pow1plus(contribution.stepUp, yearsInPhase), MC);
                    contribution.bucket.contribute(deposit);
                    contributionsThisYear = contributionsThisYear.add(deposit, MC);
                }
            }

            // Retirement benefits + future/recurring incomes join the existing
            // corpus (grown at the corpus rate, taxed at the corpus rate).
            final BigDecimal netBenefitsThisYear = isRetireYear
                    ? netRetirementBenefits(in.getRetirementBenefits())
                    : BigDecimal.ZERO;
            final BigDecimal netFutureIncomeThisYear = netFutureIncomesFor(
                    in.getFutureIncomes(), year);
            final BigDecimal netRecurringIncomeThisYear = netRecurringIncomesFor(
                    in.getRecurringIncomes(), year);
            final BigDecimal incomeThisYear = netBenefitsThisYear
                    .add(netFutureIncomeThisYear, MC)
                    .add(netRecurringIncomeThisYear, MC);
            main.contribute(incomeThisYear);

            final BigDecimal investment = contributionsThisYear.add(incomeThisYear, MC);
            totalInvested = totalInvested.add(investment, MC);

            BigDecimal startCorpus = main.balance;
            for (final ContributionBucket contribution : allBuckets) {
                startCorpus = startCorpus.add(contribution.bucket.balance, MC);
            }

            // Apply this year's growth before any withdrawal.
            BigDecimal returns = main.applyGrowth(mainRate);
            for (final ContributionBucket contribution : allBuckets) {
                returns = returns.add(
                        contribution.bucket.applyGrowth(contribution.currentRate(isPost, growthPost)), MC);
            }

            final BigDecimal futureExpensesThisYear = inflatedFutureExpensesFor(
                    in.getFutureExpenses(), year, currentYear);
            final BigDecimal recurringExpensesThisYear = inflatedRecurringExpensesFor(
                    in.getRecurringExpenses(), year, currentYear, inflation);
            final BigDecimal withdrawal = (isPost ? annualExp : BigDecimal.ZERO)
                    .add(futureExpensesThisYear, MC)
                    .add(recurringExpensesThisYear, MC);

            // Drain from the bucket with the lower current growth rate first;
            // ties keep insertion order (main first, then streams), preserving
            // the higher-yielding buckets for longer compounding.
            final List<BucketDraw> drawOrder = new ArrayList<>();
            drawOrder.add(new BucketDraw(main, mainRate, corpusTaxRate));
            for (final ContributionBucket contribution : allBuckets) {
                drawOrder.add(new BucketDraw(contribution.bucket,
                        contribution.currentRate(isPost, growthPost), contribution.taxRate));
            }
            drawOrder.sort(Comparator.comparing((BucketDraw d) -> d.growthRate));

            BigDecimal remaining = withdrawal;
            BigDecimal taxPaid = BigDecimal.ZERO;
            for (final BucketDraw d : drawOrder) {
                if (remaining.signum() <= 0) {
                    break;
                }
                final BigDecimal balanceBefore = d.bucket.balance;
                final BigDecimal principalBefore = d.bucket.principal;
                remaining = d.bucket.drain(remaining);
                final BigDecimal drawn = balanceBefore.subtract(d.bucket.balance, MC);
                if (drawn.signum() > 0 && balanceBefore.signum() > 0) {
                    final BigDecimal gainsBefore = balanceBefore.subtract(principalBefore, MC);
                    final BigDecimal gainsDrawn = drawn.multiply(gainsBefore, MC)
                            .divide(balanceBefore, MC);
                    if (gainsDrawn.signum() > 0) {
                        taxPaid = taxPaid.add(gainsDrawn.multiply(d.taxRate, MC), MC);
                    }
                }
            }

            final BigDecimal endCorpus;
            final boolean depleted;
            if (remaining.signum() > 0) {
                // Shortfall — surface as negative endCorpus.
                endCorpus = remaining.negate();
                depleted = true;
            } else {
                BigDecimal total = main.balance;
                for (final ContributionBucket contribution : allBuckets) {
                    total = total.add(contribution.bucket.balance, MC);
                }
                endCorpus = total;
                depleted = endCorpus.signum() < 0;
            }

            rows.add(new ProjectionRow(year, age, isRetireYear, isPost,
                    annualExp, startCorpus, returns, investment, withdrawal,
                    taxPaid, endCorpus, depleted));

            if (depleted && corpusDepletedAt == null) {
                corpusDepletedAt = age;
            }
            if (depleted) {
                break;
            }
        }

        return new RetirementResult(
                List.copyOf(rows),
                Optional.ofNullable(corpusDepletedAt),
                investedAtRetirement);
    }

    private static List<ContributionBucket> buildBuckets(List<Contribution> rows, boolean post) {
        final List<ContributionBucket> out = new ArrayList<>();
        if (rows == null) {
            return out;
        }
        for (final Contribution row : rows) {
            if (row == null || row.getAmount() == null || row.getAmount().signum() <= 0) {
                continue;
            }
            final Frequency frequency = row.getFrequency() == null ? Frequency.MONTHLY : row.getFrequency();
            final BigDecimal annualAmount = row.getAmount()
                    .multiply(BigDecimal.valueOf(frequency.periodsPerYear()), MC);
            out.add(new ContributionBucket(annualAmount,
                    pctToFraction(row.getStepUpPct()),
                    pctToFraction(row.getGrowthPct()),
                    pctToFraction(row.getTaxRatePct()),
                    post));
        }
        return out;
    }

    /**
     * One contribution stream: its own accumulating {@link Bucket} plus the
     * annualised amount, yearly step-up, growth rate, and gains tax rate. A
     * pre-retirement stream ({@code post == false}) grows at its own rate while
     * contributing, then derisks to the corpus post-retirement rate; a
     * post-retirement stream grows at its own rate throughout.
     */
    private static final class ContributionBucket {
        final Bucket bucket = new Bucket(BigDecimal.ZERO, BigDecimal.ZERO);
        final BigDecimal annualAmount;
        final BigDecimal stepUp;
        final BigDecimal growth;
        final BigDecimal taxRate;
        final boolean post;

        ContributionBucket(BigDecimal annualAmount, BigDecimal stepUp, BigDecimal growth,
                           BigDecimal taxRate, boolean post) {
            this.annualAmount = annualAmount;
            this.stepUp = stepUp;
            this.growth = growth;
            this.taxRate = taxRate;
            this.post = post;
        }

        boolean activeAt(boolean isPost) {
            return this.post == isPost;
        }

        BigDecimal currentRate(boolean isPost, BigDecimal growthPost) {
            if (this.post) {
                return this.growth;
            }
            return isPost ? growthPost : this.growth;
        }
    }

    /**
     * Mutable per-bucket state. Tracks total balance and principal; gains
     * are derived as {@code balance − principal}.
     */
    private static final class Bucket {
        BigDecimal balance;
        BigDecimal principal;

        Bucket(BigDecimal balance, BigDecimal principal) {
            this.balance = balance;
            this.principal = principal;
        }

        void contribute(BigDecimal amount) {
            this.balance = this.balance.add(amount, MC);
            this.principal = this.principal.add(amount, MC);
        }

        BigDecimal applyGrowth(BigDecimal rate) {
            final BigDecimal earnings = this.balance.multiply(rate, MC);
            this.balance = this.balance.add(earnings, MC);
            return earnings;
        }

        /**
         * Draw up to {@code request} from this bucket. Returns whatever
         * couldn't be satisfied (so the caller can roll over to the next
         * bucket). Updates principal proportionally so the gains-vs-principal
         * mix of the remaining balance is preserved.
         */
        BigDecimal drain(BigDecimal request) {
            if (request.signum() <= 0 || this.balance.signum() <= 0) {
                return request;
            }
            if (request.compareTo(this.balance) >= 0) {
                final BigDecimal shortfall = request.subtract(this.balance, MC);
                this.balance = BigDecimal.ZERO;
                this.principal = BigDecimal.ZERO;
                return shortfall;
            }
            final BigDecimal principalDrawn = request.multiply(this.principal, MC)
                    .divide(this.balance, MC);
            this.principal = this.principal.subtract(principalDrawn, MC);
            this.balance = this.balance.subtract(request, MC);
            return BigDecimal.ZERO;
        }
    }

    private record BucketDraw(Bucket bucket, BigDecimal growthRate, BigDecimal taxRate) {
    }

    private static BigDecimal netRetirementBenefits(List<RetirementBenefit> benefits) {
        if (benefits == null || benefits.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (final RetirementBenefit benefit : benefits) {
            if (benefit == null) {
                continue;
            }
            final BigDecimal amount = benefit.getAmount();
            if (amount == null || amount.signum() <= 0) {
                continue;
            }
            final BigDecimal taxRate = pctToFraction(benefit.getTaxRatePct());
            final BigDecimal net = amount.multiply(BigDecimal.ONE.subtract(taxRate, MC), MC);
            sum = sum.add(net, MC);
        }
        return sum;
    }

    private static BigDecimal netFutureIncomesFor(List<FutureIncome> incomes, int year) {
        if (incomes == null || incomes.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (final FutureIncome income : incomes) {
            if (income == null || !Objects.equals(income.getYear(), year)) {
                continue;
            }
            final BigDecimal amount = income.getAmount();
            if (amount == null || amount.signum() <= 0) {
                continue;
            }
            final BigDecimal taxRate = pctToFraction(income.getTaxRatePct());
            final BigDecimal net = amount.multiply(BigDecimal.ONE.subtract(taxRate, MC), MC);
            sum = sum.add(net, MC);
        }
        return sum;
    }

    private static BigDecimal netRecurringIncomesFor(List<RecurringIncome> incomes, int year) {
        if (incomes == null || incomes.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (final RecurringIncome income : incomes) {
            if (income == null || income.getYear() == null || year < income.getYear()) {
                continue;
            }
            if (income.getStopYear() != null && year > income.getStopYear()) {
                continue;
            }
            final BigDecimal amount = income.getAmount();
            if (amount == null || amount.signum() <= 0) {
                continue;
            }
            final BigDecimal annualised = annualise(amount, income.getFrequency());
            final BigDecimal taxRate = pctToFraction(income.getTaxRatePct());
            final BigDecimal net = annualised.multiply(BigDecimal.ONE.subtract(taxRate, MC), MC);
            sum = sum.add(net, MC);
        }
        return sum;
    }

    private static BigDecimal inflatedRecurringExpensesFor(List<RecurringExpense> expenses,
                                                           int year, int currentYear,
                                                           BigDecimal inflation) {
        if (expenses == null || expenses.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (final RecurringExpense expense : expenses) {
            if (expense == null || expense.getYear() == null || year < expense.getYear()) {
                continue;
            }
            if (expense.getStopYear() != null && year > expense.getStopYear()) {
                continue;
            }
            final BigDecimal amount = expense.getAmount();
            if (amount == null || amount.signum() <= 0) {
                continue;
            }
            final BigDecimal annualised = annualise(amount, expense.getFrequency());
            // Use the per-item inflation rate when supplied; otherwise fall
            // back to the main rate so amounts at least keep pace with general
            // inflation (medical, education, food etc. usually differ — the
            // per-item field lets the user override).
            final BigDecimal itemInflation = expense.getInflationPct() == null
                    || expense.getInflationPct().signum() <= 0
                    ? inflation
                    : pctToFraction(expense.getInflationPct());
            final int yearsFromNow = Math.max(0, year - currentYear);
            sum = sum.add(annualised.multiply(pow1plus(itemInflation, yearsFromNow), MC), MC);
        }
        return sum;
    }

    private static BigDecimal annualise(BigDecimal amount, Frequency frequency) {
        // Default to monthly if unspecified — matches the form default and is
        // the more conservative (larger) interpretation if a value sneaks
        // through without a frequency tag.
        final Frequency effective = frequency == null ? Frequency.MONTHLY : frequency;
        return amount.multiply(BigDecimal.valueOf(effective.periodsPerYear()), MC);
    }

    private static BigDecimal inflatedFutureExpensesFor(List<FutureExpense> expenses,
                                                        int year, int currentYear) {
        if (expenses == null || expenses.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (final FutureExpense expense : expenses) {
            if (expense == null || !Objects.equals(expense.getYear(), year)) {
                continue;
            }
            final BigDecimal amount = expense.getAmount();
            if (amount == null || amount.signum() <= 0) {
                continue;
            }
            final BigDecimal itemInflation = pctToFraction(expense.getInflationPct());
            final int yearsFromNow = Math.max(0, year - currentYear);
            sum = sum.add(amount.multiply(pow1plus(itemInflation, yearsFromNow), MC), MC);
        }
        return sum;
    }

    private static BigDecimal pctToFraction(BigDecimal pct) {
        return Rates.pctToFraction(pct);
    }

    private static BigDecimal pow1plus(BigDecimal rate, int n) {
        return Rates.pow1plus(rate, n);
    }
}
