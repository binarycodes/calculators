package io.binarycodes.calculators.base.ui;

import com.vaadin.flow.component.customfield.CustomField;
import com.vaadin.flow.component.select.Select;
import io.binarycodes.calculators.base.common.Frequency;
import io.binarycodes.calculators.base.i18n.Translations;

/**
 * A dropdown for picking a {@link Frequency}. Presents the four options
 * (Monthly / Quarterly / Half-Yearly / Yearly) uniformly across every
 * calculator, so the choice looks and behaves the same whether it is a
 * top-level bound form field or lives inside a recurring-row editor.
 */
public class FrequencyField extends CustomField<Frequency> {

    private final Select<Frequency> inner = new Select<>();

    public FrequencyField(String label) {
        setLabel(label);

        this.inner.setItems(Frequency.values());
        this.inner.setItemLabelGenerator(FrequencyField::labelFor);
        this.inner.setValue(Frequency.MONTHLY);
        this.inner.setWidthFull();
        // CustomField only auto-syncs on the host "change" DOM event. Push the
        // new value so the binder and any value-change listeners fire as soon
        // as the inner select changes.
        this.inner.addValueChangeListener(event -> updateValue());

        add(this.inner);
    }

    private static String labelFor(Frequency frequency) {
        return switch (frequency) {
            case MONTHLY -> Translations.get("frequency.monthly");
            case QUARTERLY -> Translations.get("frequency.quarterly");
            case HALF_YEARLY -> Translations.get("frequency.halfYearly");
            case YEARLY -> Translations.get("frequency.yearly");
        };
    }

    @Override
    protected Frequency generateModelValue() {
        return this.inner.getValue();
    }

    @Override
    protected void setPresentationValue(Frequency newPresentationValue) {
        this.inner.setValue(newPresentationValue);
    }

    @Override
    public void setInvalid(boolean invalid) {
        super.setInvalid(invalid);
        this.inner.setInvalid(invalid);
    }

    /**
     * Manual setter for tests / scenario seeding.
     */
    public void setFrequency(Frequency value) {
        setPresentationValue(value);
        setModelValue(value, false);
    }

    public Select<Frequency> inner() {
        return this.inner;
    }
}
