package io.binarycodes.calculators.investment.ui;

import com.vaadin.browserless.BrowserlessExtension;
import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.flow.component.UI;
import io.binarycodes.calculators.base.common.Frequency;
import io.binarycodes.calculators.base.i18n.Translations;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.base.ui.FrequencyField;
import io.binarycodes.calculators.investment.domain.InvestmentInputs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

/**
 * Browserless coverage for the shared {@link FrequencyField} as wired into the
 * investment form: it offers all four frequencies with their translated
 * labels, and a selection round-trips through the form's binder.
 */
class InvestmentFrequencyFieldBrowserlessTest extends BrowserlessTest {

    @RegisterExtension
    static final BrowserlessExtension EXTENSION = new BrowserlessExtension()
            .withServices(UserPreferences.class);

    @Override
    protected Set<String> scanPackages() {
        // Skip route discovery so the @Route("") landing view isn't mounted
        // (its constructor needs a UI for menu introspection).
        return Set.of("io.binarycodes.calculators.test.noroutes");
    }

    @Test
    void frequency_field_offers_all_four_options_with_translated_labels() {
        final var form = new InvestmentCalculatorForm(new UserPreferences());
        UI.getCurrent().add(form);
        roundTrip();

        final FrequencyField frequency = find(FrequencyField.class, form).single();

        // Every enum value is offered, in declaration order.
        assertIterableEquals(
                List.of(Frequency.MONTHLY, Frequency.QUARTERLY, Frequency.HALF_YEARLY, Frequency.YEARLY),
                frequency.inner().getGenericDataView().getItems().toList());

        // Each renders through its i18n key rather than the enum name.
        final var labels = frequency.inner().getItemLabelGenerator();
        assertEquals(Translations.get("frequency.monthly"), labels.apply(Frequency.MONTHLY));
        assertEquals(Translations.get("frequency.quarterly"), labels.apply(Frequency.QUARTERLY));
        assertEquals(Translations.get("frequency.halfYearly"), labels.apply(Frequency.HALF_YEARLY));
        assertEquals(Translations.get("frequency.yearly"), labels.apply(Frequency.YEARLY));
    }

    @Test
    void selecting_a_frequency_round_trips_through_the_binder() {
        final var form = new InvestmentCalculatorForm(new UserPreferences());
        UI.getCurrent().add(form);
        roundTrip();

        final FrequencyField frequency = find(FrequencyField.class, form).single();

        // Simulate the user picking Half-Yearly: driving the inner Select must
        // push through the CustomField (updateValue) into the bound inputs.
        frequency.inner().setValue(Frequency.HALF_YEARLY);
        roundTrip();
        assertEquals(Frequency.HALF_YEARLY, form.getInputs().getFrequency());

        // ...and inbound values (share links, defaults) render on the field.
        final InvestmentInputs quarterly = new InvestmentInputs();
        quarterly.setFrequency(Frequency.QUARTERLY);
        form.setInputs(quarterly);
        roundTrip();
        assertEquals(Frequency.QUARTERLY, frequency.getValue());
    }
}
