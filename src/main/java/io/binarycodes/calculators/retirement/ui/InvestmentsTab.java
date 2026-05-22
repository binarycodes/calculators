package io.binarycodes.calculators.retirement.ui;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.validator.BigDecimalRangeValidator;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.base.ui.MoneyField;
import io.binarycodes.calculators.retirement.domain.RetirementInputs;

import java.math.BigDecimal;

import static io.binarycodes.calculators.retirement.ui.FormFields.bindPercentage;
import static io.binarycodes.calculators.retirement.ui.FormFields.buildNestedSectionCard;
import static io.binarycodes.calculators.retirement.ui.FormFields.buildSectionCard;
import static io.binarycodes.calculators.retirement.ui.FormFields.percentageField;
import static io.binarycodes.calculators.retirement.ui.FormFields.withPercentageSuffix;

/**
 * The "Investments" tab: existing corpus growth, pre/post SIP contributions
 * (with growth + step-up), and the (currently UI-only) tax rate.
 */
class InvestmentsTab extends VerticalLayout {

    private final NumberField corpusReturnsPrePct = percentageField("Before Retirement");
    private final NumberField corpusReturnsPostPct = percentageField("After Retirement");

    private final MoneyField monthlyInvestmentPre;
    private final NumberField sipReturnsPrePct = percentageField("Growth Percentage");
    private final NumberField sipStepUpPrePct = percentageField("Step Up Percentage (Yearly)");

    private final MoneyField monthlyInvestmentPost;
    private final NumberField sipReturnsPostPct = percentageField("Growth Percentage");
    private final NumberField sipStepUpPostPct = percentageField("Step Up Percentage (Yearly)");

    private final NumberField taxRatePct = percentageField("Tax Rate (on investment gains)");

    InvestmentsTab(Binder<RetirementInputs> binder, UserPreferences prefs) {
        this.monthlyInvestmentPre = new MoneyField("Monthly SIP", prefs);
        this.monthlyInvestmentPost = new MoneyField("Monthly SIP", prefs);

        setPadding(false);
        setSpacing(true);
        add(
                buildSectionCard("Existing Corpus Returns",
                        withPercentageSuffix(this.corpusReturnsPrePct),
                        withPercentageSuffix(this.corpusReturnsPostPct)),
                buildNestedSectionCard("Monthly SIP Contributions",
                        buildSectionCard("Before Retirement",
                                this.monthlyInvestmentPre,
                                withPercentageSuffix(this.sipReturnsPrePct),
                                withPercentageSuffix(this.sipStepUpPrePct)),
                        buildSectionCard("After Retirement",
                                this.monthlyInvestmentPost,
                                withPercentageSuffix(this.sipReturnsPostPct),
                                withPercentageSuffix(this.sipStepUpPostPct))),
                buildSectionCard("Taxation",
                        withPercentageSuffix(this.taxRatePct)));

        configureBindings(binder);
    }

    private void configureBindings(Binder<RetirementInputs> binder) {
        binder.forField(this.monthlyInvestmentPre)
                .asRequired("Required")
                .withValidator(new BigDecimalRangeValidator("Must be non-negative",
                        BigDecimal.ZERO, null))
                .bind(RetirementInputs::getMonthlyInvPre, RetirementInputs::setMonthlyInvPre);

        binder.forField(this.monthlyInvestmentPost)
                .asRequired("Required")
                .withValidator(new BigDecimalRangeValidator("Must be non-negative",
                        BigDecimal.ZERO, null))
                .bind(RetirementInputs::getMonthlyInvPost, RetirementInputs::setMonthlyInvPost);

        bindPercentage(binder, this.corpusReturnsPrePct,
                RetirementInputs::getGrowthPrePct, RetirementInputs::setGrowthPrePct);
        bindPercentage(binder, this.corpusReturnsPostPct,
                RetirementInputs::getGrowthPostPct, RetirementInputs::setGrowthPostPct);
        bindPercentage(binder, this.sipReturnsPrePct,
                RetirementInputs::getSipGrowthPrePct, RetirementInputs::setSipGrowthPrePct);
        bindPercentage(binder, this.sipReturnsPostPct,
                RetirementInputs::getSipGrowthPostPct, RetirementInputs::setSipGrowthPostPct);
        bindPercentage(binder, this.sipStepUpPrePct,
                RetirementInputs::getSipStepUpPrePct, RetirementInputs::setSipStepUpPrePct);
        bindPercentage(binder, this.sipStepUpPostPct,
                RetirementInputs::getSipStepUpPostPct, RetirementInputs::setSipStepUpPostPct);
        bindPercentage(binder, this.taxRatePct,
                RetirementInputs::getTaxRatePct, RetirementInputs::setTaxRatePct);
    }
}
