package io.binarycodes.calculators.investment.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.card.Card;
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
import io.binarycodes.calculators.investment.domain.ContributionFrequency;
import io.binarycodes.calculators.investment.domain.InvestmentInputs;

import com.vaadin.flow.data.binder.Setter;
import com.vaadin.flow.data.binder.Validator;
import com.vaadin.flow.function.ValueProvider;

import java.math.BigDecimal;
import java.time.Month;
import java.time.Year;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Input form for the investment calculator. One card carries the contribution
 * (amount + monthly/yearly cadence), the rates (growth / tax / inflation /
 * step-up), the shared Years/Ages/Target-Year selector for the investment
 * phase, and a plain Years+Months hold duration.
 */
public class InvestmentCalculatorForm extends VerticalLayout {

    private final Binder<InvestmentInputs> binder = new Binder<>(InvestmentInputs.class);

    private final MoneyField amount;
    private final RadioButtonGroup<ContributionFrequency> frequency = new RadioButtonGroup<>();
    private final NumberField growthRate = percentageField("Growth Rate");
    private final NumberField taxRate = percentageField("Tax Rate");
    private final NumberField inflationRate = percentageField("Inflation Rate");
    private final NumberField stepUp = percentageField("Step-Up (Yearly)");

    private final RadioButtonGroup<TimeHorizonMode> horizonMode = new RadioButtonGroup<>();
    private final IntegerField investYears = yearsField("Years");
    private final IntegerField investMonths = monthsField("Months");
    private final IntegerField currentAge = ageField("Current Age");
    private final IntegerField goalAge = ageField("Goal Age");
    private final IntegerField targetYear = targetYearField();
    private final Select<Month> targetMonth = monthSelect();
    private final FormLayout horizonFields = new FormLayout();

    private final IntegerField holdYears = yearsField("Years");
    private final IntegerField holdMonths = monthsField("Months");

    private final Signal<InvestmentInputs> inputsSignal;

    public InvestmentCalculatorForm(UserPreferences preferences) {
        addClassName("investment-form");
        setPadding(false);
        setSpacing(true);
        setWidthFull();

        this.amount = new MoneyField("Amount", preferences);
        configureFrequencyGroup();
        configureHorizonModeGroup();
        configureBindings();
        renderHorizonFieldsFor(this.horizonMode.getValue());

        add(buildContributionCard(), buildInvestmentTimeCard(), buildHoldingCard());

        this.inputsSignal = Signal.computed(() -> {
            this.amountSignal.get();
            this.frequencySignal.get();
            this.growthSignal.get();
            this.taxSignal.get();
            this.inflationSignal.get();
            this.stepUpSignal.get();
            this.horizonModeSignal.get();
            this.investYearsSignal.get();
            this.investMonthsSignal.get();
            this.currentAgeSignal.get();
            this.goalAgeSignal.get();
            this.targetYearSignal.get();
            this.targetMonthSignal.get();
            this.holdYearsSignal.get();
            this.holdMonthsSignal.get();
            return buildInputs();
        });
    }

    public Signal<InvestmentInputs> inputsSignal() {
        return this.inputsSignal;
    }

    public void setInputs(InvestmentInputs inputs) {
        if (inputs.getHorizonMode() == null) {
            inputs.setHorizonMode(TimeHorizonMode.YEARS);
        }
        if (inputs.getFrequency() == null) {
            inputs.setFrequency(ContributionFrequency.MONTHLY);
        }
        this.binder.readBean(inputs);
        renderHorizonFieldsFor(inputs.getHorizonMode());
    }

    public InvestmentInputs getInputs() {
        return buildInputs();
    }

    public boolean isValid() {
        return this.binder.isValid();
    }

    public BinderValidationStatus<InvestmentInputs> validate() {
        return this.binder.validate();
    }

    private InvestmentInputs buildInputs() {
        final var target = new InvestmentInputs();
        this.binder.writeBeanAsDraft(target);
        if (target.getHorizonMode() == null) {
            target.setHorizonMode(TimeHorizonMode.YEARS);
        }
        if (target.getFrequency() == null) {
            target.setFrequency(ContributionFrequency.MONTHLY);
        }
        return target;
    }

    private Component buildContributionCard() {
        final FormLayout topLayout = sectionForm();
        topLayout.add(this.amount, withPercentageSuffix(this.growthRate),
                withPercentageSuffix(this.taxRate), withPercentageSuffix(this.inflationRate),
                withPercentageSuffix(this.stepUp));

        final Span frequencyLabel = new Span("Contribution frequency");
        frequencyLabel.addClassName("subsection-label");

        return card("Contribution", topLayout, frequencyLabel, this.frequency);
    }

    private Component buildInvestmentTimeCard() {
        final Span intro = new Span("How long you keep contributing.");
        intro.addClassName("subsection-hint");

        this.horizonFields.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("36em", 2));
        this.horizonFields.setWidthFull();

        return card("Investment Time", intro, this.horizonMode, this.horizonFields);
    }

    private Component buildHoldingCard() {
        final Span intro = new Span("How much longer the corpus keeps growing after "
                + "contributions stop — no new money is added.");
        intro.addClassName("subsection-hint");

        final FormLayout holdLayout = new FormLayout();
        holdLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("36em", 2));
        holdLayout.add(this.holdYears, this.holdMonths);
        holdLayout.setWidthFull();

        return card("Holding Period", intro, holdLayout);
    }

    private static Card card(String title, Component... children) {
        final VerticalLayout content = new VerticalLayout(children);
        content.setPadding(false);
        content.setSpacing(true);

        final Card card = new Card();
        card.setTitle(title);
        card.add(content);
        card.setWidthFull();
        card.addClassName("form-section");
        return card;
    }

    private void configureFrequencyGroup() {
        this.frequency.setItems(ContributionFrequency.values());
        this.frequency.setItemLabelGenerator(value ->
                value == ContributionFrequency.MONTHLY ? "Monthly" : "Yearly");
        this.frequency.setValue(ContributionFrequency.MONTHLY);
        this.frequency.addClassName("segmented-toggle");
    }

    private void configureHorizonModeGroup() {
        this.horizonMode.setItems(TimeHorizonMode.values());
        this.horizonMode.setItemLabelGenerator(InvestmentCalculatorForm::horizonModeLabel);
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
            case YEARS -> this.horizonFields.add(this.investYears, this.investMonths);
            case AGES -> this.horizonFields.add(this.currentAge, this.goalAge);
            case TARGET_YEAR -> this.horizonFields.add(this.targetYear, this.targetMonth);
        }
    }

    private Signal<?> amountSignal;
    private Signal<?> frequencySignal;
    private Signal<?> growthSignal;
    private Signal<?> taxSignal;
    private Signal<?> inflationSignal;
    private Signal<?> stepUpSignal;
    private Signal<?> horizonModeSignal;
    private Signal<?> investYearsSignal;
    private Signal<?> investMonthsSignal;
    private Signal<?> currentAgeSignal;
    private Signal<?> goalAgeSignal;
    private Signal<?> targetYearSignal;
    private Signal<?> targetMonthSignal;
    private Signal<?> holdYearsSignal;
    private Signal<?> holdMonthsSignal;

    private void configureBindings() {
        this.amountSignal = this.binder.forField(this.amount)
                .asRequired("Required")
                .withValidator(new BigDecimalRangeValidator("Must be positive",
                        new BigDecimal("0.01"), null))
                .bind(InvestmentInputs::getAmount, InvestmentInputs::setAmount)
                .valueSignal();

        this.frequencySignal = this.binder.forField(this.frequency)
                .bind(InvestmentInputs::getFrequency, InvestmentInputs::setFrequency)
                .valueSignal();

        this.growthSignal = bindPercentage(this.growthRate,
                InvestmentInputs::getGrowthRatePct, InvestmentInputs::setGrowthRatePct, true);
        this.taxSignal = bindPercentage(this.taxRate,
                InvestmentInputs::getTaxRatePct, InvestmentInputs::setTaxRatePct, true);
        this.inflationSignal = bindPercentage(this.inflationRate,
                InvestmentInputs::getInflationRatePct, InvestmentInputs::setInflationRatePct, true);
        this.stepUpSignal = bindPercentage(this.stepUp,
                InvestmentInputs::getStepUpPct, InvestmentInputs::setStepUpPct, false);

        this.horizonModeSignal = this.binder.forField(this.horizonMode)
                .bind(InvestmentInputs::getHorizonMode, InvestmentInputs::setHorizonMode)
                .valueSignal();

        this.investYearsSignal = this.binder.forField(this.investYears)
                .withValidator((value, context) -> {
                    if (this.horizonMode.getValue() == TimeHorizonMode.YEARS) {
                        final int extra = this.investMonths.getValue() == null ? 0 : this.investMonths.getValue();
                        if (value == null) {
                            return ValidationResult.error("Required");
                        }
                        if (value < 0 || value > 100) {
                            return ValidationResult.error("Must be between 0 and 100");
                        }
                        if (value == 0 && extra == 0) {
                            return ValidationResult.error("Investment time must be at least one month");
                        }
                    }
                    return ValidationResult.ok();
                })
                .bind(InvestmentInputs::getInvestYears, InvestmentInputs::setInvestYears)
                .valueSignal();

        this.investMonthsSignal = this.binder.forField(this.investMonths)
                .withValidator(monthsValidator(() -> this.horizonMode.getValue() == TimeHorizonMode.YEARS))
                .bind(InvestmentInputs::getInvestMonths, InvestmentInputs::setInvestMonths)
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
                .bind(InvestmentInputs::getCurrentAge, InvestmentInputs::setCurrentAge)
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
                .bind(InvestmentInputs::getGoalAge, InvestmentInputs::setGoalAge)
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
                .bind(InvestmentInputs::getTargetYear, InvestmentInputs::setTargetYear)
                .valueSignal();

        this.targetMonthSignal = this.binder.forField(this.targetMonth)
                .withConverter(
                        month -> month == null ? null : month.getValue(),
                        value -> value == null ? null : Month.of(value))
                .bind(InvestmentInputs::getTargetMonth, InvestmentInputs::setTargetMonth)
                .valueSignal();

        this.holdYearsSignal = this.binder.forField(this.holdYears)
                .withValidator((value, context) ->
                        value != null && (value < 0 || value > 100)
                                ? ValidationResult.error("Must be between 0 and 100")
                                : ValidationResult.ok())
                .bind(InvestmentInputs::getHoldYears, InvestmentInputs::setHoldYears)
                .valueSignal();

        this.holdMonthsSignal = this.binder.forField(this.holdMonths)
                .withValidator(monthsValidator(() -> true))
                .bind(InvestmentInputs::getHoldMonths, InvestmentInputs::setHoldMonths)
                .valueSignal();

        this.horizonMode.addValueChangeListener(event -> this.binder.validate());
        this.currentAge.addValueChangeListener(event -> this.binder.validate());
        this.investMonths.addValueChangeListener(event -> this.binder.validate());
        this.targetMonth.addValueChangeListener(event -> this.binder.validate());
    }

    private static Validator<Integer> monthsValidator(
            BooleanSupplier active) {
        return (value, context) -> {
            if (active.getAsBoolean() && value != null && (value < 0 || value > 11)) {
                return ValidationResult.error("Must be between 0 and 11");
            }
            return ValidationResult.ok();
        };
    }

    private Signal<?> bindPercentage(NumberField field,
                                     ValueProvider<InvestmentInputs, BigDecimal> getter,
                                     Setter<InvestmentInputs, BigDecimal> setter,
                                     boolean required) {
        var forField = this.binder.forField(field);
        if (required) {
            forField = forField.asRequired("Required");
        }
        return forField
                .withValidator(new DoubleRangeValidator("Must be between 0 and 100", 0d, 100d))
                .withConverter(doubleToBigDecimalConverter())
                .bind(getter, setter)
                .valueSignal();
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
