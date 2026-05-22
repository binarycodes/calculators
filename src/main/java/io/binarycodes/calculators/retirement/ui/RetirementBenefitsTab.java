package io.binarycodes.calculators.retirement.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.base.ui.MoneyField;
import io.binarycodes.calculators.retirement.domain.RetirementBenefit;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static io.binarycodes.calculators.retirement.ui.FormFields.percentageField;
import static io.binarycodes.calculators.retirement.ui.FormFields.withPercentageSuffix;

/**
 * Editable list of one-off retirement-period inflows (pensions, gratuities,
 * social security lump sums, annuity payouts). Each row captures target
 * year, description, gross amount, and a per-item tax rate; the net amount
 * after tax is added to the corpus in the target year.
 */
class RetirementBenefitsTab extends VerticalLayout {

    private final UserPreferences prefs;
    private final VerticalLayout rowsContainer = new VerticalLayout();
    private final List<RetirementBenefitRow> rows = new ArrayList<>();
    private final List<Runnable> changeListeners = new ArrayList<>();
    private boolean suppressChangeEvents;

    RetirementBenefitsTab(UserPreferences prefs) {
        this.prefs = prefs;
        setPadding(true);
        setSpacing(true);

        final Span intro = new Span(
                "Plan retirement-period inflows (pensions, gratuities, "
                        + "annuity payouts…) received on the retirement-age year. "
                        + "The tax rate is applied immediately on receipt; the "
                        + "net amount lands in the corpus that year.");
        intro.getStyle().setColor("var(--vaadin-secondary-text-color, #71717a)");

        this.rowsContainer.setPadding(false);
        this.rowsContainer.setSpacing(true);
        this.rowsContainer.setWidthFull();

        final Button addButton = new Button("Add benefit", VaadinIcon.PLUS.create(),
                e -> addRow(new RetirementBenefit()));
        addButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        add(intro, this.rowsContainer, addButton);
    }

    void addInputChangeListener(Runnable listener) {
        this.changeListeners.add(listener);
    }

    List<RetirementBenefit> getRetirementBenefits() {
        final List<RetirementBenefit> out = new ArrayList<>();
        for (final RetirementBenefitRow row : this.rows) {
            out.add(row.snapshot());
        }
        return out;
    }

    void setRetirementBenefits(List<RetirementBenefit> benefits) {
        this.suppressChangeEvents = true;
        try {
            this.rowsContainer.removeAll();
            this.rows.clear();
            if (benefits != null) {
                for (final RetirementBenefit benefit : benefits) {
                    addRow(benefit);
                }
            }
        } finally {
            this.suppressChangeEvents = false;
        }
    }

    private void addRow(RetirementBenefit benefit) {
        final RetirementBenefitRow row = new RetirementBenefitRow(this.prefs, benefit, this::removeRow,
                this::notifyChangeListeners);
        this.rows.add(row);
        this.rowsContainer.add(row);
        notifyChangeListeners();
    }

    private void removeRow(RetirementBenefitRow row) {
        if (this.rows.remove(row)) {
            this.rowsContainer.remove(row);
            notifyChangeListeners();
        }
    }

    private void notifyChangeListeners() {
        if (this.suppressChangeEvents) {
            return;
        }
        this.changeListeners.forEach(Runnable::run);
    }

    private static final class RetirementBenefitRow extends HorizontalLayout {
        private final TextField descriptionField = new TextField("Description");
        private final MoneyField amountField;
        private final NumberField taxField = withPercentageSuffix(percentageField("Tax Rate"));

        RetirementBenefitRow(UserPreferences prefs, RetirementBenefit initial,
                             java.util.function.Consumer<RetirementBenefitRow> onRemove,
                             Runnable onChanged) {
            this.amountField = new MoneyField("Amount", prefs);

            this.descriptionField.setValueChangeMode(ValueChangeMode.LAZY);
            this.descriptionField.setWidthFull();

            this.descriptionField.setValue(initial.getDescription() == null ? "" : initial.getDescription());
            this.amountField.setValue(initial.getAmount());
            this.taxField.setValue(initial.getTaxRatePct() == null
                    ? null : initial.getTaxRatePct().doubleValue());

            this.descriptionField.addValueChangeListener(e -> onChanged.run());
            this.amountField.addValueChangeListener(e -> onChanged.run());
            this.taxField.addValueChangeListener(e -> onChanged.run());

            final Button removeButton = new Button(VaadinIcon.TRASH.create(), e -> onRemove.accept(this));
            removeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_ICON);
            removeButton.getElement().setAttribute("aria-label", "Remove benefit");

            setWidthFull();
            setAlignItems(Alignment.BASELINE);
            add(this.descriptionField, this.amountField, this.taxField, removeButton);
            expand(this.descriptionField);
        }

        RetirementBenefit snapshot() {
            final RetirementBenefit out = new RetirementBenefit();
            out.setDescription(this.descriptionField.getValue());
            out.setAmount(this.amountField.getValue());
            final Double taxRate = this.taxField.getValue();
            out.setTaxRatePct(taxRate == null ? null : BigDecimal.valueOf(taxRate));
            return out;
        }
    }
}
