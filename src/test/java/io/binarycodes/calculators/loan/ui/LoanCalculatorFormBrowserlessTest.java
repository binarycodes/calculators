package io.binarycodes.calculators.loan.ui;

import com.vaadin.browserless.BrowserlessExtension;
import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.flow.component.textfield.NumberField;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Percentage rate fields must not impose a numeric {@code step}: a step (e.g.
 * 0.1) makes the browser reject valid two-decimal rates like 4.45% as a step
 * mismatch, flagging the field invalid with no explanatory message. Guards
 * against re-introducing {@code setStep(...)} on these fields.
 */
class LoanCalculatorFormBrowserlessTest extends BrowserlessTest {

    @RegisterExtension
    static final BrowserlessExtension EXTENSION = new BrowserlessExtension()
            .withServices(UserPreferences.class);

    @Override
    protected Set<String> scanPackages() {
        // Avoid route discovery dragging in MainLayout (no no-arg constructor).
        return Set.of("io.binarycodes.calculators.test.noroutes");
    }

    @Test
    void rate_fields_impose_no_step_so_decimal_rates_are_accepted() {
        final var form = new LoanCalculatorForm(new UserPreferences());
        for (final String caption : new String[]{"Interest Rate", "Inflation Rate"}) {
            final NumberField field = find(NumberField.class, form).withCaption(caption).single();
            assertNull(field.getElement().getProperty("step"),
                    caption + " must not set a step — it would reject decimals like 4.45%");
        }
    }
}
