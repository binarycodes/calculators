package io.binarycodes.calculators.retirement.domain;

import java.math.BigDecimal;

/**
 * One year of projection output.
 */
public record ProjectionRow(
        int year,
        int age,
        boolean isRetireYear,
        boolean isPost,
        BigDecimal annualExp,
        BigDecimal startCorpus,
        BigDecimal returns,
        BigDecimal investment,
        BigDecimal withdrawal,
        BigDecimal endCorpus,
        boolean depleted
) {
    private static final BigDecimal LOW_CORPUS_MULTIPLIER = BigDecimal.TEN;

    public boolean lowCorpus() {
        return endCorpus().compareTo(annualExp().multiply(LOW_CORPUS_MULTIPLIER)) < 0;
    }
}
