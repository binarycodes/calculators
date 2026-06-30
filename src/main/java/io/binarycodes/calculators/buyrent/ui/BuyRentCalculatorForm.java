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
import io.binarycodes.calculators.base.i18n.Translations;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.base.ui.CalculatorForm;
import io.binarycodes.calculators.base.ui.FormCard;
import io.binarycodes.calculators.base.ui.MoneyField;
import io.binarycodes.calculators.base.ui.PercentageField;
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
    private final NumberField downPaymentPct = PercentageField.create(Translations.get("field.downPayment"));
    private final IntegerField loanTermYears = yearsField(Translations.get("field.loanTerm"));
    private final NumberField mortgageRatePct = PercentageField.create(Translations.get("field.mortgageRate"));

    private final NumberField propertyTaxRatePct = PercentageField.create(Translations.get("field.propertyTaxRate"));
    private final NumberField maintenancePct = PercentageField.create(Translations.get("field.maintenanceRate"));
    private final NumberField appreciationPct = PercentageField.create(Translations.get("field.homeAppreciation"));
    private final NumberField buyingCostPct = PercentageField.create(Translations.get("field.buyingCosts"));
    private final NumberField sellingCostPct = PercentageField.create(Translations.get("field.sellingCosts"));

    private final MoneyField monthlyRent;
    private final NumberField rentIncreasePct = PercentageField.create(Translations.get("field.annualRentIncrease"));

    private final NumberField investmentReturnPct = PercentageField.create(Translations.get("field.investmentReturn"));
    private final NumberField inflationRatePct = PercentageField.create(Translations.get("field.inflationRate"));
    private final IntegerField analysisYears = yearsField(Translations.get("field.analysisHorizon"));
    private final NumberField propertyCapitalGainsTaxPct = PercentageField.create(Translations.get("field.propertyCapitalGainsTax"));
    private final NumberField investmentGainsTaxPct = PercentageField.create(Translations.get("field.investmentGainsTax"));

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
    private Signal<?> propertyCapitalGainsTaxSignal;
    private Signal<?> investmentGainsTaxSignal;

    private final Signal<BuyRentInputs> inputsSignal;

    public BuyRentCalculatorForm(UserPreferences preferences) {
        addClassName("buyrent-form");
        setPadding(false);
        setSpacing(true);
        setWidthFull();

        this.homePrice = new MoneyField(Translations.get("field.homePrice"), preferences);
        this.monthlyRent = new MoneyField(Translations.get("field.monthlyRent"), preferences);

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
            this.propertyCapitalGainsTaxSignal.get();
            this.investmentGainsTaxSignal.get();
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
        this.homePurchaseCard = card(Translations.get("section.buyrent.homePurchase"), layout);
        return this.homePurchaseCard;
    }

    private Component buildCostsCard() {
        final FormLayout layout = threeColForm();
        layout.add(withPercentageSuffix(this.propertyTaxRatePct),
                withPercentageSuffix(this.maintenancePct),
                withPercentageSuffix(this.appreciationPct),
                withPercentageSuffix(this.buyingCostPct),
                withPercentageSuffix(this.sellingCostPct));

        final Span hint = new Span(Translations.get("buyrent.costs.hint"));
        hint.addClassName("subsection-hint");

        final VerticalLayout content = new VerticalLayout(layout, hint);
        content.setPadding(false);
        content.setSpacing(true);
        return card(Translations.get("section.buyrent.costs"), content);
    }

    private Component buildRentingCard() {
        final FormLayout layout = twoColForm();
        layout.add(this.monthlyRent, withPercentageSuffix(this.rentIncreasePct));
        return card(Translations.get("section.buyrent.renting"), layout);
    }

    private Component buildAnalysisCard() {
        final FormLayout layout = threeColForm();
        layout.add(withPercentageSuffix(this.investmentReturnPct),
                withPercentageSuffix(this.inflationRatePct), this.analysisYears,
                withPercentageSuffix(this.propertyCapitalGainsTaxPct),
                withPercentageSuffix(this.investmentGainsTaxPct));

        final Span hint = new Span(Translations.get("buyrent.analysis.hint"));
        hint.addClassName("subsection-hint");

        final VerticalLayout content = new VerticalLayout(layout, hint);
        content.setPadding(false);
        content.setSpacing(true);
        return card(Translations.get("section.buyrent.analysis"), content);
    }

    private void configureBindings() {
        this.homePriceSignal = this.binder.forField(this.homePrice)
                .asRequired(Translations.get("validation.required"))
                .withValidator(new BigDecimalRangeValidator(Translations.get("validation.positive"),
                        new BigDecimal("0.01"), null))
                .bind(BuyRentInputs::getHomePrice, BuyRentInputs::setHomePrice)
                .valueSignal();

        this.downPaymentSignal = this.binder.forField(this.downPaymentPct)
                .asRequired(Translations.get("validation.required"))
                .withValidator(new DoubleRangeValidator(Translations.get("validation.between", 0, 99), 0d, 99d))
                .withConverter(doubleToBigDecimalConverter())
                .bind(BuyRentInputs::getDownPaymentPct, BuyRentInputs::setDownPaymentPct)
                .valueSignal();

        this.loanTermSignal = this.binder.forField(this.loanTermYears)
                .asRequired(Translations.get("validation.required"))
                .withValidator((value, context) -> {
                    if (value == null || value < 1 || value > 40) {
                        return com.vaadin.flow.data.binder.ValidationResult.error(Translations.get("validation.between", 1, 40));
                    }
                    return com.vaadin.flow.data.binder.ValidationResult.ok();
                })
                .bind(BuyRentInputs::getLoanTermYears, BuyRentInputs::setLoanTermYears)
                .valueSignal();

        this.mortgageRateSignal = this.binder.forField(this.mortgageRatePct)
                .asRequired(Translations.get("validation.required"))
                .withValidator(new DoubleRangeValidator(Translations.get("validation.between", 0, 30), 0d, 30d))
                .withConverter(doubleToBigDecimalConverter())
                .bind(BuyRentInputs::getMortgageRatePct, BuyRentInputs::setMortgageRatePct)
                .valueSignal();

        this.propertyTaxSignal = this.binder.forField(this.propertyTaxRatePct)
                .withValidator(new DoubleRangeValidator(Translations.get("validation.between", 0, 5), 0d, 5d))
                .withConverter(doubleToBigDecimalConverter())
                .bind(BuyRentInputs::getPropertyTaxRatePct, BuyRentInputs::setPropertyTaxRatePct)
                .valueSignal();

        this.maintenanceSignal = this.binder.forField(this.maintenancePct)
                .withValidator(new DoubleRangeValidator(Translations.get("validation.between", 0, 5), 0d, 5d))
                .withConverter(doubleToBigDecimalConverter())
                .bind(BuyRentInputs::getMaintenancePct, BuyRentInputs::setMaintenancePct)
                .valueSignal();

        this.appreciationSignal = this.binder.forField(this.appreciationPct)
                .withValidator(new DoubleRangeValidator(Translations.get("validation.between", 0, 20), 0d, 20d))
                .withConverter(doubleToBigDecimalConverter())
                .bind(BuyRentInputs::getAppreciationPct, BuyRentInputs::setAppreciationPct)
                .valueSignal();

        this.buyingCostSignal = this.binder.forField(this.buyingCostPct)
                .withValidator(new DoubleRangeValidator(Translations.get("validation.between", 0, 15), 0d, 15d))
                .withConverter(doubleToBigDecimalConverter())
                .bind(BuyRentInputs::getBuyingCostPct, BuyRentInputs::setBuyingCostPct)
                .valueSignal();

        this.sellingCostSignal = this.binder.forField(this.sellingCostPct)
                .withValidator(new DoubleRangeValidator(Translations.get("validation.between", 0, 10), 0d, 10d))
                .withConverter(doubleToBigDecimalConverter())
                .bind(BuyRentInputs::getSellingCostPct, BuyRentInputs::setSellingCostPct)
                .valueSignal();

        this.monthlyRentSignal = this.binder.forField(this.monthlyRent)
                .asRequired(Translations.get("validation.required"))
                .withValidator(new BigDecimalRangeValidator(Translations.get("validation.positive"),
                        new BigDecimal("0.01"), null))
                .bind(BuyRentInputs::getMonthlyRent, BuyRentInputs::setMonthlyRent)
                .valueSignal();

        this.rentIncreaseSignal = this.binder.forField(this.rentIncreasePct)
                .withValidator(new DoubleRangeValidator(Translations.get("validation.between", 0, 20), 0d, 20d))
                .withConverter(doubleToBigDecimalConverter())
                .bind(BuyRentInputs::getRentIncreasePct, BuyRentInputs::setRentIncreasePct)
                .valueSignal();

        this.investmentReturnSignal = this.binder.forField(this.investmentReturnPct)
                .asRequired(Translations.get("validation.required"))
                .withValidator(new DoubleRangeValidator(Translations.get("validation.between", 0, 30), 0d, 30d))
                .withConverter(doubleToBigDecimalConverter())
                .bind(BuyRentInputs::getInvestmentReturnPct, BuyRentInputs::setInvestmentReturnPct)
                .valueSignal();

        this.inflationRateSignal = this.binder.forField(this.inflationRatePct)
                .withValidator(new DoubleRangeValidator(Translations.get("validation.between", 0, 20), 0d, 20d))
                .withConverter(doubleToBigDecimalConverter())
                .bind(BuyRentInputs::getInflationRatePct, BuyRentInputs::setInflationRatePct)
                .valueSignal();

        this.analysisYearsSignal = this.binder.forField(this.analysisYears)
                .asRequired(Translations.get("validation.required"))
                .withValidator((value, context) -> {
                    if (value == null || value < 1 || value > 50) {
                        return com.vaadin.flow.data.binder.ValidationResult.error(Translations.get("validation.between", 1, 50));
                    }
                    return com.vaadin.flow.data.binder.ValidationResult.ok();
                })
                .bind(BuyRentInputs::getAnalysisYears, BuyRentInputs::setAnalysisYears)
                .valueSignal();

        this.propertyCapitalGainsTaxSignal = this.binder.forField(this.propertyCapitalGainsTaxPct)
                .withValidator(new DoubleRangeValidator(Translations.get("validation.between", 0, 60), 0d, 60d))
                .withConverter(doubleToBigDecimalConverter())
                .bind(BuyRentInputs::getPropertyCapitalGainsTaxPct, BuyRentInputs::setPropertyCapitalGainsTaxPct)
                .valueSignal();

        this.investmentGainsTaxSignal = this.binder.forField(this.investmentGainsTaxPct)
                .withValidator(new DoubleRangeValidator(Translations.get("validation.between", 0, 60), 0d, 60d))
                .withConverter(doubleToBigDecimalConverter())
                .bind(BuyRentInputs::getInvestmentGainsTaxPct, BuyRentInputs::setInvestmentGainsTaxPct)
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

    private static IntegerField yearsField(String label) {
        final IntegerField field = new IntegerField(label);
        field.setStepButtonsVisible(false);
        field.setSuffixComponent(secondaryText(Translations.get("unit.yrs")));
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

    private static Converter<Double, BigDecimal> doubleToBigDecimalConverter() {
        return Converter.from(
                value -> Result.ok(value == null ? null : BigDecimal.valueOf(value)),
                value -> value == null ? null : value.doubleValue());
    }
}
