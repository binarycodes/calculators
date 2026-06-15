package io.binarycodes.calculators.retirement.ui;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.BinderValidationStatus;
import com.vaadin.flow.signals.Signal;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.base.ui.CalculatorForm;
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
        tabSheet.add("Basic", this.basicTab);
        tabSheet.add("Investments", this.investmentsTab);
        tabSheet.add("Future Expenses", this.futureExpensesTab);
        tabSheet.add("Future Incomes", this.futureIncomesTab);
        tabSheet.add("Retirement Benefits", this.retirementBenefitsTab);
        tabSheet.setWidthFull();
        add(tabSheet);

        this.inputsSignal = Signal.computed(() -> {
            // Touch each source signal so the computed re-runs when any one
            // of them changes. The list-tab signals are read for their values;
            // the binder-tab field signals are touched purely as dependencies
            // (the binder is the authoritative source via writeBeanAsDraft).
            this.basicTab.fieldSignals().forEach(Signal::get);
            this.investmentsTab.fieldSignals().forEach(Signal::get);
            this.futureExpensesTab.futureExpensesSignal().get();
            this.futureExpensesTab.recurringExpensesSignal().get();
            this.futureIncomesTab.futureIncomesSignal().get();
            this.futureIncomesTab.recurringIncomesSignal().get();
            this.retirementBenefitsTab.retirementBenefitsSignal().get();
            return buildInputs();
        });
    }

    public Signal<RetirementInputs> inputsSignal() {
        return this.inputsSignal;
    }

    public void setInputs(RetirementInputs inputs) {
        // Local Signal.effect observers schedule their re-run, so the
        // sequential field/list writes here naturally coalesce into one
        // downstream effect invocation without needing explicit batching.
        this.binder.readBean(inputs);
        this.futureExpensesTab.setFutureExpenses(inputs.getFutureExpenses());
        this.futureExpensesTab.setRecurringExpenses(inputs.getRecurringExpenses());
        this.retirementBenefitsTab.setRetirementBenefits(inputs.getRetirementBenefits());
        this.futureIncomesTab.setFutureIncomes(inputs.getFutureIncomes());
        this.futureIncomesTab.setRecurringIncomes(inputs.getRecurringIncomes());
    }

    public RetirementInputs getInputs() {
        return buildInputs();
    }

    public boolean isValid() {
        return this.binder.isValid();
    }

    public BinderValidationStatus<RetirementInputs> validate() {
        return this.binder.validate();
    }

    private RetirementInputs buildInputs() {
        final var target = new RetirementInputs();
        this.binder.writeBeanAsDraft(target);
        target.setFutureExpenses(this.futureExpensesTab.getFutureExpenses());
        target.setRecurringExpenses(this.futureExpensesTab.getRecurringExpenses());
        target.setRetirementBenefits(this.retirementBenefitsTab.getRetirementBenefits());
        target.setFutureIncomes(this.futureIncomesTab.getFutureIncomes());
        target.setRecurringIncomes(this.futureIncomesTab.getRecurringIncomes());
        return target;
    }
}
