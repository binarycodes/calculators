package io.binarycodes.calculators.retirement.ui;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.BinderValidationStatus;
import com.vaadin.flow.shared.Registration;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.retirement.domain.RetirementInputs;

import java.util.ArrayList;
import java.util.List;

/**
 * The retirement-calculator input form. Composes one tab per logical group
 * ({@link BasicTab}, {@link InvestmentsTab}, {@link FutureExpensesTab},
 * {@link RetirementBenefitsTab}) into a single {@link TabSheet}. The basic
 * and investment tabs share one {@link Binder}; the future-expenses tab
 * manages its own list and is harvested at {@link #getInputs()} time.
 *
 * <h3>API</h3>
 * <ul>
 *   <li>{@link #setInputs(RetirementInputs)} — populate all fields from a bean.</li>
 *   <li>{@link #getInputs()} — write field values back into a new bean.</li>
 *   <li>{@link #isValid()} — current binder validity.</li>
 *   <li>{@link #validate()} — force-validate; returns a status object that
 *       carries the per-field errors.</li>
 *   <li>{@link #addInputChangeListener(Runnable)} — fires after any field
 *       changes, except while {@link #setInputs} is running.</li>
 * </ul>
 */
public class RetirementCalculatorForm extends VerticalLayout {

    private final Binder<RetirementInputs> binder = new Binder<>(RetirementInputs.class);
    private final FutureExpensesTab futureExpensesTab;
    private final List<Runnable> changeListeners = new ArrayList<>();
    private boolean suppressChangeEvents;

    public RetirementCalculatorForm(UserPreferences prefs) {
        addClassName("retirement-form");
        setPadding(false);
        setSpacing(true);
        setWidthFull();

        this.futureExpensesTab = new FutureExpensesTab(prefs);
        this.futureExpensesTab.addInputChangeListener(this::notifyChangeListeners);

        final var tabSheet = new TabSheet();
        tabSheet.add("Basic", new BasicTab(this.binder, prefs));
        tabSheet.add("Investments", new InvestmentsTab(this.binder, prefs));
        tabSheet.add("Future Expenses", this.futureExpensesTab);
        tabSheet.add("Retirement Benefits", new RetirementBenefitsTab());
        tabSheet.setWidthFull();
        add(tabSheet);

        this.binder.addValueChangeListener(event -> notifyChangeListeners());
    }

    public void setInputs(RetirementInputs inputs) {
        this.suppressChangeEvents = true;
        try {
            this.binder.readBean(inputs);
            this.futureExpensesTab.setFutureExpenses(inputs.getFutureExpenses());
        } finally {
            this.suppressChangeEvents = false;
        }
    }

    public RetirementInputs getInputs() {
        final var target = new RetirementInputs();
        this.binder.writeBeanAsDraft(target);
        target.setFutureExpenses(this.futureExpensesTab.getFutureExpenses());
        return target;
    }

    public boolean isValid() {
        return this.binder.isValid();
    }

    public BinderValidationStatus<RetirementInputs> validate() {
        return this.binder.validate();
    }

    public Registration addInputChangeListener(Runnable listener) {
        this.changeListeners.add(listener);
        return () -> this.changeListeners.remove(listener);
    }

    private void notifyChangeListeners() {
        if (this.suppressChangeEvents) {
            return;
        }
        this.changeListeners.forEach(Runnable::run);
    }
}
