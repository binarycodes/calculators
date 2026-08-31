package io.binarycodes.calculators.irr.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ValueSignal;
import io.binarycodes.calculators.base.common.Frequency;
import io.binarycodes.calculators.base.i18n.Translations;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.base.ui.FormCard;
import io.binarycodes.calculators.base.ui.FrequencyField;
import io.binarycodes.calculators.base.ui.MoneyField;
import io.binarycodes.calculators.base.ui.RowControls;
import io.binarycodes.calculators.base.ui.TabIndicator;
import io.binarycodes.calculators.irr.domain.DatedCashflow;
import io.binarycodes.calculators.irr.domain.RecurringCashflow;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * One side of the cashflow form — either investments (money paid in) or
 * withdrawals (money received) — split into a one-off card and a recurring card.
 * Structurally identical for both directions; the {@link Labels} supplied at
 * construction decide the wording. Each list is exposed as a {@link Signal} so
 * the parent form can fold it into the overall inputs signal.
 */
class CashflowSection extends VerticalLayout implements TabIndicator.Source {

    /** The translated strings that distinguish an investments section from a withdrawals one. */
    record Labels(String oneOffTitle, String oneOffIntro, String addOneOff,
                  String recurringTitle, String recurringIntro, String addRecurring) {
    }

    private final UserPreferences preferences;
    private final Labels labels;

    private final VerticalLayout oneOffContainer = new VerticalLayout();
    private final List<OneOffRow> oneOffRows = new ArrayList<>();
    private final ValueSignal<List<DatedCashflow>> oneOffSignal = new ValueSignal<>(List.of());

    private final VerticalLayout recurringContainer = new VerticalLayout();
    private final List<RecurringRow> recurringRows = new ArrayList<>();
    private final ValueSignal<List<RecurringCashflow>> recurringSignal = new ValueSignal<>(List.of());

    CashflowSection(UserPreferences preferences, Labels labels) {
        this.preferences = preferences;
        this.labels = labels;
        setPadding(true);
        setSpacing(true);

        add(buildOneOffCard(), buildRecurringCard());
    }

    Signal<List<DatedCashflow>> oneOffSignal() {
        return this.oneOffSignal.asReadonly();
    }

    Signal<List<RecurringCashflow>> recurringSignal() {
        return this.recurringSignal.asReadonly();
    }

    List<DatedCashflow> getOneOff() {
        return snapshotOneOff();
    }

    List<RecurringCashflow> getRecurring() {
        return snapshotRecurring();
    }

    void setOneOff(List<DatedCashflow> items) {
        this.oneOffContainer.removeAll();
        this.oneOffRows.clear();
        if (items != null) {
            items.forEach(this::addOneOffRow);
        }
        publishOneOff();
    }

    void setRecurring(List<RecurringCashflow> items) {
        this.recurringContainer.removeAll();
        this.recurringRows.clear();
        if (items != null) {
            items.forEach(this::addRecurringRow);
        }
        publishRecurring();
    }

    boolean isValid() {
        return this.oneOffRows.stream().allMatch(OneOffRow::isValid)
                && this.recurringRows.stream().allMatch(RecurringRow::isValid);
    }

    private Component buildOneOffCard() {
        final Span intro = secondary(this.labels.oneOffIntro());
        this.oneOffContainer.setPadding(false);
        this.oneOffContainer.setSpacing(true);
        this.oneOffContainer.setWidthFull();

        final Button addButton = new Button(this.labels.addOneOff(), VaadinIcon.PLUS.create(),
                event -> addOneOffRow(new DatedCashflow()));
        addButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        final FormCard card = card(this.labels.oneOffTitle(), intro, this.oneOffContainer, addButton);
        card.onClear(() -> setOneOff(List.of()));
        return card;
    }

    private Component buildRecurringCard() {
        final Span intro = secondary(this.labels.recurringIntro());
        this.recurringContainer.setPadding(false);
        this.recurringContainer.setSpacing(true);
        this.recurringContainer.setWidthFull();

        final Button addButton = new Button(this.labels.addRecurring(), VaadinIcon.PLUS.create(),
                event -> addRecurringRow(new RecurringCashflow()));
        addButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        final FormCard card = card(this.labels.recurringTitle(), intro, this.recurringContainer, addButton);
        card.onClear(() -> setRecurring(List.of()));
        return card;
    }

    private void addOneOffRow(DatedCashflow initial) {
        final OneOffRow row = new OneOffRow(this.preferences, initial, this::removeOneOffRow, this::publishOneOff);
        this.oneOffRows.add(row);
        this.oneOffContainer.add(row);
        publishOneOff();
    }

    private void removeOneOffRow(OneOffRow row) {
        if (this.oneOffRows.remove(row)) {
            this.oneOffContainer.remove(row);
            publishOneOff();
        }
    }

    private void addRecurringRow(RecurringCashflow initial) {
        final RecurringRow row = new RecurringRow(this.preferences, initial, this::removeRecurringRow, this::publishRecurring);
        this.recurringRows.add(row);
        this.recurringContainer.add(row);
        publishRecurring();
    }

    private void removeRecurringRow(RecurringRow row) {
        if (this.recurringRows.remove(row)) {
            this.recurringContainer.remove(row);
            publishRecurring();
        }
    }

    private void publishOneOff() {
        this.oneOffSignal.set(snapshotOneOff());
    }

    private void publishRecurring() {
        this.recurringSignal.set(snapshotRecurring());
    }

    private List<DatedCashflow> snapshotOneOff() {
        final List<DatedCashflow> out = new ArrayList<>();
        for (final OneOffRow row : this.oneOffRows) {
            out.add(row.snapshot());
        }
        return out;
    }

    private List<RecurringCashflow> snapshotRecurring() {
        final List<RecurringCashflow> out = new ArrayList<>();
        for (final RecurringRow row : this.recurringRows) {
            out.add(row.snapshot());
        }
        return out;
    }

    private static FormCard card(String title, Component... children) {
        final VerticalLayout content = new VerticalLayout(children);
        content.setPadding(false);
        content.setSpacing(true);

        final FormCard card = new FormCard(title);
        card.setWidthFull();
        card.add(content);
        return card;
    }

    private static Span secondary(String text) {
        final Span span = new Span(text);
        span.getStyle().setColor("var(--vaadin-secondary-text-color, #71717a)");
        return span;
    }

    private static DatePicker dateField(String label) {
        final DatePicker field = new DatePicker(label);
        field.setClearButtonVisible(true);
        return field;
    }

    private static final class OneOffRow extends HorizontalLayout {
        private final DatePicker dateField = dateField(Translations.get("field.date"));
        private final TextField descriptionField = new TextField(Translations.get("field.description"));
        private final MoneyField amountField;
        private final Binder<DatedCashflow> binder = new Binder<>(DatedCashflow.class);

        OneOffRow(UserPreferences preferences, DatedCashflow initial,
                  Consumer<OneOffRow> onRemove, Runnable onChanged) {
            this.amountField = new MoneyField(Translations.get("field.amount"), preferences);
            this.descriptionField.setValueChangeMode(ValueChangeMode.LAZY);
            this.descriptionField.setWidthFull();

            this.dateField.setValue(initial.getDate());
            this.descriptionField.setValue(initial.getDescription() == null ? "" : initial.getDescription());
            this.amountField.setValue(initial.getAmount());

            this.dateField.addValueChangeListener(event -> onChanged.run());
            this.descriptionField.addValueChangeListener(event -> onChanged.run());
            this.amountField.addValueChangeListener(event -> onChanged.run());

            final Button removeButton = RowControls.removeButton(() -> onRemove.accept(this));

            setWidthFull();
            setAlignItems(Alignment.BASELINE);
            addClassName("form-row");
            add(this.dateField, this.descriptionField, this.amountField, removeButton);
            expand(this.descriptionField);

            this.binder.forField(this.dateField).asRequired(Translations.get("irr.validation.enterDate"))
                    .bind(DatedCashflow::getDate, DatedCashflow::setDate);
            this.binder.forField(this.amountField).asRequired(Translations.get("irr.validation.enterAmount"))
                    .bind(DatedCashflow::getAmount, DatedCashflow::setAmount);
            this.binder.validate();
            this.binder.addStatusChangeListener(event -> onChanged.run());
        }

        boolean isValid() {
            return !this.dateField.isEmpty() && !this.amountField.isEmpty();
        }

        DatedCashflow snapshot() {
            return new DatedCashflow(this.dateField.getValue(), this.descriptionField.getValue(),
                    this.amountField.getValue());
        }
    }

    private static final class RecurringRow extends HorizontalLayout {
        private final DatePicker startDateField = dateField(Translations.get("field.startDate"));
        private final FrequencyField frequencyField = new FrequencyField(Translations.get("field.frequency"));
        private final IntegerField countField = countField();
        private final TextField descriptionField = new TextField(Translations.get("field.description"));
        private final MoneyField amountField;
        private final Binder<RecurringCashflow> binder = new Binder<>(RecurringCashflow.class);

        RecurringRow(UserPreferences preferences, RecurringCashflow initial,
                     Consumer<RecurringRow> onRemove, Runnable onChanged) {
            this.amountField = new MoneyField(Translations.get("field.amount"), preferences);
            this.descriptionField.setValueChangeMode(ValueChangeMode.LAZY);
            this.descriptionField.setWidthFull();

            this.frequencyField.setValue(initial.getFrequency() == null ? Frequency.MONTHLY : initial.getFrequency());

            this.startDateField.setValue(initial.getStartDate());
            this.countField.setValue(initial.getCount());
            this.descriptionField.setValue(initial.getDescription() == null ? "" : initial.getDescription());
            this.amountField.setValue(initial.getAmount());

            this.startDateField.addValueChangeListener(event -> onChanged.run());
            this.frequencyField.addValueChangeListener(event -> onChanged.run());
            this.countField.addValueChangeListener(event -> onChanged.run());
            this.descriptionField.addValueChangeListener(event -> onChanged.run());
            this.amountField.addValueChangeListener(event -> onChanged.run());

            final Button removeButton = RowControls.removeButton(() -> onRemove.accept(this));

            setWidthFull();
            setAlignItems(Alignment.BASELINE);
            addClassName("form-row");
            add(this.startDateField, this.frequencyField, this.countField, this.descriptionField,
                    this.amountField, removeButton);
            expand(this.descriptionField);

            this.binder.forField(this.startDateField).asRequired(Translations.get("irr.validation.enterDate"))
                    .bind(RecurringCashflow::getStartDate, RecurringCashflow::setStartDate);
            this.binder.forField(this.countField).asRequired(Translations.get("irr.validation.enterCount"))
                    .bind(RecurringCashflow::getCount, RecurringCashflow::setCount);
            this.binder.forField(this.amountField).asRequired(Translations.get("irr.validation.enterAmount"))
                    .bind(RecurringCashflow::getAmount, RecurringCashflow::setAmount);
            this.binder.validate();
            this.binder.addStatusChangeListener(event -> onChanged.run());
        }

        boolean isValid() {
            return !this.startDateField.isEmpty() && !this.amountField.isEmpty()
                    && this.countField.getValue() != null && this.countField.getValue() >= 1;
        }

        RecurringCashflow snapshot() {
            return new RecurringCashflow(this.startDateField.getValue(), this.frequencyField.getValue(),
                    this.countField.getValue(), this.descriptionField.getValue(), this.amountField.getValue());
        }
    }

    private static IntegerField countField() {
        final IntegerField field = new IntegerField(Translations.get("field.count"));
        field.setMin(1);
        field.setStepButtonsVisible(false);
        field.setValueChangeMode(ValueChangeMode.LAZY);
        return field;
    }

}
