package io.binarycodes.calculators.inflation.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.card.Card;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.BinderValidationStatus;
import com.vaadin.flow.data.binder.Result;
import com.vaadin.flow.data.binder.ValidationResult;
import com.vaadin.flow.data.converter.Converter;
import com.vaadin.flow.data.validator.BigDecimalRangeValidator;
import com.vaadin.flow.data.validator.DoubleRangeValidator;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.signals.Signal;
import io.binarycodes.calculators.base.common.TimeHorizonMode;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.base.ui.MoneyField;
import io.binarycodes.calculators.inflation.domain.InflationInputs;

import java.math.BigDecimal;
import java.time.Month;
import java.time.Year;
import java.util.List;

/**
 * Single-card input form for the inflation projection: amount + inflation rate,
 * a "today's money" toggle that flips the projection direction, and the shared
 * Years / Ages / Target-Year horizon selector.
 */
public class InflationCalculatorForm extends VerticalLayout {

    private final Binder<InflationInputs> binder = new Binder<>(InflationInputs.class);

    private final MoneyField amount;
    private final NumberField inflationRate = percentageField("Inflation Rate");
    private final Checkbox amountIsToday = new Checkbox("Amount is in today's money");
    private final RadioButtonGroup<TimeHorizonMode> horizonMode = new RadioButtonGroup<>();

    private final IntegerField years = yearsField("Years");
    private final IntegerField months = monthsField("Months");
    private final IntegerField currentAge = ageField("Current Age");
    private final IntegerField goalAge = ageField("Goal Age");
    private final IntegerField targetYear = targetYearField();
    private final Select<Month> targetMonth = monthSelect();

    private final FormLayout horizonFields = new FormLayout();
    private final Signal<InflationInputs> inputsSignal;

    public InflationCalculatorForm(UserPreferences preferences) {
        addClassName("inflation-form");
        setPadding(false);
        setSpacing(true);
        setWidthFull();

        this.amount = new MoneyField("Amount", preferences);

        configureHorizonModeGroup();
        configureBindings();
        renderHorizonFieldsFor(this.horizonMode.getValue());

        add(buildCard());

        this.inputsSignal = Signal.computed(() -> {
            this.amountSignal.get();
            this.inflationSignal.get();
            this.amountIsTodaySignal.get();
            this.horizonModeSignal.get();
            this.yearsSignal.get();
            this.monthsSignal.get();
            this.currentAgeSignal.get();
            this.goalAgeSignal.get();
            this.targetYearSignal.get();
            this.targetMonthSignal.get();
            return buildInputs();
        });
    }

    public Signal<InflationInputs> inputsSignal() {
        return this.inputsSignal;
    }

    public void setInputs(InflationInputs inputs) {
        if (inputs.getHorizonMode() == null) {
            inputs.setHorizonMode(TimeHorizonMode.YEARS);
        }
        this.binder.readBean(inputs);
        renderHorizonFieldsFor(inputs.getHorizonMode());
    }

    public InflationInputs getInputs() {
        return buildInputs();
    }

    public boolean isValid() {
        return this.binder.isValid();
    }

    public BinderValidationStatus<InflationInputs> validate() {
        return this.binder.validate();
    }

    private InflationInputs buildInputs() {
        final var target = new InflationInputs();
        this.binder.writeBeanAsDraft(target);
        if (target.getHorizonMode() == null) {
            target.setHorizonMode(TimeHorizonMode.YEARS);
        }
        return target;
    }

    private Component buildCard() {
        final FormLayout topLayout = sectionForm();
        topLayout.add(this.amount, withPercentageSuffix(this.inflationRate), this.amountIsToday);

        this.horizonFields.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("36em", 2));
        this.horizonFields.setWidthFull();

        final Span horizonLabel = new Span("Time horizon");
        horizonLabel.addClassName("subsection-label");

        final VerticalLayout content = new VerticalLayout(
                topLayout, horizonLabel, this.horizonMode, this.horizonFields);
        content.setPadding(false);
        content.setSpacing(true);

        final Card card = new Card();
        card.setTitle("Inflation");
        card.add(content);
        card.setWidthFull();
        card.addClassName("form-section");
        return card;
    }

    private void configureHorizonModeGroup() {
        this.horizonMode.setItems(TimeHorizonMode.values());
        this.horizonMode.setItemLabelGenerator(InflationCalculatorForm::horizonModeLabel);
        this.horizonMode.setValue(TimeHorizonMode.YEARS);
        this.horizonMode.addClassName("segmented-toggle");
        this.horizonMode.addValueChangeListener(event -> {
            renderHorizonFieldsFor(event.getValue());
            this.binder.validate();
        });
    }

    private void renderHorizonFieldsFor(TimeHorizonMode mode) {
        this.horizonFields.removeAll();
        final TimeHorizonMode resolved = mode == null ? TimeHorizonMode.YEARS : mode;
        switch (resolved) {
            case YEARS -> this.horizonFields.add(this.years, this.months);
            case AGES -> this.horizonFields.add(this.currentAge, this.goalAge);
            case TARGET_YEAR -> this.horizonFields.add(this.targetYear, this.targetMonth);
        }
    }

    private Signal<?> amountSignal;
    private Signal<?> inflationSignal;
    private Signal<?> amountIsTodaySignal;
    private Signal<?> horizonModeSignal;
    private Signal<?> yearsSignal;
    private Signal<?> monthsSignal;
    private Signal<?> currentAgeSignal;
    private Signal<?> goalAgeSignal;
    private Signal<?> targetYearSignal;
    private Signal<?> targetMonthSignal;

    private void configureBindings() {
        this.amountSignal = this.binder.forField(this.amount)
                .asRequired("Required")
                .withValidator(new BigDecimalRangeValidator("Must be non-negative",
                        BigDecimal.ZERO, null))
                .bind(InflationInputs::getAmount, InflationInputs::setAmount)
                .valueSignal();

        this.inflationSignal = this.binder.forField(this.inflationRate)
                .asRequired("Required")
                .withValidator(new DoubleRangeValidator("Must be between 0 and 100", 0d, 100d))
                .withConverter(doubleToBigDecimalConverter())
                .bind(InflationInputs::getInflationRatePct, InflationInputs::setInflationRatePct)
                .valueSignal();

        this.amountIsTodaySignal = this.binder.forField(this.amountIsToday)
                .bind(InflationInputs::isAmountIsToday, InflationInputs::setAmountIsToday)
                .valueSignal();

        this.horizonModeSignal = this.binder.forField(this.horizonMode)
                .bind(InflationInputs::getHorizonMode, InflationInputs::setHorizonMode)
                .valueSignal();

        this.yearsSignal = this.binder.forField(this.years)
                .withValidator((value, context) -> {
                    if (this.horizonMode.getValue() == TimeHorizonMode.YEARS) {
                        final int extraMonths = this.months.getValue() == null ? 0 : this.months.getValue();
                        if (value == null) {
                            return ValidationResult.error("Required");
                        }
                        if (value < 0 || value > 100) {
                            return ValidationResult.error("Must be between 0 and 100");
                        }
                        if (value == 0 && extraMonths == 0) {
                            return ValidationResult.error("Years + months must be at least one month");
                        }
                    }
                    return ValidationResult.ok();
                })
                .bind(InflationInputs::getYearsToGoal, InflationInputs::setYearsToGoal)
                .valueSignal();

        this.monthsSignal = this.binder.forField(this.months)
                .withValidator((value, context) -> {
                    if (this.horizonMode.getValue() == TimeHorizonMode.YEARS
                            && value != null && (value < 0 || value > 11)) {
                        return ValidationResult.error("Must be between 0 and 11");
                    }
                    return ValidationResult.ok();
                })
                .bind(InflationInputs::getMonthsToGoal, InflationInputs::setMonthsToGoal)
                .valueSignal();

        this.currentAgeSignal = this.binder.forField(this.currentAge)
                .withValidator((value, context) -> {
                    if (this.horizonMode.getValue() == TimeHorizonMode.AGES) {
                        if (value == null) {
                            return ValidationResult.error("Required");
                        }
                        if (value < 1 || value > 120) {
                            return ValidationResult.error("Must be between 1 and 120");
                        }
                    }
                    return ValidationResult.ok();
                })
                .bind(InflationInputs::getCurrentAge, InflationInputs::setCurrentAge)
                .valueSignal();

        this.goalAgeSignal = this.binder.forField(this.goalAge)
                .withValidator((value, context) -> {
                    if (this.horizonMode.getValue() != TimeHorizonMode.AGES) {
                        return ValidationResult.ok();
                    }
                    if (value == null) {
                        return ValidationResult.error("Required");
                    }
                    if (value < 1 || value > 120) {
                        return ValidationResult.error("Must be between 1 and 120");
                    }
                    final Integer from = this.currentAge.getValue();
                    if (from != null && value <= from) {
                        return ValidationResult.error("Must be greater than current age");
                    }
                    return ValidationResult.ok();
                })
                .bind(InflationInputs::getGoalAge, InflationInputs::setGoalAge)
                .valueSignal();

        this.targetYearSignal = this.binder.forField(this.targetYear)
                .withValidator((value, context) -> {
                    if (this.horizonMode.getValue() != TimeHorizonMode.TARGET_YEAR) {
                        return ValidationResult.ok();
                    }
                    if (value == null) {
                        return ValidationResult.error("Required");
                    }
                    final int currentYear = Year.now().getValue();
                    final int currentMonth = java.time.LocalDate.now().getMonthValue();
                    if (value < currentYear) {
                        return ValidationResult.error("Must be " + currentYear + " or later");
                    }
                    final Month picked = this.targetMonth.getValue();
                    if (value.intValue() == currentYear && picked != null
                            && picked.getValue() <= currentMonth) {
                        return ValidationResult.error("Target must be in the future");
                    }
                    return ValidationResult.ok();
                })
                .bind(InflationInputs::getTargetYear, InflationInputs::setTargetYear)
                .valueSignal();

        this.targetMonthSignal = this.binder.forField(this.targetMonth)
                .withConverter(
                        month -> month == null ? null : month.getValue(),
                        value -> value == null ? null : Month.of(value))
                .bind(InflationInputs::getTargetMonth, InflationInputs::setTargetMonth)
                .valueSignal();

        this.horizonMode.addValueChangeListener(event -> this.binder.validate());
        this.currentAge.addValueChangeListener(event -> this.binder.validate());
        this.months.addValueChangeListener(event -> this.binder.validate());
        this.targetMonth.addValueChangeListener(event -> this.binder.validate());
    }

    private static Converter<Double, BigDecimal> doubleToBigDecimalConverter() {
        return Converter.from(
                value -> Result.ok(value == null ? null : BigDecimal.valueOf(value)),
                value -> value == null ? null : value.doubleValue());
    }

    private static String horizonModeLabel(TimeHorizonMode mode) {
        return switch (mode) {
            case YEARS -> "Years";
            case AGES -> "Ages";
            case TARGET_YEAR -> "Target Year";
        };
    }

    private static FormLayout sectionForm() {
        final FormLayout layout = new FormLayout();
        layout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("36em", 2),
                new FormLayout.ResponsiveStep("64em", 3));
        return layout;
    }

    private static IntegerField ageField(String label) {
        final IntegerField field = new IntegerField(label);
        field.setMin(1);
        field.setMax(120);
        field.setStepButtonsVisible(false);
        field.setSuffixComponent(secondaryText("yrs"));
        field.setValueChangeMode(ValueChangeMode.LAZY);
        return field;
    }

    private static IntegerField yearsField(String label) {
        final IntegerField field = new IntegerField(label);
        field.setMin(0);
        field.setMax(100);
        field.setStepButtonsVisible(false);
        field.setSuffixComponent(secondaryText("yrs"));
        field.setValueChangeMode(ValueChangeMode.LAZY);
        return field;
    }

    private static IntegerField monthsField(String label) {
        final IntegerField field = new IntegerField(label);
        field.setMin(0);
        field.setMax(11);
        field.setStepButtonsVisible(false);
        field.setSuffixComponent(secondaryText("mo"));
        field.setValueChangeMode(ValueChangeMode.LAZY);
        return field;
    }

    private static Select<Month> monthSelect() {
        final Select<Month> select = new Select<>();
        select.setLabel("Target Month");
        select.setItems(List.of(Month.values()));
        select.setItemLabelGenerator(month -> month == null ? "" : monthLabel(month));
        return select;
    }

    private static String monthLabel(Month month) {
        final String name = month.name();
        return name.charAt(0) + name.substring(1).toLowerCase();
    }

    private static IntegerField targetYearField() {
        final IntegerField field = new IntegerField("Target Year");
        field.setMin(Year.now().getValue());
        field.setMax(Year.now().getValue() + 100);
        field.setStepButtonsVisible(false);
        field.setValueChangeMode(ValueChangeMode.LAZY);
        return field;
    }

    private static NumberField percentageField(String label) {
        final NumberField field = new NumberField(label);
        field.setMin(0);
        field.setMax(100);
        field.setStep(0.1);
        field.setStepButtonsVisible(false);
        field.setValueChangeMode(ValueChangeMode.LAZY);
        return field;
    }

    private static NumberField withPercentageSuffix(NumberField field) {
        if (field.getSuffixComponent() == null) {
            field.setSuffixComponent(secondaryText("%"));
        }
        return field;
    }

    private static Span secondaryText(String text) {
        final Span span = new Span(text);
        span.getStyle().setColor("var(--vaadin-secondary-text-color, #71717a)");
        return span;
    }
}
