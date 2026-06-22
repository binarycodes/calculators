package io.binarycodes.calculators.retirement.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ValueSignal;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.base.ui.FormCard;
import io.binarycodes.calculators.base.ui.MoneyField;
import io.binarycodes.calculators.base.ui.RowControls;
import io.binarycodes.calculators.base.ui.TabIndicator;
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
class RetirementBenefitsTab extends VerticalLayout implements TabIndicator.Source {

    private final UserPreferences prefs;
    private final VerticalLayout rowsContainer = new VerticalLayout();
    private final List<RetirementBenefitRow> rows = new ArrayList<>();
    private final ValueSignal<List<RetirementBenefit>> benefitsSignal = new ValueSignal<>(List.of());

    RetirementBenefitsTab(UserPreferences prefs) {
        this.prefs = prefs;
        setPadding(true);
        setSpacing(true);
        final Span intro = new Span("Plan retirement-period inflows (gratuities, provident fund payouts, etc) received on the retirement-age year. The tax rate is applied immediately on receipt; the net amount lands in the corpus that year.");

        intro.getStyle().setColor("var(--vaadin-secondary-text-color, #71717a)");

        this.rowsContainer.setPadding(false);
        this.rowsContainer.setSpacing(true);
        this.rowsContainer.setWidthFull();

        final Button addButton = new Button("Add benefit", VaadinIcon.PLUS.create(),
                event -> addRow(new RetirementBenefit()));
        addButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        // A section card so this tab carries its validation message at the
        // top-right like every other form card.
        final VerticalLayout inner = new VerticalLayout(intro, this.rowsContainer, addButton);
        inner.setPadding(false);
        inner.setSpacing(true);

        final FormCard card = new FormCard("Benefits");
        card.setWidthFull();
        card.addClassName("form-section");
        card.add(inner);
        add(card);
    }

    Signal<List<RetirementBenefit>> retirementBenefitsSignal() {
        return this.benefitsSignal.asReadonly();
    }

    /** Every added row must carry its required fields — an empty row is invalid. */
    boolean isValid() {
        return this.rows.stream().allMatch(RetirementBenefitRow::isValid);
    }

    List<RetirementBenefit> getRetirementBenefits() {
        return snapshotRows();
    }

    void setRetirementBenefits(List<RetirementBenefit> benefits) {
        this.rowsContainer.removeAll();
        this.rows.clear();
        if (benefits != null) {
            for (final RetirementBenefit benefit : benefits) {
                addRow(benefit);
            }
        }
        publishSnapshot();
    }

    private void addRow(RetirementBenefit benefit) {
        final RetirementBenefitRow row = new RetirementBenefitRow(this.prefs, benefit,
                this::removeRow, this::publishSnapshot);
        this.rows.add(row);
        this.rowsContainer.add(row);
        publishSnapshot();
    }

    private void removeRow(RetirementBenefitRow row) {
        if (this.rows.remove(row)) {
            this.rowsContainer.remove(row);
            publishSnapshot();
        }
    }

    private void publishSnapshot() {
        this.benefitsSignal.set(snapshotRows());
    }

    private List<RetirementBenefit> snapshotRows() {
        final List<RetirementBenefit> out = new ArrayList<>();
        for (final RetirementBenefitRow row : this.rows) {
            out.add(row.snapshot());
        }
        return out;
    }

    private static final class RetirementBenefitRow extends HorizontalLayout {
        private final TextField descriptionField = new TextField("Description");
        private final MoneyField amountField;
        private final NumberField taxField = withPercentageSuffix(percentageField("Tax Rate"));
        private final Binder<RetirementBenefit> binder = new Binder<>(RetirementBenefit.class);

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

            this.descriptionField.addValueChangeListener(event -> onChanged.run());
            this.amountField.addValueChangeListener(event -> onChanged.run());
            this.taxField.addValueChangeListener(event -> onChanged.run());

            final Button removeButton = RowControls.removeButton(() -> onRemove.accept(this));

            setWidthFull();
            setAlignItems(Alignment.BASELINE);
            addClassName("form-row");
            add(this.descriptionField, this.amountField, this.taxField, removeButton);
            expand(this.descriptionField);

            // A benefit with no amount is meaningless, so it is required; validating
            // now flags a freshly added blank row immediately.
            this.binder.forField(this.amountField).asRequired("Enter an amount")
                    .bind(RetirementBenefit::getAmount, RetirementBenefit::setAmount);
            this.binder.validate();
            // Re-publish on validity changes so the tab indicator refreshes once
            // validation has settled, not a beat before it.
            this.binder.addStatusChangeListener(event -> onChanged.run());
        }

        boolean isValid() {
            return !this.amountField.isEmpty();
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
