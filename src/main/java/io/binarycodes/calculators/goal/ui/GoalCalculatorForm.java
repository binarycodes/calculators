package io.binarycodes.calculators.goal.ui;

import com.vaadin.flow.component.Component;
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
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.base.ui.CalculatorForm;
import io.binarycodes.calculators.base.ui.FormCard;
import io.binarycodes.calculators.base.ui.MoneyField;
import io.binarycodes.calculators.goal.domain.GoalInputs;
import io.binarycodes.calculators.base.common.TimeHorizonMode;

import java.math.BigDecimal;
import java.time.Month;
import java.time.Year;
import java.util.List;

/**
 * The goal-planner input form. Composes three cards:
 *
 * <ul>
 *   <li><b>Goal</b> — target post-tax amount and the yearly step-up applied
 *       to the monthly contribution.</li>
 *   <li><b>{@link InvestmentsCard Investments}</b> — list of buckets with
 *       their own corpus, growth, tax, and allocation share. Validated to
 *       sum to 100%.</li>
 *   <li><b>Time Horizon</b> — segmented toggle between Years (+ months),
 *       Ages, and Target Year (+ month).</li>
 * </ul>
 */
public class GoalCalculatorForm extends VerticalLayout implements CalculatorForm<GoalInputs> {

    private final Binder<GoalInputs> binder = new Binder<>(GoalInputs.class);

    private final MoneyField goalAmount;
    private final NumberField inflationRate = percentageField("Inflation Rate");
    private final RadioButtonGroup<TimeHorizonMode> horizonMode = new RadioButtonGroup<>();

    private final IntegerField yearsToGoal = yearsField("Years");
    private final IntegerField monthsToGoal = monthsField("Months");
    private final IntegerField currentAge = ageField("Current Age");
    private final IntegerField goalAge = ageField("Goal Age");
    private final IntegerField targetYear = targetYearField();
    private final Select<Month> targetMonth = monthSelect();

    private final InvestmentsCard investmentsCard;
    private final FormLayout horizonFields = new FormLayout();
    private FormCard goalCard;
    private final Signal<GoalInputs> inputsSignal;

    public GoalCalculatorForm(UserPreferences preferences) {
        addClassName("goal-form");
        setPadding(false);
        setSpacing(true);
        setWidthFull();

        this.goalAmount = new MoneyField("Amount (today)", preferences);
        this.investmentsCard = new InvestmentsCard(preferences);

        configureHorizonModeGroup();
        configureBindings();
        renderHorizonFieldsFor(this.horizonMode.getValue());

        add(buildGoalCard(), this.investmentsCard);

        this.inputsSignal = Signal.computed(() -> {
            this.goalAmountSignal.get();
            this.inflationSignal.get();
            this.horizonModeSignal.get();
            this.yearsSignal.get();
            this.monthsSignal.get();
            this.currentAgeSignal.get();
            this.goalAgeSignal.get();
            this.targetYearSignal.get();
            this.targetMonthSignal.get();
            this.investmentsCard.investmentsSignal().get();
            return buildInputs();
        });
    }

    public Signal<GoalInputs> inputsSignal() {
        return this.inputsSignal;
    }

    public void setInputs(GoalInputs inputs) {
        if (inputs.getHorizonMode() == null) {
            inputs.setHorizonMode(TimeHorizonMode.YEARS);
        }
        this.binder.readBean(inputs);
        renderHorizonFieldsFor(inputs.getHorizonMode());
        this.investmentsCard.setInvestments(inputs.getInvestments());
    }

    public void clear() {
        setInputs(new GoalInputs());
    }

    @Override
    public void showValidationMessages(String calculationError) {
        FormCard.refreshGenericErrors(this);
        if (!this.investmentsCard.hasInvalidField() && !this.investmentsCard.isAllocationValid()) {
            this.investmentsCard.showError("Allocations must sum to 100%.");
        }
        if (calculationError != null && !this.goalCard.hasInvalidField()) {
            this.goalCard.showError(calculationError);
        }
    }

    public GoalInputs getInputs() {
        return buildInputs();
    }

    public boolean isValid() {
        return this.binder.isValid() && this.investmentsCard.isAllocationValid();
    }

    public BinderValidationStatus<GoalInputs> validate() {
        return this.binder.validate();
    }

    /**
     * Helper text shown under the inflation rate field — used by the view to
     * surface the inflated target ("Target at horizon: …") once a calculation
     * has run, so the user can see what amount the SIP is actually solving for.
     */
    public void setInflationHelperText(String text) {
        this.inflationRate.setHelperText(text);
    }

    private GoalInputs buildInputs() {
        final var target = new GoalInputs();
        this.binder.writeBeanAsDraft(target);
        if (target.getHorizonMode() == null) {
            target.setHorizonMode(TimeHorizonMode.YEARS);
        }
        target.setInvestments(this.investmentsCard.getInvestments());
        return target;
    }

    private Component buildGoalCard() {
        final FormLayout amountLayout = sectionForm();
        amountLayout.add(this.goalAmount, withPercentageSuffix(this.inflationRate));

        this.horizonFields.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("36em", 2));
        this.horizonFields.setWidthFull();

        final Span horizonLabel = new Span("Time horizon");
        horizonLabel.addClassName("subsection-label");

        final VerticalLayout content = new VerticalLayout(
                amountLayout, horizonLabel, this.horizonMode, this.horizonFields);
        content.setPadding(false);
        content.setSpacing(true);

        final FormCard card = new FormCard("Goal");
        card.add(content);
        card.setWidthFull();
        card.addClassName("form-section");
        this.goalCard = card;
        return card;
    }

    private void configureHorizonModeGroup() {
        this.horizonMode.setItems(TimeHorizonMode.values());
        this.horizonMode.setItemLabelGenerator(GoalCalculatorForm::horizonModeLabel);
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
            case YEARS -> this.horizonFields.add(this.yearsToGoal, this.monthsToGoal);
            case AGES -> this.horizonFields.add(this.currentAge, this.goalAge);
            case TARGET_YEAR -> this.horizonFields.add(this.targetYear, this.targetMonth);
        }
    }

    private Signal<?> goalAmountSignal;
    private Signal<?> inflationSignal;
    private Signal<?> horizonModeSignal;
    private Signal<?> yearsSignal;
    private Signal<?> monthsSignal;
    private Signal<?> currentAgeSignal;
    private Signal<?> goalAgeSignal;
    private Signal<?> targetYearSignal;
    private Signal<?> targetMonthSignal;

    private void configureBindings() {
        this.goalAmountSignal = this.binder.forField(this.goalAmount)
                .asRequired("Required")
                .withValidator(new BigDecimalRangeValidator("Must be positive",
                        new BigDecimal("0.01"), null))
                .bind(GoalInputs::getGoalAmount, GoalInputs::setGoalAmount)
                .valueSignal();

        this.inflationSignal = this.binder.forField(this.inflationRate)
                .withValidator(new DoubleRangeValidator("Must be between 0 and 100", 0d, 100d))
                .withConverter(doubleToBigDecimalConverter())
                .bind(GoalInputs::getInflationRatePct, GoalInputs::setInflationRatePct)
                .valueSignal();

        this.horizonModeSignal = this.binder.forField(this.horizonMode)
                .bind(GoalInputs::getHorizonMode, GoalInputs::setHorizonMode)
                .valueSignal();

        this.yearsSignal = this.binder.forField(this.yearsToGoal)
                .withValidator((value, context) -> {
                    if (this.horizonMode.getValue() == TimeHorizonMode.YEARS) {
                        final int months = this.monthsToGoal.getValue() == null
                                ? 0 : this.monthsToGoal.getValue();
                        if (value == null) {
                            return ValidationResult.error("Required");
                        }
                        if (value < 0 || value > 80) {
                            return ValidationResult.error("Must be between 0 and 80");
                        }
                        if (value == 0 && months == 0) {
                            return ValidationResult.error("Years + months must be at least one month");
                        }
                    }
                    return ValidationResult.ok();
                })
                .bind(GoalInputs::getYearsToGoal, GoalInputs::setYearsToGoal)
                .valueSignal();

        this.monthsSignal = this.binder.forField(this.monthsToGoal)
                .withValidator((value, context) -> {
                    if (this.horizonMode.getValue() == TimeHorizonMode.YEARS
                            && value != null && (value < 0 || value > 11)) {
                        return ValidationResult.error("Must be between 0 and 11");
                    }
                    return ValidationResult.ok();
                })
                .bind(GoalInputs::getMonthsToGoal, GoalInputs::setMonthsToGoal)
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
                .bind(GoalInputs::getCurrentAge, GoalInputs::setCurrentAge)
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
                    final Integer current = this.currentAge.getValue();
                    if (current != null && value <= current) {
                        return ValidationResult.error("Must be greater than current age");
                    }
                    return ValidationResult.ok();
                })
                .bind(GoalInputs::getGoalAge, GoalInputs::setGoalAge)
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
                    if (value.intValue() == currentYear
                            && picked != null
                            && picked.getValue() <= currentMonth) {
                        return ValidationResult.error("Target must be in the future");
                    }
                    return ValidationResult.ok();
                })
                .bind(GoalInputs::getTargetYear, GoalInputs::setTargetYear)
                .valueSignal();

        this.targetMonthSignal = this.binder.forField(this.targetMonth)
                .withConverter(
                        month -> month == null ? null : month.getValue(),
                        value -> value == null ? null : Month.of(value))
                .bind(GoalInputs::getTargetMonth, GoalInputs::setTargetMonth)
                .valueSignal();

        this.horizonMode.addValueChangeListener(event -> this.binder.validate());
        this.currentAge.addValueChangeListener(event -> this.binder.validate());
        this.monthsToGoal.addValueChangeListener(event -> this.binder.validate());
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
        field.setMax(80);
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
        field.setMax(Year.now().getValue() + 80);
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
