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
import io.binarycodes.calculators.retirement.domain.FutureExpense;

import java.math.BigDecimal;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

import static io.binarycodes.calculators.retirement.ui.FormFields.percentageField;
import static io.binarycodes.calculators.retirement.ui.FormFields.withPercentageSuffix;

/**
 * Editable list of one-off planned expenses (car purchase, wedding, knee
 * replacement…). Each row captures the target year, a short description, the
 * amount in today's money, and a per-item annual inflation rate so the
 * calculator can project it forward to the target year. State is held in
 * {@link FutureExpenseRow}-per-row form; the parent form pulls it via
 * {@link #getFutureExpenses()} and writes it back via
 * {@link #setFutureExpenses(List)}.
 */
class FutureExpensesTab extends VerticalLayout {

    private final UserPreferences prefs;
    private final VerticalLayout rowsContainer = new VerticalLayout();
    private final List<FutureExpenseRow> rows = new ArrayList<>();
    private final List<Runnable> changeListeners = new ArrayList<>();
    private boolean suppressChangeEvents;

    FutureExpensesTab(UserPreferences prefs) {
        this.prefs = prefs;
        setPadding(true);
        setSpacing(true);
final Span intro = new Span("Plan one-off expenses (home improvements, children's education, cars, medicals, etc.). Amounts are entered in today's monetary value and projected to the target year using the per-item inflation rate.");

        intro.getStyle().setColor("var(--vaadin-secondary-text-color, #71717a)");

        this.rowsContainer.setPadding(false);
        this.rowsContainer.setSpacing(true);
        this.rowsContainer.setWidthFull();

        final Button addButton = new Button("Add expense", VaadinIcon.PLUS.create(), e -> addRow(new FutureExpense()));
        addButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        add(intro, this.rowsContainer, addButton);
    }

    void addInputChangeListener(Runnable listener) {
        this.changeListeners.add(listener);
    }

    List<FutureExpense> getFutureExpenses() {
        final List<FutureExpense> out = new ArrayList<>();
        for (final FutureExpenseRow row : this.rows) {
            out.add(row.snapshot());
        }
        return out;
    }

    void setFutureExpenses(List<FutureExpense> expenses) {
        this.suppressChangeEvents = true;
        try {
            this.rowsContainer.removeAll();
            this.rows.clear();
            if (expenses != null) {
                for (final FutureExpense expense : expenses) {
                    addRow(expense);
                }
            }
        } finally {
            this.suppressChangeEvents = false;
        }
    }

    private void addRow(FutureExpense expense) {
        final FutureExpenseRow row = new FutureExpenseRow(this.prefs, expense, this::removeRow,
                this::notifyChangeListeners);
        this.rows.add(row);
        this.rowsContainer.add(row);
        notifyChangeListeners();
    }

    private void removeRow(FutureExpenseRow row) {
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

    private static final class FutureExpenseRow extends HorizontalLayout {
        private final IntegerField yearField = yearField();
        private final TextField descriptionField = new TextField("Description");
        private final MoneyField amountField;
        private final NumberField inflationField = withPercentageSuffix(percentageField("Inflation"));

        FutureExpenseRow(UserPreferences prefs, FutureExpense initial,
                         java.util.function.Consumer<FutureExpenseRow> onRemove,
                         Runnable onChanged) {
            this.amountField = new MoneyField("Amount (today)", prefs);

            this.descriptionField.setValueChangeMode(ValueChangeMode.LAZY);
            this.descriptionField.setWidthFull();

            this.yearField.setValue(initial.getYear());
            this.descriptionField.setValue(initial.getDescription() == null ? "" : initial.getDescription());
            this.amountField.setValue(initial.getAmount());
            this.inflationField.setValue(initial.getInflationPct() == null
                    ? null : initial.getInflationPct().doubleValue());

            this.yearField.addValueChangeListener(e -> onChanged.run());
            this.descriptionField.addValueChangeListener(e -> onChanged.run());
            this.amountField.addValueChangeListener(e -> onChanged.run());
            this.inflationField.addValueChangeListener(e -> onChanged.run());

            final Button removeButton = new Button(VaadinIcon.TRASH.create(), e -> onRemove.accept(this));
            removeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_ICON);
            removeButton.getElement().setAttribute("aria-label", "Remove expense");

            setWidthFull();
            setAlignItems(Alignment.BASELINE);
            add(this.yearField, this.descriptionField, this.amountField, this.inflationField, removeButton);
            expand(this.descriptionField);
        }

        FutureExpense snapshot() {
            final FutureExpense out = new FutureExpense();
            out.setYear(this.yearField.getValue());
            out.setDescription(this.descriptionField.getValue());
            out.setAmount(this.amountField.getValue());
            final Double inflation = this.inflationField.getValue();
            out.setInflationPct(inflation == null ? null : BigDecimal.valueOf(inflation));
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
