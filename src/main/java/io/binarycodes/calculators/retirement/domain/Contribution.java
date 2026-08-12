package io.binarycodes.calculators.retirement.domain;

import io.binarycodes.calculators.base.common.Frequency;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A single recurring investment contribution stream. {@code amount} is paid
 * every {@link #frequency} period; {@code growthPct} is the annual return earned
 * on this stream's accumulated balance; {@code stepUpPct} raises the contribution
 * amount each year; {@code taxRatePct} is applied on the gains portion when the
 * money is later drawn. Pre-retirement streams contribute during the working
 * years; post-retirement streams contribute during retirement.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Contribution {

    private BigDecimal amount;
    private Frequency frequency;
    private BigDecimal growthPct;
    private BigDecimal stepUpPct;
    private BigDecimal taxRatePct;
}
