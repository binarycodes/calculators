package io.binarycodes.calculators.retirement.ui;

import com.vaadin.flow.component.charts.model.DataSeries;
import com.vaadin.flow.component.charts.model.DataSeriesItem;
import com.vaadin.flow.component.charts.model.PlotLine;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.retirement.domain.ProjectionRow;
import io.binarycodes.calculators.retirement.domain.RetirementInputs;
import io.binarycodes.calculators.retirement.domain.RetirementResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The depletion marker on the corpus chart must sit exactly where the plotted
 * corpus curve reaches zero — not a year earlier, where the curve is still
 * visibly positive.
 */
class CorpusChartTest {

    private static ProjectionRow row(int age, double endCorpus) {
        return new ProjectionRow(2000 + age, age, false, false,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(endCorpus), false);
    }

    @Test
    void depletion_marker_aligns_with_the_curve_zero_crossing() {
        final RetirementInputs inputs = new RetirementInputs();
        inputs.setCurrentAge(60);
        inputs.setRetireAge(61);
        inputs.setCorpus(new BigDecimal("1000000"));

        // Corpus drains to zero in the age-62 row; the (clamped) zero is plotted
        // at the following x tick.
        final RetirementResult result = new RetirementResult(
                List.of(row(61, 400000), row(62, -200000)),
                Optional.of(62), new BigDecimal("1000000"));

        final CorpusChart chart = new CorpusChart();
        chart.update(inputs, result, SupportedCurrency.USD);

        final DataSeries series = (DataSeries) chart.getConfiguration().getSeries().get(0);
        double firstZeroX = Double.NaN;
        for (final DataSeriesItem item : series.getData()) {
            if (item.getY().doubleValue() == 0.0) {
                firstZeroX = item.getX().doubleValue();
                break;
            }
        }

        PlotLine depletionLine = null;
        for (final PlotLine line : chart.getConfiguration().getxAxis().getPlotLines()) {
            if ("depletion_point".equals(line.getClassName())) {
                depletionLine = line;
                break;
            }
        }

        assertNotNull(depletionLine, "depletion marker must be present");
        assertEquals(firstZeroX, depletionLine.getValue().doubleValue(),
                "depletion marker must sit at the curve's zero crossing");
    }
}
