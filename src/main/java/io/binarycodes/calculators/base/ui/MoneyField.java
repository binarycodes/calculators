package io.binarycodes.calculators.base.ui;

import com.vaadin.flow.component.customfield.CustomField;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.data.value.ValueChangeMode;
import io.binarycodes.calculators.base.money.NumberToWords;
import io.binarycodes.calculators.base.prefs.UserPreferences;

import java.math.BigDecimal;

/**
 * A money input bound to a {@link BigDecimal}. The currency symbol is shown as
 * a prefix, and a helper text below the field renders the amount in words
 * (Indian or Western, depending on the session-active currency).
 *
 * <p>The component subscribes to {@link UserPreferences} so prefix and
 * helper-text language update live when the user switches currency.</p>
 */
public class MoneyField extends CustomField<BigDecimal> {

    private final NumberField inner = new NumberField();
    private final Span prefix = new Span();
    private final UserPreferences prefs;

    public MoneyField(String label, UserPreferences prefs) {
        this.prefs = prefs;
        setLabel(label);

        this.inner.setStep(1);
        this.inner.setStepButtonsVisible(false);
        this.inner.setWidthFull();
        this.inner.setPrefixComponent(this.prefix);
        this.inner.setValueChangeMode(ValueChangeMode.LAZY);
        this.inner.addValueChangeListener(e -> {
            updateHelperText();
            // CustomField only auto-syncs on the host "change" DOM event (blur on
            // number inputs). Explicitly push the new value so the binder and
            // any value-change listeners fire as soon as the inner field's
            // value-change fires (i.e. after the LAZY typing-pause debounce).
            updateValue();
        });

        add(this.inner);
        prefs.addChangeListener(p -> applyCurrency());
        applyCurrency();
    }

    /**
     * Currency-aware presentation. Called on prefs change & on construction.
     */
    private void applyCurrency() {
        this.prefix.setText(this.prefs.currency().symbol());
        updateHelperText();
    }

    private void updateHelperText() {
        final Double v = this.inner.getValue();
        if (v == null) {
            this.inner.setHelperText("");
        } else {
            this.inner.setHelperText(NumberToWords.amountInWords(
                    BigDecimal.valueOf(v), this.prefs.currency()));
        }
    }

    @Override
    protected BigDecimal generateModelValue() {
        final Double v = this.inner.getValue();
        return v == null ? null : BigDecimal.valueOf(v);
    }

    @Override
    protected void setPresentationValue(BigDecimal newPresentationValue) {
        this.inner.setValue(newPresentationValue == null
                ? null
                : newPresentationValue.doubleValue());
        updateHelperText();
    }

    /**
     * Manual setter for tests / scenario seeding.
     */
    public void setBigDecimal(BigDecimal value) {
        setPresentationValue(value);
        setModelValue(value, false);
    }

    public NumberField inner() {
        return this.inner;
    }
}
