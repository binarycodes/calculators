package io.binarycodes.calculators.goal.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One investment bucket inside the goal planner. The user can add as many as
 * they want; each carries its own growth assumption, withdrawal-tax rate, and
 * a share of every monthly SIP contribution (set by {@link #allocationPct}).
 *
 * <p>The form validates that the allocations across all investments sum to
 * 100% so the solver can distribute the required SIP correctly.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Investment {

    /** Human label for the bucket — purely cosmetic (e.g. "Index Fund", "FD"). */
    private String label;

    /** Current balance in this bucket (treated as 100% principal). */
    private BigDecimal currentCorpus;

    /** Annual growth rate, as a percentage ({@code 12} for 12%). */
    private BigDecimal growthRatePct;

    /** Tax rate applied to the gains portion at the end of the horizon. */
    private BigDecimal withdrawalTaxRatePct;

    /** Share of every monthly SIP contribution that flows into this bucket. */
    private BigDecimal allocationPct;

    /**
     * Annual step-up on this bucket's share of the monthly contribution. After
     * 12 months, the share becomes {@code share · (1 + stepUp)}; after 24
     * months {@code share · (1 + stepUp)^2}; and so on. Independent per bucket
     * so a user can flat-line their FD contribution while ramping equity.
     */
    private BigDecimal stepUpPct;
}
