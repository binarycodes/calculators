package io.binarycodes.calculators.retirement.ui;

import com.vaadin.browserless.BrowserlessExtension;
import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.flow.component.textfield.NumberField;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.retirement.domain.RetirementInputs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Smoke test that the SIP step-up fields are rendered and round-trip cleanly
 * through the binder. Catches binding-level regressions (renamed getters,
 * missing {@code bindPercentage} call) that the calculator unit tests can't.
 */
class RetirementCalculatorFormBrowserlessTest extends BrowserlessTest {

    @RegisterExtension
    static final BrowserlessExtension EXTENSION = new BrowserlessExtension()
            .withServices(UserPreferences.class);

    @Override
    protected Set<String> scanPackages() {
        // Default discovery picks up RetirementView via @RouteAlias(""), and the
        // mock setup then tries to render it through MainLayout — whose
        // constructor takes UserPreferences and can't be no-arg-instantiated by
        // the mock. Scope discovery to an empty package so no routes are found
        // and setup doesn't dump a navigation stack trace to stderr.
        return Set.of("io.binarycodes.calculators.test.noroutes");
    }

    @Test
    void renders_two_step_up_fields() {
        final var form = new RetirementCalculatorForm(new UserPreferences());

        final List<NumberField> stepUpFields = find(NumberField.class, form)
                .withCaption("Step Up Percentage (Yearly)").all();

        assertEquals(2, stepUpFields.size(),
                "expected one Step Up field under Before Retirement and one under After Retirement");
    }

    @Test
    void step_up_values_round_trip_through_binder() {
        final var form = new RetirementCalculatorForm(new UserPreferences());

        final RetirementInputs initial = new RetirementInputs(
                38, 45, 90,
                BigDecimal.valueOf(15_000_000), BigDecimal.valueOf(100_000),
                BigDecimal.valueOf(8),
                BigDecimal.valueOf(12), BigDecimal.valueOf(8),
                BigDecimal.valueOf(150_000), BigDecimal.valueOf(12), BigDecimal.valueOf(10),
                BigDecimal.valueOf(50_000), BigDecimal.valueOf(8), BigDecimal.valueOf(5));
        form.setInputs(initial);

        final RetirementInputs roundTripped = form.getInputs();

        assertNotNull(roundTripped.getSipStepUpPrePct());
        assertNotNull(roundTripped.getSipStepUpPostPct());
        assertEquals(0, initial.getSipStepUpPrePct().compareTo(roundTripped.getSipStepUpPrePct()),
                "pre step-up must survive binder round-trip");
        assertEquals(0, initial.getSipStepUpPostPct().compareTo(roundTripped.getSipStepUpPostPct()),
                "post step-up must survive binder round-trip");
    }
}
