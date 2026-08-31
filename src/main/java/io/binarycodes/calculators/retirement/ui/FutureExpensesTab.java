package io.binarycodes.calculators.retirement.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ValueSignal;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.base.i18n.Translations;
import io.binarycodes.calculators.base.common.Frequency;
import io.binarycodes.calculators.base.ui.FormCard;
import io.binarycodes.calculators.base.ui.FrequencyField;
import io.binarycodes.calculators.base.ui.MoneyField;
import io.binarycodes.calculators.base.ui.RowControls;
import io.binarycodes.calculators.base.ui.TabIndicator;
import io.binarycodes.calculators.retirement.domain.FutureExpense;
import io.binarycodes.calculators.retirement.domain.RecurringExpense;

import java.math.BigDecimal;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

import static io.binarycodes.calculators.retirement.ui.FormFields.percentageField;
import static io.binarycodes.calculators.retirement.ui.FormFields.withPercentageSuffix;

/**
 * Future expenses, split into two cards:
 *
 * <ul>
 *   <li><b>Fixed</b> — one-off planned expenses in a specific year (car
 *       purchase, wedding, knee replacement, etc.).</li>
 *   <li><b>Recurring</b> — repeating expenses (rent, school fees) starting
 *       in {@code year} and continuing indefinitely. The amount is per
 *       period (Monthly or Yearly) and is projected forward at the per-item
 *       inflation rate.</li>
 * </ul>
 *
 * <p>Each list is exposed as a {@link Signal} so the parent form can
 * compose it into the overall inputs signal.</p>
 */
class FutureExpensesTab extends VerticalLayout implements TabIndicator.Source {

    private final UserPreferences prefs;
    private final VerticalLayout fixedRowsContainer = new VerticalLayout();
    private final List<FutureExpenseRow> fixedRows = new ArrayList<>();
    private final VerticalLayout recurringRowsContainer = new VerticalLayout();
    private final List<RecurringExpenseRow> recurringRowsList = new ArrayList<>();
    private final ValueSignal<List<FutureExpense>> fixedSignal = new ValueSignal<>(List.of());
    private final ValueSignal<List<RecurringExpense>> recurringSignal = new ValueSignal<>(List.of());

    FutureExpensesTab(UserPreferences prefs) {
        this.prefs = prefs;
        setPadding(true);
        setSpacing(true);

        add(buildFixedCard(), buildRecurringCard());
    }

    private Component buildFixedCard() {
        final Span intro = new Span(Translations.get("retirement.futExp.fixedIntro"));
        intro.getStyle().setColor("var(--vaadin-secondary-text-color, #71717a)");

        this.fixedRowsContainer.setPadding(false);
        this.fixedRowsContainer.setSpacing(true);
        this.fixedRowsContainer.setWidthFull();

        final Button addButton = new Button(Translations.get("retirement.futExp.addFixed"), VaadinIcon.PLUS.create(),
                event -> addFixedRow(new FutureExpense()));
        addButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        final FormCard card = wrapInCard(Translations.get("section.retirement.fixed"), intro,
                this.fixedRowsContainer, addButton);
        card.onClear(() -> setFutureExpenses(List.of()));
        return card;
    }

    private Component buildRecurringCard() {
        final Span intro = new Span(Translations.get("retirement.futExp.recurringIntro"));
        intro.getStyle().setColor("var(--vaadin-secondary-text-color, #71717a)");

        this.recurringRowsContainer.setPadding(false);
        this.recurringRowsContainer.setSpacing(true);
        this.recurringRowsContainer.setWidthFull();

        final Button addButton = new Button(Translations.get("retirement.futExp.addRecurring"), VaadinIcon.PLUS.create(),
                event -> addRecurringRow(new RecurringExpense()));
        addButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        final FormCard card = wrapInCard(Translations.get("section.retirement.recurring"), intro,
                this.recurringRowsContainer, addButton);
        card.onClear(() -> setRecurringExpenses(List.of()));
        return card;
    }

    private static FormCard wrapInCard(String title, Component... children) {
        final var inner = new VerticalLayout(children);
        inner.setPadding(false);
        inner.setSpacing(true);

        final FormCard card = new FormCard(title);
        card.setWidthFull();
        card.add(inner);
        return card;
    }

    Signal<List<FutureExpense>> futureExpensesSignal() {
        return this.fixedSignal.asReadonly();
    }

    Signal<List<RecurringExpense>> recurringExpensesSignal() {
        return this.recurringSignal.asReadonly();
    }

    /** Every added row must carry its required fields — an empty row is invalid. */
    boolean isValid() {
        return this.fixedRows.stream().allMatch(FutureExpenseRow::isValid)
                && this.recurringRowsList.stream().allMatch(RecurringExpenseRow::isValid);
    }

    List<FutureExpense> getFutureExpenses() {
        return snapshotFixed();
    }

    void setFutureExpenses(List<FutureExpense> expenses) {
        this.fixedRowsContainer.removeAll();
        this.fixedRows.clear();
        if (expenses != null) {
            for (final FutureExpense expense : expenses) {
                addFixedRow(expense);
            }
        }
        publishFixedSnapshot();
    }

    List<RecurringExpense> getRecurringExpenses() {
        return snapshotRecurring();
    }

    void setRecurringExpenses(List<RecurringExpense> expenses) {
        this.recurringRowsContainer.removeAll();
        this.recurringRowsList.clear();
        if (expenses != null) {
            for (final RecurringExpense expense : expenses) {
                addRecurringRow(expense);
            }
        }
        publishRecurringSnapshot();
    }

    private void addFixedRow(FutureExpense expense) {
        final FutureExpenseRow row = new FutureExpenseRow(this.prefs, expense,
                this::removeFixedRow, this::publishFixedSnapshot);
        this.fixedRows.add(row);
        this.fixedRowsContainer.add(row);
        publishFixedSnapshot();
    }

    private void removeFixedRow(FutureExpenseRow row) {
        if (this.fixedRows.remove(row)) {
            this.fixedRowsContainer.remove(row);
            publishFixedSnapshot();
        }
    }

    private void addRecurringRow(RecurringExpense expense) {
        final RecurringExpenseRow row = new RecurringExpenseRow(this.prefs, expense,
                this::removeRecurringRow, this::publishRecurringSnapshot);
        this.recurringRowsList.add(row);
        this.recurringRowsContainer.add(row);
        publishRecurringSnapshot();
    }

    private void removeRecurringRow(RecurringExpenseRow row) {
        if (this.recurringRowsList.remove(row)) {
            this.recurringRowsContainer.remove(row);
            publishRecurringSnapshot();
        }
    }

    private void publishFixedSnapshot() {
        this.fixedSignal.set(snapshotFixed());
    }

    private void publishRecurringSnapshot() {
        this.recurringSignal.set(snapshotRecurring());
    }

    private List<FutureExpense> snapshotFixed() {
        final List<FutureExpense> out = new ArrayList<>();
        for (final FutureExpenseRow row : this.fixedRows) {
            out.add(row.snapshot());
        }
        return out;
    }

    private List<RecurringExpense> snapshotRecurring() {
        final List<RecurringExpense> out = new ArrayList<>();
        for (final RecurringExpenseRow row : this.recurringRowsList) {
            out.add(row.snapshot());
        }
        return out;
    }

    private static IntegerField yearField(String label) {
        final IntegerField field = new IntegerField(label);
        field.setMin(Year.now().getValue());
        field.setMax(Year.now().getValue() + 100);
        field.setStepButtonsVisible(false);
        field.setValueChangeMode(ValueChangeMode.LAZY);
        return field;
    }

    private static final class FutureExpenseRow extends HorizontalLayout {
        private final IntegerField yearField = yearField(Translations.get("field.year"));
        private final TextField descriptionField = new TextField(Translations.get("field.description"));
        private final MoneyField amountField;
        private final NumberField inflationField = withPercentageSuffix(percentageField(Translations.get("field.inflation")));
        private final Binder<FutureExpense> binder = new Binder<>(FutureExpense.class);

        FutureExpenseRow(UserPreferences prefs, FutureExpense initial,
                         java.util.function.Consumer<FutureExpenseRow> onRemove,
                         Runnable onChanged) {
            this.amountField = new MoneyField(Translations.get("field.amountToday"), prefs);

            this.descriptionField.setValueChangeMode(ValueChangeMode.LAZY);
            this.descriptionField.setWidthFull();

            this.yearField.setValue(initial.getYear());
            this.descriptionField.setValue(initial.getDescription() == null ? "" : initial.getDescription());
            this.amountField.setValue(initial.getAmount());
            this.inflationField.setValue(initial.getInflationPct() == null
                    ? null : initial.getInflationPct().doubleValue());

            this.yearField.addValueChangeListener(event -> onChanged.run());
            this.descriptionField.addValueChangeListener(event -> onChanged.run());
            this.amountField.addValueChangeListener(event -> onChanged.run());
            this.inflationField.addValueChangeListener(event -> onChanged.run());

            final Button removeButton = RowControls.removeButton(() -> onRemove.accept(this));

            setWidthFull();
            setAlignItems(Alignment.BASELINE);
            addClassName("form-row");
            add(this.yearField, this.descriptionField, this.amountField, this.inflationField, removeButton);
            expand(this.descriptionField);

            // A future expense needs a target year and an amount; validating now
            // flags a freshly added blank row immediately.
            this.binder.forField(this.yearField).asRequired(Translations.get("retirement.validation.enterYear"))
                    .bind(FutureExpense::getYear, FutureExpense::setYear);
            this.binder.forField(this.amountField).asRequired(Translations.get("retirement.validation.enterAmount"))
                    .bind(FutureExpense::getAmount, FutureExpense::setAmount);
            this.binder.validate();
            // Re-publish on validity changes so the tab indicator refreshes once
            // validation has settled, not a beat before it.
            this.binder.addStatusChangeListener(event -> onChanged.run());
        }

        boolean isValid() {
            return !this.yearField.isEmpty() && !this.amountField.isEmpty();
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
    }

    private static final class RecurringExpenseRow extends HorizontalLayout {
        private final IntegerField yearField = yearField(Translations.get("field.startYear"));
        private final IntegerField stopYearField = optionalStopYearField();
        private final TextField descriptionField = new TextField(Translations.get("field.description"));
        private final FrequencyField frequencyField = new FrequencyField(Translations.get("field.frequency"));
        private final MoneyField amountField;
        private final NumberField inflationField = optionalInflationField();
        private final Binder<RecurringExpense> binder = new Binder<>(RecurringExpense.class);

        RecurringExpenseRow(UserPreferences prefs, RecurringExpense initial,
                            java.util.function.Consumer<RecurringExpenseRow> onRemove,
                            Runnable onChanged) {
            this.amountField = new MoneyField(Translations.get("field.amountToday"), prefs);

            this.descriptionField.setValueChangeMode(ValueChangeMode.LAZY);
            this.descriptionField.setWidthFull();

            this.frequencyField.setValue(initial.getFrequency() == null
                    ? Frequency.MONTHLY : initial.getFrequency());

            this.yearField.setValue(initial.getYear());
            this.stopYearField.setValue(initial.getStopYear());
            this.descriptionField.setValue(initial.getDescription() == null ? "" : initial.getDescription());
            this.amountField.setValue(initial.getAmount());
            this.inflationField.setValue(initial.getInflationPct() == null
                    ? null : initial.getInflationPct().doubleValue());

            this.yearField.addValueChangeListener(event -> onChanged.run());
            this.stopYearField.addValueChangeListener(event -> onChanged.run());
            this.descriptionField.addValueChangeListener(event -> onChanged.run());
            this.frequencyField.addValueChangeListener(event -> onChanged.run());
            this.amountField.addValueChangeListener(event -> onChanged.run());
            this.inflationField.addValueChangeListener(event -> onChanged.run());

            final Button removeButton = RowControls.removeButton(() -> onRemove.accept(this));

            setWidthFull();
            setAlignItems(Alignment.BASELINE);
            addClassName("form-row");
            add(this.yearField, this.stopYearField, this.descriptionField, this.frequencyField,
                    this.amountField, this.inflationField, removeButton);
            expand(this.descriptionField);

            // A recurring expense needs a start year and an amount; validating now
            // flags a freshly added blank row immediately.
            this.binder.forField(this.yearField).asRequired(Translations.get("retirement.validation.enterStartYear"))
                    .bind(RecurringExpense::getYear, RecurringExpense::setYear);
            this.binder.forField(this.amountField).asRequired(Translations.get("retirement.validation.enterAmount"))
                    .bind(RecurringExpense::getAmount, RecurringExpense::setAmount);
            this.binder.validate();
            // Re-publish on validity changes so the tab indicator refreshes once
            // validation has settled, not a beat before it.
            this.binder.addStatusChangeListener(event -> onChanged.run());
        }

        boolean isValid() {
            return !this.yearField.isEmpty() && !this.amountField.isEmpty();
        }

        RecurringExpense snapshot() {
            final RecurringExpense out = new RecurringExpense();
            out.setYear(this.yearField.getValue());
            out.setStopYear(this.stopYearField.getValue());
            out.setDescription(this.descriptionField.getValue());
            out.setFrequency(this.frequencyField.getValue());
            out.setAmount(this.amountField.getValue());
            final Double inflation = this.inflationField.getValue();
            out.setInflationPct(inflation == null ? null : BigDecimal.valueOf(inflation));
            return out;
        }
    }

    private static NumberField optionalInflationField() {
        final NumberField field = withPercentageSuffix(percentageField(Translations.get("field.inflation")));
        field.setPlaceholder(Translations.get("retirement.futExp.overallRatePlaceholder"));
        field.setHelperText(Translations.get("retirement.futExp.overallRateHelper"));
        field.setClearButtonVisible(true);
        return field;
    }

    private static IntegerField optionalStopYearField() {
        final IntegerField field = new IntegerField(Translations.get("field.stopYear"));
        field.setMin(Year.now().getValue());
        field.setMax(Year.now().getValue() + 100);
        field.setStepButtonsVisible(false);
        field.setValueChangeMode(ValueChangeMode.LAZY);
        field.setPlaceholder(Translations.get("retirement.futExp.foreverPlaceholder"));
        field.setHelperText(Translations.get("retirement.futExp.noEndHelper"));
        field.setClearButtonVisible(true);
        return field;
    }
}
