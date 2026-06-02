package io.binarycodes.calculators.retirement.ui;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.validator.BigDecimalRangeValidator;
import com.vaadin.flow.signals.Signal;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.base.ui.MoneyField;
import io.binarycodes.calculators.retirement.domain.RetirementInputs;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static io.binarycodes.calculators.retirement.ui.FormFields.bindPercentage;
import static io.binarycodes.calculators.retirement.ui.FormFields.buildNestedSectionCard;
import static io.binarycodes.calculators.retirement.ui.FormFields.buildSectionCard;
import static io.binarycodes.calculators.retirement.ui.FormFields.percentageField;
import static io.binarycodes.calculators.retirement.ui.FormFields.withPercentageSuffix;

/**
 * The "Investments" tab: existing corpus growth, and pre/post monthly
 * contributions (with growth, step-up, and per-phase tax rate on
 * investment gains).
 */
class InvestmentsTab extends VerticalLayout {

    private final NumberField corpusReturnsPrePct = percentageField("Before Retirement");
    private final NumberField corpusReturnsPostPct = percentageField("After Retirement");
    private final NumberField corpusTaxRatePct = percentageField("Tax Rate");

    private final MoneyField monthlyInvestmentPre;
    private final NumberField sipReturnsPrePct = percentageField("Growth Percentage");
    private final NumberField sipStepUpPrePct = percentageField("Step Up Percentage (Yearly)");
    private final NumberField taxRatePrePct = percentageField("Tax Rate");

    private final MoneyField monthlyInvestmentPost;
    private final NumberField sipReturnsPostPct = percentageField("Growth Percentage");
    private final NumberField sipStepUpPostPct = percentageField("Step Up Percentage (Yearly)");
    private final NumberField taxRatePostPct = percentageField("Tax Rate");

    private final List<Signal<?>> fieldSignals = new ArrayList<>();

    InvestmentsTab(Binder<RetirementInputs> binder, UserPreferences prefs) {
        this.monthlyInvestmentPre = new MoneyField("Amount", prefs);
        this.monthlyInvestmentPost = new MoneyField("Amount", prefs);

        setPadding(false);
        setSpacing(true);
        add(
                buildSectionCard("Existing Corpus Returns",
                        withPercentageSuffix(this.corpusReturnsPrePct),
                        withPercentageSuffix(this.corpusReturnsPostPct),
                        withPercentageSuffix(this.corpusTaxRatePct)),
                buildNestedSectionCard("Monthly Contributions",
                        buildSectionCard("Before Retirement",
                                this.monthlyInvestmentPre,
                                withPercentageSuffix(this.sipReturnsPrePct),
                                withPercentageSuffix(this.sipStepUpPrePct),
                                withPercentageSuffix(this.taxRatePrePct)),
                        buildSectionCard("After Retirement",
                                this.monthlyInvestmentPost,
                                withPercentageSuffix(this.sipReturnsPostPct),
                                withPercentageSuffix(this.sipStepUpPostPct),
                                withPercentageSuffix(this.taxRatePostPct))));

        configureBindings(binder);
    }

    Stream<Signal<?>> fieldSignals() {
        return this.fieldSignals.stream();
    }

    private void configureBindings(Binder<RetirementInputs> binder) {
        this.fieldSignals.add(binder.forField(this.monthlyInvestmentPre)
                .asRequired("Required")
                .withValidator(new BigDecimalRangeValidator("Must be non-negative",
                        BigDecimal.ZERO, null))
                .bind(RetirementInputs::getMonthlyInvPre, RetirementInputs::setMonthlyInvPre)
                .valueSignal());

        this.fieldSignals.add(binder.forField(this.monthlyInvestmentPost)
                .asRequired("Required")
                .withValidator(new BigDecimalRangeValidator("Must be non-negative",
                        BigDecimal.ZERO, null))
                .bind(RetirementInputs::getMonthlyInvPost, RetirementInputs::setMonthlyInvPost)
                .valueSignal());

        this.fieldSignals.add(bindPercentage(binder, this.corpusReturnsPrePct,
                RetirementInputs::getGrowthPrePct, RetirementInputs::setGrowthPrePct).valueSignal());
        this.fieldSignals.add(bindPercentage(binder, this.corpusReturnsPostPct,
                RetirementInputs::getGrowthPostPct, RetirementInputs::setGrowthPostPct).valueSignal());
        this.fieldSignals.add(bindPercentage(binder, this.sipReturnsPrePct,
                RetirementInputs::getSipGrowthPrePct, RetirementInputs::setSipGrowthPrePct).valueSignal());
        this.fieldSignals.add(bindPercentage(binder, this.sipReturnsPostPct,
                RetirementInputs::getSipGrowthPostPct, RetirementInputs::setSipGrowthPostPct).valueSignal());
        this.fieldSignals.add(bindPercentage(binder, this.sipStepUpPrePct,
                RetirementInputs::getSipStepUpPrePct, RetirementInputs::setSipStepUpPrePct).valueSignal());
        this.fieldSignals.add(bindPercentage(binder, this.sipStepUpPostPct,
                RetirementInputs::getSipStepUpPostPct, RetirementInputs::setSipStepUpPostPct).valueSignal());
        this.fieldSignals.add(bindPercentage(binder, this.taxRatePrePct,
                RetirementInputs::getTaxRatePrePct, RetirementInputs::setTaxRatePrePct).valueSignal());
        this.fieldSignals.add(bindPercentage(binder, this.taxRatePostPct,
                RetirementInputs::getTaxRatePostPct, RetirementInputs::setTaxRatePostPct).valueSignal());
        this.fieldSignals.add(bindPercentage(binder, this.corpusTaxRatePct,
                RetirementInputs::getCorpusTaxRatePct, RetirementInputs::setCorpusTaxRatePct).valueSignal());
    }
}
