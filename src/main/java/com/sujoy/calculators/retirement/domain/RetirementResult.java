package com.sujoy.calculators.retirement.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Aggregate output of {@code RetirementCalculator.calculate(...)}.
 *
 * @param rows                  year-by-year projection rows
 * @param corpusDepletedAt      age of the first row where the corpus went negative,
 *                              or empty if the corpus survived past life expectancy
 * @param investedAtRetirement  cumulative principal (initial + SIP contributions)
 *                              at the moment of retirement
 */
public record RetirementResult(
        List<ProjectionRow> rows,
        Optional<Integer> corpusDepletedAt,
        BigDecimal investedAtRetirement
) {
    /** Last fully-covered year. Equals lifeExp when corpus never depleted. */
    public ProjectionRow lastsUntilRow() {
        int target = corpusDepletedAt.map(a -> a - 1)
                .orElseGet(() -> rows.get(rows.size() - 1).age());
        for (ProjectionRow r : rows) if (r.age() == target) return r;
        return rows.get(rows.size() - 1);
    }
}
