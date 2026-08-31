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
import io.binarycodes.calculators.retirement.domain.FutureIncome;
import io.binarycodes.calculators.retirement.domain.RecurringIncome;

import java.math.BigDecimal;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

import static io.binarycodes.calculators.retirement.ui.FormFields.percentageField;
import static io.binarycodes.calculators.retirement.ui.FormFields.withPercentageSuffix;

/**
 * Future incomes, split into two cards:
 *
 * <ul>
 *   <li><b>Fixed</b> — one-off inflows in a specific year (house sale,
 *       business liquidation, inheritance, etc.).</li>
 *   <li><b>Recurring</b> — repeating inflows (rental income, side-gig)
 *       starting in {@code year} and continuing indefinitely. The amount
 *       is per period (Monthly or Yearly) and is not inflated.</li>
 * </ul>
 */
class FutureIncomesTab extends VerticalLayout implements TabIndicator.Source {

    private final UserPreferences prefs;
    private final VerticalLayout fixedRowsContainer = new VerticalLayout();
    private final List<FutureIncomeRow> fixedRows = new ArrayList<>();
    private final VerticalLayout recurringRowsContainer = new VerticalLayout();
    private final List<RecurringIncomeRow> recurringRowsList = new ArrayList<>();
    private final ValueSignal<List<FutureIncome>> fixedSignal = new ValueSignal<>(List.of());
    private final ValueSignal<List<RecurringIncome>> recurringSignal = new ValueSignal<>(List.of());

    FutureIncomesTab(UserPreferences prefs) {
        this.prefs = prefs;
        setPadding(true);
        setSpacing(true);

        add(buildFixedCard(), buildRecurringCard());
    }

    private Component buildFixedCard() {
        final Span intro = new Span(Translations.get("retirement.futInc.fixedIntro"));
        intro.getStyle().setColor("var(--vaadin-secondary-text-color, #71717a)");

        this.fixedRowsContainer.setPadding(false);
        this.fixedRowsContainer.setSpacing(true);
        this.fixedRowsContainer.setWidthFull();

        final Button addButton = new Button(Translations.get("retirement.futInc.addFixed"), VaadinIcon.PLUS.create(),
                event -> addFixedRow(new FutureIncome()));
        addButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        final FormCard card = wrapInCard(Translations.get("section.retirement.fixed"), intro,
                this.fixedRowsContainer, addButton);
        card.onClear(() -> setFutureIncomes(List.of()));
        return card;
    }

    private Component buildRecurringCard() {
        final Span intro = new Span(Translations.get("retirement.futInc.recurringIntro"));
        intro.getStyle().setColor("var(--vaadin-secondary-text-color, #71717a)");

        this.recurringRowsContainer.setPadding(false);
        this.recurringRowsContainer.setSpacing(true);
        this.recurringRowsContainer.setWidthFull();

        final Button addButton = new Button(Translations.get("retirement.futInc.addRecurring"), VaadinIcon.PLUS.create(),
                event -> addRecurringRow(new RecurringIncome()));
        addButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        final FormCard card = wrapInCard(Translations.get("section.retirement.recurring"), intro,
                this.recurringRowsContainer, addButton);
        card.onClear(() -> setRecurringIncomes(List.of()));
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

    Signal<List<FutureIncome>> futureIncomesSignal() {
        return this.fixedSignal.asReadonly();
    }

    Signal<List<RecurringIncome>> recurringIncomesSignal() {
        return this.recurringSignal.asReadonly();
    }

    /** Every added row must carry its required fields — an empty row is invalid. */
    boolean isValid() {
        return this.fixedRows.stream().allMatch(FutureIncomeRow::isValid)
                && this.recurringRowsList.stream().allMatch(RecurringIncomeRow::isValid);
    }

    List<FutureIncome> getFutureIncomes() {
        return snapshotFixed();
    }

    void setFutureIncomes(List<FutureIncome> incomes) {
        this.fixedRowsContainer.removeAll();
        this.fixedRows.clear();
        if (incomes != null) {
            for (final FutureIncome income : incomes) {
                addFixedRow(income);
            }
        }
        publishFixedSnapshot();
    }

    List<RecurringIncome> getRecurringIncomes() {
        return snapshotRecurring();
    }

    void setRecurringIncomes(List<RecurringIncome> incomes) {
        this.recurringRowsContainer.removeAll();
        this.recurringRowsList.clear();
        if (incomes != null) {
            for (final RecurringIncome income : incomes) {
                addRecurringRow(income);
            }
        }
        publishRecurringSnapshot();
    }

    private void addFixedRow(FutureIncome income) {
        final FutureIncomeRow row = new FutureIncomeRow(this.prefs, income,
                this::removeFixedRow, this::publishFixedSnapshot);
        this.fixedRows.add(row);
        this.fixedRowsContainer.add(row);
        publishFixedSnapshot();
    }

    private void removeFixedRow(FutureIncomeRow row) {
        if (this.fixedRows.remove(row)) {
            this.fixedRowsContainer.remove(row);
            publishFixedSnapshot();
        }
    }

    private void addRecurringRow(RecurringIncome income) {
        final RecurringIncomeRow row = new RecurringIncomeRow(this.prefs, income,
                this::removeRecurringRow, this::publishRecurringSnapshot);
        this.recurringRowsList.add(row);
        this.recurringRowsContainer.add(row);
        publishRecurringSnapshot();
    }

    private void removeRecurringRow(RecurringIncomeRow row) {
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

    private List<FutureIncome> snapshotFixed() {
        final List<FutureIncome> out = new ArrayList<>();
        for (final FutureIncomeRow row : this.fixedRows) {
            out.add(row.snapshot());
        }
        return out;
    }

    private List<RecurringIncome> snapshotRecurring() {
        final List<RecurringIncome> out = new ArrayList<>();
        for (final RecurringIncomeRow row : this.recurringRowsList) {
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

    private static final class FutureIncomeRow extends HorizontalLayout {
        private final IntegerField yearField = yearField(Translations.get("field.year"));
        private final TextField descriptionField = new TextField(Translations.get("field.description"));
        private final MoneyField amountField;
        private final NumberField taxField = withPercentageSuffix(percentageField(Translations.get("field.taxRate")));
        private final Binder<FutureIncome> binder = new Binder<>(FutureIncome.class);

        FutureIncomeRow(UserPreferences prefs, FutureIncome initial,
                        java.util.function.Consumer<FutureIncomeRow> onRemove,
                        Runnable onChanged) {
            this.amountField = new MoneyField(Translations.get("field.amount"), prefs);

            this.descriptionField.setValueChangeMode(ValueChangeMode.LAZY);
            this.descriptionField.setWidthFull();

            this.yearField.setValue(initial.getYear());
            this.descriptionField.setValue(initial.getDescription() == null ? "" : initial.getDescription());
            this.amountField.setValue(initial.getAmount());
            this.taxField.setValue(initial.getTaxRatePct() == null
                    ? null : initial.getTaxRatePct().doubleValue());

            this.yearField.addValueChangeListener(event -> onChanged.run());
            this.descriptionField.addValueChangeListener(event -> onChanged.run());
            this.amountField.addValueChangeListener(event -> onChanged.run());
            this.taxField.addValueChangeListener(event -> onChanged.run());

            final Button removeButton = RowControls.removeButton(() -> onRemove.accept(this));

            setWidthFull();
            setAlignItems(Alignment.BASELINE);
            addClassName("form-row");
            add(this.yearField, this.descriptionField, this.amountField, this.taxField, removeButton);
            expand(this.descriptionField);

            // A future income needs a target year and an amount; validating now
            // flags a freshly added blank row immediately.
            this.binder.forField(this.yearField).asRequired(Translations.get("retirement.validation.enterYear"))
                    .bind(FutureIncome::getYear, FutureIncome::setYear);
            this.binder.forField(this.amountField).asRequired(Translations.get("retirement.validation.enterAmount"))
                    .bind(FutureIncome::getAmount, FutureIncome::setAmount);
            this.binder.validate();
            // Re-publish on validity changes so the tab indicator refreshes once
            // validation has settled, not a beat before it.
            this.binder.addStatusChangeListener(event -> onChanged.run());
        }

        boolean isValid() {
            return !this.yearField.isEmpty() && !this.amountField.isEmpty();
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
    }

    private static final class RecurringIncomeRow extends HorizontalLayout {
        private final IntegerField yearField = yearField(Translations.get("field.startYear"));
        private final IntegerField stopYearField = optionalStopYearField();
        private final TextField descriptionField = new TextField(Translations.get("field.description"));
        private final FrequencyField frequencyField = new FrequencyField(Translations.get("field.frequency"));
        private final MoneyField amountField;
        private final NumberField taxField = withPercentageSuffix(percentageField(Translations.get("field.taxRate")));
        private final Binder<RecurringIncome> binder = new Binder<>(RecurringIncome.class);

        RecurringIncomeRow(UserPreferences prefs, RecurringIncome initial,
                           java.util.function.Consumer<RecurringIncomeRow> onRemove,
                           Runnable onChanged) {
            this.amountField = new MoneyField(Translations.get("field.amount"), prefs);

            this.descriptionField.setValueChangeMode(ValueChangeMode.LAZY);
            this.descriptionField.setWidthFull();

            this.frequencyField.setValue(initial.getFrequency() == null
                    ? Frequency.MONTHLY : initial.getFrequency());

            this.yearField.setValue(initial.getYear());
            this.stopYearField.setValue(initial.getStopYear());
            this.descriptionField.setValue(initial.getDescription() == null ? "" : initial.getDescription());
            this.amountField.setValue(initial.getAmount());
            this.taxField.setValue(initial.getTaxRatePct() == null
                    ? null : initial.getTaxRatePct().doubleValue());

            this.yearField.addValueChangeListener(event -> onChanged.run());
            this.stopYearField.addValueChangeListener(event -> onChanged.run());
            this.descriptionField.addValueChangeListener(event -> onChanged.run());
            this.frequencyField.addValueChangeListener(event -> onChanged.run());
            this.amountField.addValueChangeListener(event -> onChanged.run());
            this.taxField.addValueChangeListener(event -> onChanged.run());

            final Button removeButton = RowControls.removeButton(() -> onRemove.accept(this));

            setWidthFull();
            setAlignItems(Alignment.BASELINE);
            addClassName("form-row");
            add(this.yearField, this.stopYearField, this.descriptionField, this.frequencyField,
                    this.amountField, this.taxField, removeButton);
            expand(this.descriptionField);

            // A recurring income needs a start year and an amount; validating now
            // flags a freshly added blank row immediately.
            this.binder.forField(this.yearField).asRequired(Translations.get("retirement.validation.enterStartYear"))
                    .bind(RecurringIncome::getYear, RecurringIncome::setYear);
            this.binder.forField(this.amountField).asRequired(Translations.get("retirement.validation.enterAmount"))
                    .bind(RecurringIncome::getAmount, RecurringIncome::setAmount);
            this.binder.validate();
            // Re-publish on validity changes so the tab indicator refreshes once
            // validation has settled, not a beat before it.
            this.binder.addStatusChangeListener(event -> onChanged.run());
        }

        boolean isValid() {
            return !this.yearField.isEmpty() && !this.amountField.isEmpty();
        }

        RecurringIncome snapshot() {
            final RecurringIncome out = new RecurringIncome();
            out.setYear(this.yearField.getValue());
            out.setStopYear(this.stopYearField.getValue());
            out.setDescription(this.descriptionField.getValue());
            out.setFrequency(this.frequencyField.getValue());
            out.setAmount(this.amountField.getValue());
            final Double taxRate = this.taxField.getValue();
            out.setTaxRatePct(taxRate == null ? null : BigDecimal.valueOf(taxRate));
            return out;
        }
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
