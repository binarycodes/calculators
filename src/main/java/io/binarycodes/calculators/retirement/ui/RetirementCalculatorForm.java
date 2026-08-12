package io.binarycodes.calculators.retirement.ui;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.BinderValidationStatus;
import com.vaadin.flow.signals.Signal;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.base.i18n.Translations;
import io.binarycodes.calculators.base.ui.CalculatorForm;
import io.binarycodes.calculators.base.ui.FormCard;
import io.binarycodes.calculators.base.ui.TabIndicator;
import io.binarycodes.calculators.retirement.domain.RetirementInputs;

/**
 * The retirement-calculator input form. Composes one tab per logical group
 * ({@link BasicTab}, {@link InvestmentsTab}, {@link FutureExpensesTab},
 * {@link RetirementBenefitsTab}) into a single {@link TabSheet}. The basic
 * and investment tabs share one {@link Binder}; the list-based tabs expose
 * their state as {@link Signal}s.
 *
 * <h3>API</h3>
 * <ul>
 *   <li>{@link #setInputs(RetirementInputs)} — populate all fields from a bean.</li>
 *   <li>{@link #getInputs()} — write field values back into a new bean.</li>
 *   <li>{@link #isValid()} — current binder validity.</li>
 *   <li>{@link #validate()} — force-validate; returns a status object that
 *       carries the per-field errors.</li>
 *   <li>{@link #inputsSignal()} — reactive view of the form contents.
 *       Observers (e.g. {@link RetirementView}) subscribe via
 *       {@link Signal#effect(com.vaadin.flow.component.Component,
 *       com.vaadin.flow.signals.function.ContextualEffectAction)} to be
 *       notified whenever any input changes.</li>
 * </ul>
 */
public class RetirementCalculatorForm extends VerticalLayout implements CalculatorForm<RetirementInputs> {

    private final Binder<RetirementInputs> binder = new Binder<>(RetirementInputs.class);
    private final BasicTab basicTab;
    private final InvestmentsTab investmentsTab;
    private final FutureExpensesTab futureExpensesTab;
    private final RetirementBenefitsTab retirementBenefitsTab;
    private final FutureIncomesTab futureIncomesTab;
    private final Span basicDot = TabIndicator.dot(Translations.get("retirement.dot.filledIn"));
    private final Span investmentsDot = TabIndicator.dot(Translations.get("retirement.dot.filledIn"));
    private final Span futureExpensesDot = TabIndicator.dot(Translations.get("retirement.dot.futureExpenses"));
    private final Span futureIncomesDot = TabIndicator.dot(Translations.get("retirement.dot.futureIncomes"));
    private final Span retirementBenefitsDot = TabIndicator.dot(Translations.get("retirement.dot.retirementBenefits"));
    private final Signal<RetirementInputs> inputsSignal;

    public RetirementCalculatorForm(UserPreferences prefs) {
        addClassName("retirement-form");
        setPadding(false);
        setSpacing(true);
        setWidthFull();

        this.basicTab = new BasicTab(this.binder, prefs);
        this.investmentsTab = new InvestmentsTab(this.binder, prefs);
        this.futureExpensesTab = new FutureExpensesTab(prefs);
        this.retirementBenefitsTab = new RetirementBenefitsTab(prefs);
        this.futureIncomesTab = new FutureIncomesTab(prefs);

        final var tabSheet = new TabSheet();
        tabSheet.add(new Tab(new Span(Translations.get("tab.retirement.basic")), this.basicDot), this.basicTab);
        tabSheet.add(new Tab(new Span(Translations.get("tab.retirement.investments")), this.investmentsDot), this.investmentsTab);
        tabSheet.add(new Tab(new Span(Translations.get("tab.retirement.futureExpenses")), this.futureExpensesDot), this.futureExpensesTab);
        tabSheet.add(new Tab(new Span(Translations.get("tab.retirement.futureIncomes")), this.futureIncomesDot), this.futureIncomesTab);
        tabSheet.add(new Tab(new Span(Translations.get("tab.retirement.retirementBenefits")), this.retirementBenefitsDot),
                this.retirementBenefitsTab);
        tabSheet.setWidthFull();
        add(tabSheet);

        this.inputsSignal = Signal.computed(() -> {
            // Touch each source signal so the computed re-runs when any one
            // of them changes. The list-tab signals are read for their values;
            // the binder-tab field signals are touched purely as dependencies
            // (the binder is the authoritative source via writeBeanAsDraft).
            this.basicTab.fieldSignals().forEach(Signal::get);
            this.investmentsTab.fieldSignals().forEach(Signal::get);
            this.investmentsTab.preContributionsSignal().get();
            this.investmentsTab.postContributionsSignal().get();
            this.futureExpensesTab.futureExpensesSignal().get();
            this.futureExpensesTab.recurringExpensesSignal().get();
            this.futureIncomesTab.futureIncomesSignal().get();
            this.futureIncomesTab.recurringIncomesSignal().get();
            this.retirementBenefitsTab.retirementBenefitsSignal().get();
            return buildInputs();
        });

        // Mark each tab that holds a value; flag it red when one of its fields is
        // invalid. The signal effect catches value/list changes; the binder status
        // listener catches validity changes (e.g. cross-field) that carry no value
        // change of their own.
        Signal.effect(this, context -> {
            this.inputsSignal.get();
            refreshIndicators();
        });
        this.binder.addStatusChangeListener(event -> refreshIndicators());
    }

    private void refreshIndicators() {
        TabIndicator.apply(this.basicDot, this.basicTab);
        TabIndicator.apply(this.investmentsDot, this.investmentsTab);
        TabIndicator.apply(this.futureExpensesDot, this.futureExpensesTab);
        TabIndicator.apply(this.futureIncomesDot, this.futureIncomesTab);
        TabIndicator.apply(this.retirementBenefitsDot, this.retirementBenefitsTab);
    }

    public Signal<RetirementInputs> inputsSignal() {
        return this.inputsSignal;
    }

    public void setInputs(RetirementInputs inputs) {
        // Local Signal.effect observers schedule their re-run, so the
        // sequential field/list writes here naturally coalesce into one
        // downstream effect invocation without needing explicit batching.
        this.binder.readBean(inputs);
        this.investmentsTab.setPreContributions(inputs.getPreRetirementContributions());
        this.investmentsTab.setPostContributions(inputs.getPostRetirementContributions());
        this.futureExpensesTab.setFutureExpenses(inputs.getFutureExpenses());
        this.futureExpensesTab.setRecurringExpenses(inputs.getRecurringExpenses());
        this.retirementBenefitsTab.setRetirementBenefits(inputs.getRetirementBenefits());
        this.futureIncomesTab.setFutureIncomes(inputs.getFutureIncomes());
        this.futureIncomesTab.setRecurringIncomes(inputs.getRecurringIncomes());
    }

    public void clear() {
        setInputs(new RetirementInputs());
    }

    public RetirementInputs getInputs() {
        return buildInputs();
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

    public boolean isValid() {
        return this.binder.isValid()
                && this.investmentsTab.isValid()
                && this.futureExpensesTab.isValid()
                && this.futureIncomesTab.isValid()
                && this.retirementBenefitsTab.isValid();
    }

    public BinderValidationStatus<RetirementInputs> validate() {
        // Row-level required fields validate themselves on creation and on change,
        // so they already show their errors; only the bean binder needs forcing.
        return this.binder.validate();
    }

    private RetirementInputs buildInputs() {
        final var target = new RetirementInputs();
        this.binder.writeBeanAsDraft(target);
        target.setPreRetirementContributions(this.investmentsTab.getPreContributions());
        target.setPostRetirementContributions(this.investmentsTab.getPostContributions());
        target.setFutureExpenses(this.futureExpensesTab.getFutureExpenses());
        target.setRecurringExpenses(this.futureExpensesTab.getRecurringExpenses());
        target.setRetirementBenefits(this.retirementBenefitsTab.getRetirementBenefits());
        target.setFutureIncomes(this.futureIncomesTab.getFutureIncomes());
        target.setRecurringIncomes(this.futureIncomesTab.getRecurringIncomes());
        return target;
    }
}
