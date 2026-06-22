package io.binarycodes.calculators.buyrent.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.BinderValidationStatus;
import com.vaadin.flow.data.binder.Result;
import com.vaadin.flow.data.converter.Converter;
import com.vaadin.flow.data.validator.BigDecimalRangeValidator;
import com.vaadin.flow.data.validator.DoubleRangeValidator;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.signals.Signal;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.base.ui.CalculatorForm;
import io.binarycodes.calculators.base.ui.FormCard;
import io.binarycodes.calculators.base.ui.MoneyField;
import io.binarycodes.calculators.buyrent.domain.BuyRentInputs;

import java.math.BigDecimal;

/**
 * Input form for the buy-vs-rent calculator. Four cards:
 *
 * <ul>
 *   <li><b>Home Purchase</b> — home price, down payment, loan term, mortgage rate.</li>
 *   <li><b>Costs &amp; Appreciation</b> — property tax, maintenance, appreciation,
 *       buying costs, selling costs.</li>
 *   <li><b>Renting</b> — monthly rent and annual rent increase.</li>
 *   <li><b>Analysis</b> — investment return, inflation rate, analysis horizon.</li>
 * </ul>
 */
public class BuyRentCalculatorForm extends VerticalLayout implements CalculatorForm<BuyRentInputs> {

    private final Binder<BuyRentInputs> binder = new Binder<>(BuyRentInputs.class);

    private final MoneyField homePrice;
    private final NumberField downPaymentPct = percentageField("Down Payment");
    private final IntegerField loanTermYears = yearsField("Loan Term");
    private final NumberField mortgageRatePct = percentageField("Mortgage Rate");

    private final NumberField propertyTaxRatePct = percentageField("Property Tax Rate");
    private final NumberField maintenancePct = percentageField("Maintenance Rate");
    private final NumberField appreciationPct = percentageField("Home Appreciation");
    private final NumberField buyingCostPct = percentageField("Buying Costs (stamp duty etc.)");
    private final NumberField sellingCostPct = percentageField("Selling Costs (agent fees etc.)");

    private final MoneyField monthlyRent;
    private final NumberField rentIncreasePct = percentageField("Annual Rent Increase");

    private final NumberField investmentReturnPct = percentageField("Investment Return");
    private final NumberField inflationRatePct = percentageField("Inflation Rate");
    private final IntegerField analysisYears = yearsField("Analysis Horizon");

    private FormCard homePurchaseCard;

    private Signal<?> homePriceSignal;
    private Signal<?> downPaymentSignal;
    private Signal<?> loanTermSignal;
    private Signal<?> mortgageRateSignal;
    private Signal<?> propertyTaxSignal;
    private Signal<?> maintenanceSignal;
    private Signal<?> appreciationSignal;
    private Signal<?> buyingCostSignal;
    private Signal<?> sellingCostSignal;
    private Signal<?> monthlyRentSignal;
    private Signal<?> rentIncreaseSignal;
    private Signal<?> investmentReturnSignal;
    private Signal<?> inflationRateSignal;
    private Signal<?> analysisYearsSignal;

    private final Signal<BuyRentInputs> inputsSignal;

    public BuyRentCalculatorForm(UserPreferences preferences) {
        addClassName("buyrent-form");
        setPadding(false);
        setSpacing(true);
        setWidthFull();

        this.homePrice = new MoneyField("Home Price", preferences);
        this.monthlyRent = new MoneyField("Monthly Rent", preferences);

        configureBindings();

        add(buildHomePurchaseCard(), buildCostsCard(), buildRentingCard(), buildAnalysisCard());

        this.inputsSignal = Signal.computed(() -> {
            this.homePriceSignal.get();
            this.downPaymentSignal.get();
            this.loanTermSignal.get();
            this.mortgageRateSignal.get();
            this.propertyTaxSignal.get();
            this.maintenanceSignal.get();
            this.appreciationSignal.get();
            this.buyingCostSignal.get();
            this.sellingCostSignal.get();
            this.monthlyRentSignal.get();
            this.rentIncreaseSignal.get();
            this.investmentReturnSignal.get();
            this.inflationRateSignal.get();
            this.analysisYearsSignal.get();
            return buildInputs();
        });
    }

    @Override
    public Signal<BuyRentInputs> inputsSignal() {
        return this.inputsSignal;
    }

    @Override
    public void setInputs(BuyRentInputs inputs) {
        this.binder.readBean(inputs);
    }

    @Override
    public void clear() {
        setInputs(new BuyRentInputs());
    }

    @Override
    public BuyRentInputs getInputs() {
        return buildInputs();
    }

    @Override
    public void showValidationMessages(String calculationError) {
        FormCard.refreshGenericErrors(this);
        if (calculationError != null && !this.homePurchaseCard.hasInvalidField()) {
            this.homePurchaseCard.showError(calculationError);
        }
    }

    public boolean isValid() {
        return this.binder.isValid();
    }

    public BinderValidationStatus<BuyRentInputs> validate() {
        return this.binder.validate();
    }

    private BuyRentInputs buildInputs() {
        final var target = new BuyRentInputs();
        this.binder.writeBeanAsDraft(target);
        return target;
    }

    private Component buildHomePurchaseCard() {
        final FormLayout layout = threeColForm();
        layout.add(this.homePrice, withPercentageSuffix(this.downPaymentPct),
                this.loanTermYears, withPercentageSuffix(this.mortgageRatePct));
        this.homePurchaseCard = card("Home Purchase", layout);
        return this.homePurchaseCard;
    }

    private Component buildCostsCard() {
        final FormLayout layout = threeColForm();
        layout.add(withPercentageSuffix(this.propertyTaxRatePct),
                withPercentageSuffix(this.maintenancePct),
                withPercentageSuffix(this.appreciationPct),
                withPercentageSuffix(this.buyingCostPct),
                withPercentageSuffix(this.sellingCostPct));

        final Span hint = new Span(
                "Property tax and maintenance are annual percentages of the current home value. "
                + "Buying costs cover stamp duty, registration, etc. Selling costs cover agent fees.");
        hint.addClassName("subsection-hint");

        final VerticalLayout content = new VerticalLayout(layout, hint);
        content.setPadding(false);
        content.setSpacing(true);
        return card("Costs & Appreciation", content);
    }

    private Component buildRentingCard() {
        final FormLayout layout = twoColForm();
        layout.add(this.monthlyRent, withPercentageSuffix(this.rentIncreasePct));
        return card("Renting", layout);
    }

    private Component buildAnalysisCard() {
        final FormLayout layout = threeColForm();
        layout.add(withPercentageSuffix(this.investmentReturnPct),
                withPercentageSuffix(this.inflationRatePct), this.analysisYears);

        final Span hint = new Span(
                "The investment return is applied to the down payment (and monthly surplus) "
                + "in the rent scenario. Inflation is used to express the final net-worth difference "
                + "in today's money.");
        hint.addClassName("subsection-hint");

        final VerticalLayout content = new VerticalLayout(layout, hint);
        content.setPadding(false);
        content.setSpacing(true);
        return card("Analysis", content);
    }

    private void configureBindings() {
        this.homePriceSignal = this.binder.forField(this.homePrice)
                .asRequired("Required")
                .withValidator(new BigDecimalRangeValidator("Must be positive",
                        new BigDecimal("0.01"), null))
                .bind(BuyRentInputs::getHomePrice, BuyRentInputs::setHomePrice)
                .valueSignal();

        this.downPaymentSignal = this.binder.forField(this.downPaymentPct)
                .asRequired("Required")
                .withValidator(new DoubleRangeValidator("Must be between 0 and 99", 0d, 99d))
                .withConverter(doubleToBigDecimalConverter())
                .bind(BuyRentInputs::getDownPaymentPct, BuyRentInputs::setDownPaymentPct)
                .valueSignal();

        this.loanTermSignal = this.binder.forField(this.loanTermYears)
                .asRequired("Required")
                .withValidator((value, context) -> {
                    if (value == null || value < 1 || value > 40) {
                        return com.vaadin.flow.data.binder.ValidationResult.error("Must be between 1 and 40");
                    }
                    return com.vaadin.flow.data.binder.ValidationResult.ok();
                })
                .bind(BuyRentInputs::getLoanTermYears, BuyRentInputs::setLoanTermYears)
                .valueSignal();

        this.mortgageRateSignal = this.binder.forField(this.mortgageRatePct)
                .asRequired("Required")
                .withValidator(new DoubleRangeValidator("Must be between 0 and 30", 0d, 30d))
                .withConverter(doubleToBigDecimalConverter())
                .bind(BuyRentInputs::getMortgageRatePct, BuyRentInputs::setMortgageRatePct)
                .valueSignal();

        this.propertyTaxSignal = this.binder.forField(this.propertyTaxRatePct)
                .withValidator(new DoubleRangeValidator("Must be between 0 and 5", 0d, 5d))
                .withConverter(doubleToBigDecimalConverter())
                .bind(BuyRentInputs::getPropertyTaxRatePct, BuyRentInputs::setPropertyTaxRatePct)
                .valueSignal();

        this.maintenanceSignal = this.binder.forField(this.maintenancePct)
                .withValidator(new DoubleRangeValidator("Must be between 0 and 5", 0d, 5d))
                .withConverter(doubleToBigDecimalConverter())
                .bind(BuyRentInputs::getMaintenancePct, BuyRentInputs::setMaintenancePct)
                .valueSignal();

        this.appreciationSignal = this.binder.forField(this.appreciationPct)
                .withValidator(new DoubleRangeValidator("Must be between 0 and 20", 0d, 20d))
                .withConverter(doubleToBigDecimalConverter())
                .bind(BuyRentInputs::getAppreciationPct, BuyRentInputs::setAppreciationPct)
                .valueSignal();

        this.buyingCostSignal = this.binder.forField(this.buyingCostPct)
                .withValidator(new DoubleRangeValidator("Must be between 0 and 15", 0d, 15d))
                .withConverter(doubleToBigDecimalConverter())
                .bind(BuyRentInputs::getBuyingCostPct, BuyRentInputs::setBuyingCostPct)
                .valueSignal();

        this.sellingCostSignal = this.binder.forField(this.sellingCostPct)
                .withValidator(new DoubleRangeValidator("Must be between 0 and 10", 0d, 10d))
                .withConverter(doubleToBigDecimalConverter())
                .bind(BuyRentInputs::getSellingCostPct, BuyRentInputs::setSellingCostPct)
                .valueSignal();

        this.monthlyRentSignal = this.binder.forField(this.monthlyRent)
                .asRequired("Required")
                .withValidator(new BigDecimalRangeValidator("Must be positive",
                        new BigDecimal("0.01"), null))
                .bind(BuyRentInputs::getMonthlyRent, BuyRentInputs::setMonthlyRent)
                .valueSignal();

        this.rentIncreaseSignal = this.binder.forField(this.rentIncreasePct)
                .withValidator(new DoubleRangeValidator("Must be between 0 and 20", 0d, 20d))
                .withConverter(doubleToBigDecimalConverter())
                .bind(BuyRentInputs::getRentIncreasePct, BuyRentInputs::setRentIncreasePct)
                .valueSignal();

        this.investmentReturnSignal = this.binder.forField(this.investmentReturnPct)
                .asRequired("Required")
                .withValidator(new DoubleRangeValidator("Must be between 0 and 30", 0d, 30d))
                .withConverter(doubleToBigDecimalConverter())
                .bind(BuyRentInputs::getInvestmentReturnPct, BuyRentInputs::setInvestmentReturnPct)
                .valueSignal();

        this.inflationRateSignal = this.binder.forField(this.inflationRatePct)
                .withValidator(new DoubleRangeValidator("Must be between 0 and 20", 0d, 20d))
                .withConverter(doubleToBigDecimalConverter())
                .bind(BuyRentInputs::getInflationRatePct, BuyRentInputs::setInflationRatePct)
                .valueSignal();

        this.analysisYearsSignal = this.binder.forField(this.analysisYears)
                .asRequired("Required")
                .withValidator((value, context) -> {
                    if (value == null || value < 1 || value > 50) {
                        return com.vaadin.flow.data.binder.ValidationResult.error("Must be between 1 and 50");
                    }
                    return com.vaadin.flow.data.binder.ValidationResult.ok();
                })
                .bind(BuyRentInputs::getAnalysisYears, BuyRentInputs::setAnalysisYears)
                .valueSignal();
    }

    private static FormCard card(String title, Component... children) {
        final VerticalLayout content = new VerticalLayout(children);
        content.setPadding(false);
        content.setSpacing(true);

        final FormCard formCard = new FormCard(title);
        formCard.add(content);
        formCard.setWidthFull();
        formCard.addClassName("form-section");
        return formCard;
    }

    private static FormLayout threeColForm() {
        final FormLayout layout = new FormLayout();
        layout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("36em", 2),
                new FormLayout.ResponsiveStep("64em", 3));
        return layout;
    }

    private static FormLayout twoColForm() {
        final FormLayout layout = new FormLayout();
        layout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("36em", 2));
        return layout;
    }

    private static NumberField percentageField(String label) {
        final NumberField field = new NumberField(label);
        field.setStep(0.1);
        field.setStepButtonsVisible(false);
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

    private static Converter<Double, BigDecimal> doubleToBigDecimalConverter() {
        return Converter.from(
                value -> Result.ok(value == null ? null : BigDecimal.valueOf(value)),
                value -> value == null ? null : value.doubleValue());
    }
}
