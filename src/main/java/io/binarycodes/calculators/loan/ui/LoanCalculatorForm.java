package io.binarycodes.calculators.loan.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
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
import io.binarycodes.calculators.base.ui.TabIndicator;
import io.binarycodes.calculators.loan.domain.LoanInputs;
import io.binarycodes.calculators.loan.domain.PrepaymentFrequency;

import java.math.BigDecimal;

/**
 * Input form for the loan / EMI calculator. One card carries the loan itself
 * (amount, rate, tenure as years + months, and an inflation rate used only for
 * the real-cost view); a second card carries the optional prepayment levers.
 */
public class LoanCalculatorForm extends VerticalLayout implements CalculatorForm<LoanInputs> {

    private final Binder<LoanInputs> binder = new Binder<>(LoanInputs.class);

    private final MoneyField loanAmount;
    private final NumberField annualRate = percentageField("Interest Rate");
    private final IntegerField tenureYears = yearsField("Years");
    private final IntegerField tenureMonths = monthsField("Months");
    private final NumberField inflationRate = percentageField("Inflation Rate");

    private final MoneyField extraPerPeriod;
    private final RadioButtonGroup<PrepaymentFrequency> extraFrequency = new RadioButtonGroup<>();
    private final IntegerField extraEmisPerYear = countField("Extra EMIs / year");
    private final NumberField emiStepUp = percentageField("EMI Step-Up (Yearly)");

    private final Span extraPaymentDot = TabIndicator.dot("A recurring extra payment is set");
    private final Span extraEmisDot = TabIndicator.dot("Extra EMIs per year are set");
    private final Span stepUpDot = TabIndicator.dot("An annual EMI step-up is set");

    private FormCard loanCard;

    private final Signal<LoanInputs> inputsSignal;

    private Signal<?> loanAmountSignal;
    private Signal<?> annualRateSignal;
    private Signal<?> tenureYearsSignal;
    private Signal<?> tenureMonthsSignal;
    private Signal<?> inflationSignal;
    private Signal<?> extraPerPeriodSignal;
    private Signal<?> extraFrequencySignal;
    private Signal<?> extraEmisSignal;
    private Signal<?> emiStepUpSignal;

    public LoanCalculatorForm(UserPreferences preferences) {
        addClassName("loan-form");
        setPadding(false);
        setSpacing(true);
        setWidthFull();

        this.loanAmount = new MoneyField("Loan Amount", preferences);
        this.extraPerPeriod = new MoneyField("Extra Payment", preferences);
        configureFrequencyGroup();
        configureBindings();

        add(buildLoanCard(), buildDurationCard(), buildPrepaymentCard());

        this.inputsSignal = Signal.computed(() -> {
            this.loanAmountSignal.get();
            this.annualRateSignal.get();
            this.tenureYearsSignal.get();
            this.tenureMonthsSignal.get();
            this.inflationSignal.get();
            this.extraPerPeriodSignal.get();
            this.extraFrequencySignal.get();
            this.extraEmisSignal.get();
            this.emiStepUpSignal.get();
            return buildInputs();
        });

        // Mark each prepayment tab the user has set; flag it red if its field is
        // invalid. The signal effect catches value changes; the binder status
        // listener catches validity changes that carry no value change.
        Signal.effect(this, context -> {
            this.inputsSignal.get();
            refreshIndicators();
        });
        this.binder.addStatusChangeListener(event -> refreshIndicators());
    }

    private void refreshIndicators() {
        TabIndicator.apply(this.extraPaymentDot,
                TabIndicator.isSet(this.extraPerPeriod), this.extraPerPeriod.isInvalid());
        TabIndicator.apply(this.extraEmisDot,
                TabIndicator.isSet(this.extraEmisPerYear), this.extraEmisPerYear.isInvalid());
        TabIndicator.apply(this.stepUpDot,
                TabIndicator.isSet(this.emiStepUp), this.emiStepUp.isInvalid());
    }

    public Signal<LoanInputs> inputsSignal() {
        return this.inputsSignal;
    }

    public void setInputs(LoanInputs inputs) {
        if (inputs.getExtraFrequency() == null) {
            inputs.setExtraFrequency(PrepaymentFrequency.YEARLY);
        }
        this.binder.readBean(inputs);
    }

    public void clear() {
        setInputs(new LoanInputs());
    }

    public LoanInputs getInputs() {
        return buildInputs();
    }

    @Override
    public void showValidationMessages(String calculationError) {
        FormCard.refreshGenericErrors(this);
        if (calculationError != null && !this.loanCard.hasInvalidField()) {
            this.loanCard.showError(calculationError);
        }
    }

    public boolean isValid() {
        return this.binder.isValid();
    }

    public BinderValidationStatus<LoanInputs> validate() {
        return this.binder.validate();
    }

    private LoanInputs buildInputs() {
        final var target = new LoanInputs();
        this.binder.writeBeanAsDraft(target);
        if (target.getExtraFrequency() == null) {
            target.setExtraFrequency(PrepaymentFrequency.YEARLY);
        }
        return target;
    }

    private Component buildLoanCard() {
        final FormLayout layout = sectionForm();
        layout.add(this.loanAmount, withPercentageSuffix(this.annualRate),
                withPercentageSuffix(this.inflationRate));
        this.loanCard = card("Loan", layout);
        return this.loanCard;
    }

    private Component buildDurationCard() {
        final FormLayout layout = new FormLayout();
        layout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("36em", 2));
        layout.add(this.tenureYears, this.tenureMonths);
        layout.setWidthFull();
        return card("Duration", layout);
    }

    private Component buildPrepaymentCard() {
        final Span intro = new Span("Optional — leave every tab at zero for a plain EMI. Each tab is a different "
                + "way to pay more, and they combine.");
        intro.addClassName("subsection-hint");

        // Amount and frequency share one responsive row (two columns above 36em,
        // stacked below it), so the field labels line up.
        final FormLayout amountFields = twoColForm();
        amountFields.add(this.extraPerPeriod, this.extraFrequency);
        final Component extraPayment = lever(
                "A fixed amount paid on top of the EMI, at the chosen frequency.",
                amountFields);

        final FormLayout emiFields = twoColForm();
        emiFields.add(this.extraEmisPerYear);
        final Component extraEmis = lever(
                "Extra full EMIs paid once a year — e.g. 1 means paying 13 EMIs instead of 12.",
                emiFields);

        final FormLayout stepUpFields = twoColForm();
        stepUpFields.add(withPercentageSuffix(this.emiStepUp));
        final Component stepUp = lever(
                "Permanently raises the EMI by this percentage every year, which shortens the loan.",
                stepUpFields);

        final TabSheet tabs = new TabSheet();
        tabs.addClassName("prepay-tabs");
        tabs.setWidthFull();
        tabs.add(new Tab(new Span("Extra payment"), this.extraPaymentDot), extraPayment);
        tabs.add(new Tab(new Span("Extra EMIs / year"), this.extraEmisDot), extraEmis);
        tabs.add(new Tab(new Span("Step-up EMI"), this.stepUpDot), stepUp);

        return card("Prepayments", intro, tabs);
    }

    /** One prepayment lever: its field(s) above a one-line caption explaining it. */
    private static Component lever(String hintText, Component... fields) {
        final VerticalLayout block = new VerticalLayout(fields);
        block.setPadding(false);
        block.setSpacing(false);
        block.addClassName("prepay-lever");
        final Span hint = new Span(hintText);
        hint.addClassName("subsection-hint");
        block.add(hint);
        return block;
    }

    private static FormLayout twoColForm() {
        final FormLayout layout = new FormLayout();
        layout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("36em", 2));
        return layout;
    }

    private void configureFrequencyGroup() {
        this.extraFrequency.setLabel("Frequency");
        this.extraFrequency.setItems(PrepaymentFrequency.values());
        this.extraFrequency.setItemLabelGenerator(LoanCalculatorForm::frequencyLabel);
        this.extraFrequency.setValue(PrepaymentFrequency.YEARLY);
        this.extraFrequency.addClassName("segmented-toggle");
    }

    private void configureBindings() {
        this.loanAmountSignal = this.binder.forField(this.loanAmount)
                .asRequired("Required")
                .withValidator(new BigDecimalRangeValidator("Must be positive",
                        new BigDecimal("0.01"), null))
                .bind(LoanInputs::getLoanAmount, LoanInputs::setLoanAmount)
                .valueSignal();

        this.annualRateSignal = this.binder.forField(this.annualRate)
                .asRequired("Required")
                .withValidator(new DoubleRangeValidator("Must be between 0 and 100", 0d, 100d))
                .withConverter(doubleToBigDecimalConverter())
                .bind(LoanInputs::getAnnualRatePct, LoanInputs::setAnnualRatePct)
                .valueSignal();

        this.tenureYearsSignal = this.binder.forField(this.tenureYears)
                .asRequired("Required")
                .withValidator((value, context) -> {
                    if (value == null || value < 0 || value > 100) {
                        return ValidationResult.error("Must be between 0 and 100");
                    }
                    final int extra = this.tenureMonths.getValue() == null ? 0 : this.tenureMonths.getValue();
                    if (value == 0 && extra == 0) {
                        return ValidationResult.error("Tenure must be at least one month");
                    }
                    return ValidationResult.ok();
                })
                .bind(LoanInputs::getTenureYears, LoanInputs::setTenureYears)
                .valueSignal();

        this.tenureMonthsSignal = this.binder.forField(this.tenureMonths)
                .withValidator((value, context) ->
                        value != null && (value < 0 || value > 11)
                                ? ValidationResult.error("Must be between 0 and 11")
                                : ValidationResult.ok())
                .bind(LoanInputs::getTenureMonths, LoanInputs::setTenureMonths)
                .valueSignal();
        this.tenureMonths.addValueChangeListener(event -> this.binder.validate());

        this.inflationSignal = this.binder.forField(this.inflationRate)
                .withValidator(new DoubleRangeValidator("Must be between 0 and 100", 0d, 100d))
                .withConverter(doubleToBigDecimalConverter())
                .bind(LoanInputs::getInflationRatePct, LoanInputs::setInflationRatePct)
                .valueSignal();

        this.extraPerPeriodSignal = this.binder.forField(this.extraPerPeriod)
                .withValidator(new BigDecimalRangeValidator("Must be 0 or more", BigDecimal.ZERO, null))
                .bind(LoanInputs::getExtraPerPeriod, LoanInputs::setExtraPerPeriod)
                .valueSignal();

        this.extraFrequencySignal = this.binder.forField(this.extraFrequency)
                .bind(LoanInputs::getExtraFrequency, LoanInputs::setExtraFrequency)
                .valueSignal();

        this.extraEmisSignal = this.binder.forField(this.extraEmisPerYear)
                .withValidator((value, context) ->
                        value != null && (value < 0 || value > 12)
                                ? ValidationResult.error("Must be between 0 and 12")
                                : ValidationResult.ok())
                .bind(LoanInputs::getExtraEmisPerYear, LoanInputs::setExtraEmisPerYear)
                .valueSignal();

        this.emiStepUpSignal = this.binder.forField(this.emiStepUp)
                .withValidator(new DoubleRangeValidator("Must be between 0 and 100", 0d, 100d))
                .withConverter(doubleToBigDecimalConverter())
                .bind(LoanInputs::getEmiStepUpPct, LoanInputs::setEmiStepUpPct)
                .valueSignal();
    }

    private static FormCard card(String title, Component... children) {
        final VerticalLayout content = new VerticalLayout(children);
        content.setPadding(false);
        content.setSpacing(true);

        final FormCard card = new FormCard(title);
        card.add(content);
        card.setWidthFull();
        card.addClassName("form-section");
        return card;
    }

    private static FormLayout sectionForm() {
        final FormLayout layout = new FormLayout();
        layout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("36em", 2),
                new FormLayout.ResponsiveStep("64em", 3));
        return layout;
    }

    private static String frequencyLabel(PrepaymentFrequency frequency) {
        return switch (frequency) {
            case MONTHLY -> "Monthly";
            case QUARTERLY -> "Quarterly";
            case YEARLY -> "Yearly";
        };
    }

    private static Converter<Double, BigDecimal> doubleToBigDecimalConverter() {
        return Converter.from(
                value -> Result.ok(value == null ? null : BigDecimal.valueOf(value)),
                value -> value == null ? null : value.doubleValue());
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

    private static IntegerField countField(String label) {
        final IntegerField field = new IntegerField(label);
        field.setMin(0);
        field.setMax(12);
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
