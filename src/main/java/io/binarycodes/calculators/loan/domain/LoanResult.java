package io.binarycodes.calculators.loan.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * Aggregate output of {@code LoanCalculator.calculate(...)}.
 *
 * <p>Three scenarios are reported. <b>Baseline</b> is the plain loan. With
 * prepayments, the same extra cash can either shorten the loan (<b>reduce
 * tenure</b>, EMI fixed) or lower the monthly outgo (<b>reduce EMI</b>, end date
 * fixed) — both are computed so the UI can show them side by side. When no
 * prepayments are set the two scenarios collapse onto the baseline.
 *
 * @param emi                      base monthly EMI
 * @param baseMonths               original tenure in months
 * @param totalInterestBaseline    interest over the full original tenure
 * @param totalPaymentBaseline     principal + baseline interest
 * @param reducedMonths            payoff length under the reduce-tenure scenario
 * @param totalInterestReduceTenure interest under reduce-tenure
 * @param totalPaymentReduceTenure principal + reduce-tenure interest (incl. prepayments)
 * @param interestSavedTenure      {@code baseline − reduceTenure} interest
 * @param monthsSaved              {@code baseMonths − reducedMonths}
 * @param finalEmiReduceEmi        the (lowered) EMI at the end of the reduce-EMI scenario
 * @param totalInterestReduceEmi   interest under reduce-EMI
 * @param interestSavedEmi         {@code baseline − reduceEmi} interest
 * @param realTotalInterest        reduce-tenure interest expressed in today's money
 * @param hasPrepayments           whether any prepayment lever is active
 * @param rows                     reduce-tenure yearly schedule (drives the grid by default)
 * @param reduceEmiRows            reduce-EMI yearly schedule (drives the grid when toggled)
 * @param baselineRows             baseline yearly schedule (drives the chart's reference curve)
 */
public record LoanResult(
        BigDecimal emi,
        int baseMonths,
        BigDecimal totalInterestBaseline,
        BigDecimal totalPaymentBaseline,
        int reducedMonths,
        BigDecimal totalInterestReduceTenure,
        BigDecimal totalPaymentReduceTenure,
        BigDecimal interestSavedTenure,
        int monthsSaved,
        BigDecimal finalEmiReduceEmi,
        BigDecimal totalInterestReduceEmi,
        BigDecimal interestSavedEmi,
        BigDecimal realTotalInterest,
        boolean hasPrepayments,
        List<LoanYear> rows,
        List<LoanYear> reduceEmiRows,
        List<LoanYear> baselineRows
) {
}
