package io.binarycodes.calculators.irr.ui;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.signals.Signal;
import io.binarycodes.calculators.base.i18n.Translations;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.base.ui.CalculatorForm;
import io.binarycodes.calculators.base.ui.FormCard;
import io.binarycodes.calculators.base.ui.TabIndicator;
import io.binarycodes.calculators.irr.domain.XirrInputs;

/**
 * The IRR/XIRR input form: an Investments tab and a Withdrawals tab, each a
 * {@link CashflowSection} with a one-off and a recurring card. The sections
 * expose their lists as signals, which this form folds into a single
 * {@link #inputsSignal()} the view observes for live recalculation.
 */
public class XirrCalculatorForm extends VerticalLayout implements CalculatorForm<XirrInputs> {

    private final CashflowSection investmentsSection;
    private final CashflowSection withdrawalsSection;
    private final Span investmentsDot = TabIndicator.dot(Translations.get("tab.xirr.investments"));
    private final Span withdrawalsDot = TabIndicator.dot(Translations.get("tab.xirr.withdrawals"));
    private final Signal<XirrInputs> inputsSignal;

    public XirrCalculatorForm(UserPreferences preferences) {
        addClassName("xirr-form");
        setPadding(false);
        setSpacing(true);
        setWidthFull();

        this.investmentsSection = new CashflowSection(preferences, new CashflowSection.Labels(
                Translations.get("section.xirr.oneOffInvestments"),
                Translations.get("irr.oneOffInvestmentsIntro"),
                Translations.get("irr.addOneOffInvestment"),
                Translations.get("section.xirr.recurringInvestments"),
                Translations.get("irr.recurringInvestmentsIntro"),
                Translations.get("irr.addRecurringInvestment")));
        this.withdrawalsSection = new CashflowSection(preferences, new CashflowSection.Labels(
                Translations.get("section.xirr.oneOffWithdrawals"),
                Translations.get("irr.oneOffWithdrawalsIntro"),
                Translations.get("irr.addOneOffWithdrawal"),
                Translations.get("section.xirr.recurringWithdrawals"),
                Translations.get("irr.recurringWithdrawalsIntro"),
                Translations.get("irr.addRecurringWithdrawal")));

        final var tabSheet = new TabSheet();
        tabSheet.add(new Tab(new Span(Translations.get("tab.xirr.investments")), this.investmentsDot),
                this.investmentsSection);
        tabSheet.add(new Tab(new Span(Translations.get("tab.xirr.withdrawals")), this.withdrawalsDot),
                this.withdrawalsSection);
        tabSheet.setWidthFull();
        add(tabSheet);

        this.inputsSignal = Signal.computed(() -> {
            this.investmentsSection.oneOffSignal().get();
            this.investmentsSection.recurringSignal().get();
            this.withdrawalsSection.oneOffSignal().get();
            this.withdrawalsSection.recurringSignal().get();
            return buildInputs();
        });

        Signal.effect(this, context -> {
            this.inputsSignal.get();
            refreshIndicators();
        });
    }

    public Signal<XirrInputs> inputsSignal() {
        return this.inputsSignal;
    }

    public void setInputs(XirrInputs inputs) {
        this.investmentsSection.setOneOff(inputs.getOneOffInvestments());
        this.investmentsSection.setRecurring(inputs.getRecurringInvestments());
        this.withdrawalsSection.setOneOff(inputs.getOneOffWithdrawals());
        this.withdrawalsSection.setRecurring(inputs.getRecurringWithdrawals());
    }

    public void clear() {
        setInputs(new XirrInputs());
    }

    public XirrInputs getInputs() {
        return buildInputs();
    }

    public boolean isValid() {
        return this.investmentsSection.isValid() && this.withdrawalsSection.isValid();
    }

    @Override
    public void showValidationMessages(String calculationError) {
        FormCard.refreshGenericErrors(this);
        if (calculationError != null) {
            FormCard.firstCard(this)
                    .filter(card -> !card.hasInvalidField())
                    .ifPresent(card -> card.showError(calculationError));
        }
    }

    private void refreshIndicators() {
        TabIndicator.apply(this.investmentsDot, this.investmentsSection);
        TabIndicator.apply(this.withdrawalsDot, this.withdrawalsSection);
    }

    private XirrInputs buildInputs() {
        final var inputs = new XirrInputs();
        inputs.setOneOffInvestments(this.investmentsSection.getOneOff());
        inputs.setRecurringInvestments(this.investmentsSection.getRecurring());
        inputs.setOneOffWithdrawals(this.withdrawalsSection.getOneOff());
        inputs.setRecurringWithdrawals(this.withdrawalsSection.getRecurring());
        return inputs;
    }
}
