package io.binarycodes.calculators.retirement.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.base.ui.MoneyField;
import io.binarycodes.calculators.retirement.domain.FutureIncome;

import java.math.BigDecimal;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

import static io.binarycodes.calculators.retirement.ui.FormFields.percentageField;
import static io.binarycodes.calculators.retirement.ui.FormFields.withPercentageSuffix;

/**
 * Editable list of one-off future inflows (house sale, business
 * liquidation, inheritance, windfalls…). Each row captures the target
 * year, a short description, the gross amount expected in that year, and
 * a tax rate applied immediately on receipt. The net amount lands in the
 * main corpus at the start of the target year and grows thereafter at
 * the main corpus's growth rate.
 */
class FutureIncomesTab extends VerticalLayout {

    private final UserPreferences prefs;
    private final VerticalLayout rowsContainer = new VerticalLayout();
    private final List<FutureIncomeRow> rows = new ArrayList<>();
    private final List<Runnable> changeListeners = new ArrayList<>();
    private boolean suppressChangeEvents;

    FutureIncomesTab(UserPreferences prefs) {
        this.prefs = prefs;
        setPadding(true);
        setSpacing(true);

        final Span intro = new Span(
                "Plan one-off future inflows (house sale, business "
                        + "liquidation, inheritance, windfalls…). Amounts are the "
                        + "nominal value at the target year; the tax rate is applied "
                        + "immediately on receipt and the net lands in the corpus.");
        intro.getStyle().setColor("var(--vaadin-secondary-text-color, #71717a)");

        this.rowsContainer.setPadding(false);
        this.rowsContainer.setSpacing(true);
        this.rowsContainer.setWidthFull();

        final Button addButton = new Button("Add income", VaadinIcon.PLUS.create(),
                e -> addRow(new FutureIncome()));
        addButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        add(intro, this.rowsContainer, addButton);
    }

    void addInputChangeListener(Runnable listener) {
        this.changeListeners.add(listener);
    }

    List<FutureIncome> getFutureIncomes() {
        final List<FutureIncome> out = new ArrayList<>();
        for (final FutureIncomeRow row : this.rows) {
            out.add(row.snapshot());
        }
        return out;
    }

    void setFutureIncomes(List<FutureIncome> incomes) {
        this.suppressChangeEvents = true;
        try {
            this.rowsContainer.removeAll();
            this.rows.clear();
            if (incomes != null) {
                for (final FutureIncome income : incomes) {
                    addRow(income);
                }
            }
        } finally {
            this.suppressChangeEvents = false;
        }
    }

    private void addRow(FutureIncome income) {
        final FutureIncomeRow row = new FutureIncomeRow(this.prefs, income, this::removeRow,
                this::notifyChangeListeners);
        this.rows.add(row);
        this.rowsContainer.add(row);
        notifyChangeListeners();
    }

    private void removeRow(FutureIncomeRow row) {
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

    private static final class FutureIncomeRow extends HorizontalLayout {
        private final IntegerField yearField = yearField();
        private final TextField descriptionField = new TextField("Description");
        private final MoneyField amountField;
        private final NumberField taxField = withPercentageSuffix(percentageField("Tax Rate"));

        FutureIncomeRow(UserPreferences prefs, FutureIncome initial,
                        java.util.function.Consumer<FutureIncomeRow> onRemove,
                        Runnable onChanged) {
            this.amountField = new MoneyField("Amount", prefs);

            this.descriptionField.setValueChangeMode(ValueChangeMode.LAZY);
            this.descriptionField.setWidthFull();

            this.yearField.setValue(initial.getYear());
            this.descriptionField.setValue(initial.getDescription() == null ? "" : initial.getDescription());
            this.amountField.setValue(initial.getAmount());
            this.taxField.setValue(initial.getTaxRatePct() == null
                    ? null : initial.getTaxRatePct().doubleValue());

            this.yearField.addValueChangeListener(e -> onChanged.run());
            this.descriptionField.addValueChangeListener(e -> onChanged.run());
            this.amountField.addValueChangeListener(e -> onChanged.run());
            this.taxField.addValueChangeListener(e -> onChanged.run());

            final Button removeButton = new Button(VaadinIcon.TRASH.create(), e -> onRemove.accept(this));
            removeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_ICON);
            removeButton.getElement().setAttribute("aria-label", "Remove income");

            setWidthFull();
            setAlignItems(Alignment.BASELINE);
            add(this.yearField, this.descriptionField, this.amountField, this.taxField, removeButton);
            expand(this.descriptionField);
        }

        FutureIncome snapshot() {
            final FutureIncome out = new FutureIncome();
            out.setYear(this.yearField.getValue());
            out.setDescription(this.descriptionField.getValue());
            out.setAmount(this.amountField.getValue());
            final Double taxRate = this.taxField.getValue();
            out.setTaxRatePct(taxRate == null ? null : BigDecimal.valueOf(taxRate));
            return out;
        }

        private static IntegerField yearField() {
            final IntegerField field = new IntegerField("Year");
            field.setMin(Year.now().getValue());
            field.setMax(Year.now().getValue() + 100);
            field.setStepButtonsVisible(false);
            field.setValueChangeMode(ValueChangeMode.LAZY);
            return field;
        }
    }
}
