package io.binarycodes.calculators.inflation.ui;

import com.vaadin.flow.component.Component;
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
import io.binarycodes.calculators.base.i18n.Translations;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.base.ui.CalculatorForm;
import io.binarycodes.calculators.base.ui.FormCard;
import io.binarycodes.calculators.base.ui.MoneyField;
import io.binarycodes.calculators.base.ui.PercentageField;
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
public class InflationCalculatorForm extends VerticalLayout implements CalculatorForm<InflationInputs> {

    private final Binder<InflationInputs> binder = new Binder<>(InflationInputs.class);

    private final MoneyField amount;
    private final NumberField inflationRate = PercentageField.create(Translations.get("field.inflationRate"));
    private final NumberField inflationVariation = PercentageField.create(Translations.get("field.inflationVariation"));
    private final Checkbox amountIsToday = new Checkbox(Translations.get("field.amountIsToday"));
    private final RadioButtonGroup<TimeHorizonMode> horizonMode = new RadioButtonGroup<>();

    private final IntegerField years = yearsField(Translations.get("field.years"));
    private final IntegerField months = monthsField(Translations.get("field.months"));
    private final IntegerField currentAge = ageField(Translations.get("field.currentAge"));
    private final IntegerField goalAge = ageField(Translations.get("field.goalAge"));
    private final IntegerField targetYear = targetYearField();
    private final Select<Month> targetMonth = monthSelect();

    private final FormLayout horizonFields = new FormLayout();
    private FormCard inflationCard;
    private final Signal<InflationInputs> inputsSignal;

    public InflationCalculatorForm(UserPreferences preferences) {
        addClassName("inflation-form");
        setPadding(false);
        setSpacing(true);
        setWidthFull();

        this.amount = new MoneyField(Translations.get("field.amount"), preferences);

        configureHorizonModeGroup();
        configureBindings();
        renderHorizonFieldsFor(this.horizonMode.getValue());

        add(buildCard());

        this.inputsSignal = Signal.computed(() -> {
            this.amountSignal.get();
            this.inflationSignal.get();
            this.inflationVariationSignal.get();
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

    public void clear() {
        setInputs(new InflationInputs());
    }

    public InflationInputs getInputs() {
        return buildInputs();
    }

    @Override
    public void showValidationMessages(String calculationError) {
        FormCard.refreshGenericErrors(this);
        if (calculationError != null && !this.inflationCard.hasInvalidField()) {
            this.inflationCard.showError(calculationError);
        }
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
        topLayout.add(this.amount, withPercentageSuffix(this.inflationRate),
                withPercentageSuffix(this.inflationVariation), this.amountIsToday);

        this.horizonFields.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("36em", 2));
        this.horizonFields.setWidthFull();

        final Span horizonLabel = new Span(Translations.get("timeHorizon.label"));
        horizonLabel.addClassName("subsection-label");

        final VerticalLayout content = new VerticalLayout(
                topLayout, horizonLabel, this.horizonMode, this.horizonFields);
        content.setPadding(false);
        content.setSpacing(true);

        final FormCard card = new FormCard(Translations.get("section.inflation"));
        card.add(content);
        card.setWidthFull();
        card.addClassName("form-section");
        this.inflationCard = card;
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
    private Signal<?> inflationVariationSignal;
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
                .asRequired(Translations.get("validation.required"))
                .withValidator(new BigDecimalRangeValidator(Translations.get("validation.nonNegative"),
                        BigDecimal.ZERO, null))
                .bind(InflationInputs::getAmount, InflationInputs::setAmount)
                .valueSignal();

        this.inflationSignal = this.binder.forField(this.inflationRate)
                .asRequired(Translations.get("validation.required"))
                .withValidator(new DoubleRangeValidator(Translations.get("validation.between", 0, 100), 0d, 100d))
                .withConverter(doubleToBigDecimalConverter())
                .bind(InflationInputs::getInflationRatePct, InflationInputs::setInflationRatePct)
                .valueSignal();

        this.inflationVariationSignal = this.binder.forField(this.inflationVariation)
                .withValidator(new DoubleRangeValidator(Translations.get("validation.between", 0, 20), 0d, 20d))
                .withConverter(doubleToBigDecimalConverter())
                .bind(InflationInputs::getInflationVariationPct, InflationInputs::setInflationVariationPct)
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
                            return ValidationResult.error(Translations.get("validation.required"));
                        }
                        if (value < 0 || value > 100) {
                            return ValidationResult.error(Translations.get("validation.between", 0, 100));
                        }
                        if (value == 0 && extraMonths == 0) {
                            return ValidationResult.error(Translations.get("validation.atLeastOneMonth"));
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
                        return ValidationResult.error(Translations.get("validation.between", 0, 11));
                    }
                    return ValidationResult.ok();
                })
                .bind(InflationInputs::getMonthsToGoal, InflationInputs::setMonthsToGoal)
                .valueSignal();

        this.currentAgeSignal = this.binder.forField(this.currentAge)
                .withValidator((value, context) -> {
                    if (this.horizonMode.getValue() == TimeHorizonMode.AGES) {
                        if (value == null) {
                            return ValidationResult.error(Translations.get("validation.required"));
                        }
                        if (value < 1 || value > 120) {
                            return ValidationResult.error(Translations.get("validation.between", 1, 120));
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
                        return ValidationResult.error(Translations.get("validation.required"));
                    }
                    if (value < 1 || value > 120) {
                        return ValidationResult.error(Translations.get("validation.between", 1, 120));
                    }
                    final Integer from = this.currentAge.getValue();
                    if (from != null && value <= from) {
                        return ValidationResult.error(Translations.get("validation.greaterThanCurrentAge"));
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
                        return ValidationResult.error(Translations.get("validation.required"));
                    }
                    final int currentYear = Year.now().getValue();
                    final int currentMonth = java.time.LocalDate.now().getMonthValue();
                    if (value < currentYear) {
                        return ValidationResult.error(Translations.get("validation.targetOrLater", String.valueOf(currentYear)));
                    }
                    final Month picked = this.targetMonth.getValue();
                    if (value.intValue() == currentYear && picked != null
                            && picked.getValue() <= currentMonth) {
                        return ValidationResult.error(Translations.get("validation.targetInFuture"));
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
            case YEARS -> Translations.get("timeHorizon.years");
            case AGES -> Translations.get("timeHorizon.ages");
            case TARGET_YEAR -> Translations.get("timeHorizon.targetYear");
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
        field.setSuffixComponent(secondaryText(Translations.get("unit.yrs")));
        field.setValueChangeMode(ValueChangeMode.LAZY);
        return field;
    }

    private static IntegerField yearsField(String label) {
        final IntegerField field = new IntegerField(label);
        field.setMin(0);
        field.setMax(100);
        field.setStepButtonsVisible(false);
        field.setSuffixComponent(secondaryText(Translations.get("unit.yrs")));
        field.setValueChangeMode(ValueChangeMode.LAZY);
        return field;
    }

    private static IntegerField monthsField(String label) {
        final IntegerField field = new IntegerField(label);
        field.setMin(0);
        field.setMax(11);
        field.setStepButtonsVisible(false);
        field.setSuffixComponent(secondaryText(Translations.get("unit.mo")));
        field.setValueChangeMode(ValueChangeMode.LAZY);
        return field;
    }

    private static Select<Month> monthSelect() {
        final Select<Month> select = new Select<>();
        select.setLabel(Translations.get("field.targetMonth"));
        select.setItems(List.of(Month.values()));
        select.setItemLabelGenerator(month -> month == null ? "" : monthLabel(month));
        return select;
    }

    private static String monthLabel(Month month) {
        final java.util.Locale locale = com.vaadin.flow.component.UI.getCurrent() != null
                ? com.vaadin.flow.component.UI.getCurrent().getLocale()
                : java.util.Locale.UK;
        return month.getDisplayName(java.time.format.TextStyle.FULL, locale);
    }

    private static IntegerField targetYearField() {
        final IntegerField field = new IntegerField(Translations.get("field.targetYear"));
        field.setMin(Year.now().getValue());
        field.setMax(Year.now().getValue() + 100);
        field.setStepButtonsVisible(false);
        field.setValueChangeMode(ValueChangeMode.LAZY);
        return field;
    }

    private static NumberField withPercentageSuffix(NumberField field) {
        if (field.getSuffixComponent() == null) {
            field.setSuffixComponent(secondaryText(Translations.get("unit.percent")));
        }
        return field;
    }

    private static Span secondaryText(String text) {
        final Span span = new Span(text);
        span.getStyle().setColor("var(--vaadin-secondary-text-color, #71717a)");
        return span;
    }
}
