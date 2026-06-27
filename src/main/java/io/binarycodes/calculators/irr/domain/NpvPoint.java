package io.binarycodes.calculators.irr.domain;

import java.math.BigDecimal;

/**
 * One sample of the NPV-vs-discount-rate curve: the net present value of all
 * cashflows discounted at {@code rate} (a fraction, e.g. {@code 0.12} for 12%).
 * The rate(s) where {@code npv} crosses zero are the IRR(s).
 */
public record NpvPoint(BigDecimal rate, BigDecimal npv) {
}
