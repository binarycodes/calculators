package io.binarycodes.calculators.goal.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ValueSignal;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.base.ui.FormCard;
import io.binarycodes.calculators.base.ui.MoneyField;
import io.binarycodes.calculators.base.ui.RowControls;
import io.binarycodes.calculators.goal.domain.Investment;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Editable list of {@link Investment} buckets. Each row carries its own
 * corpus, growth, tax, step-up, and allocation share; the user can add or
 * remove rows freely.
 *
 * <p>When the allocation shares don't sum to 100% the card flags it through its
 * top-right validation message; the calculator also enforces the constraint
 * when called.</p>
 */
public class InvestmentsCard extends FormCard {

    private final UserPreferences preferences;
    private final VerticalLayout rowsContainer = new VerticalLayout();
    private final List<InvestmentRow> rows = new ArrayList<>();
    private final ValueSignal<List<Investment>> investmentsSignal = new ValueSignal<>(List.of());

    public InvestmentsCard(UserPreferences preferences) {
        super("Investments");
        this.preferences = preferences;

        setWidthFull();
        addClassName("form-section");
        addClassName("investments-card");

        final Span intro = new Span("List every bucket the SIP should flow into. "
                + "Each row carries its own corpus, growth, tax, and step-up; "
                + "allocations must sum to 100%.");
        intro.getStyle().setColor("var(--vaadin-secondary-text-color, #71717a)");

        this.rowsContainer.setPadding(false);
        this.rowsContainer.setSpacing(true);
        this.rowsContainer.setWidthFull();

        final Button addButton = new Button("Add investment", VaadinIcon.PLUS.create(),
                event -> addRow(blank()));
        addButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        final VerticalLayout content = new VerticalLayout(intro, this.rowsContainer, addButton);
        content.setPadding(false);
        content.setSpacing(true);
        add(content);
    }

    public Signal<List<Investment>> investmentsSignal() {
        return this.investmentsSignal.asReadonly();
    }

    public List<Investment> getInvestments() {
        return snapshot();
    }

    public void setInvestments(List<Investment> investments) {
        this.rowsContainer.removeAll();
        this.rows.clear();
        if (investments != null) {
            for (final Investment investment : investments) {
                addRow(investment);
            }
        }
        if (this.rows.isEmpty()) {
            addRow(blank());
        }
        publish();
    }

    public boolean isAllocationValid() {
        return allocationSum().subtract(BigDecimal.valueOf(100)).abs()
                .compareTo(new BigDecimal("0.01")) <= 0;
    }

    private static Investment blank() {
        return new Investment(null, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private void addRow(Investment investment) {
        final InvestmentRow row = new InvestmentRow(this.preferences, investment,
                this::removeRow, this::publish);
        this.rows.add(row);
        this.rowsContainer.add(row);
    }

    private void removeRow(InvestmentRow row) {
        if (this.rows.remove(row)) {
            this.rowsContainer.remove(row);
            publish();
        }
    }

    private void publish() {
        this.investmentsSignal.set(snapshot());
    }

    private BigDecimal allocationSum() {
        BigDecimal sum = BigDecimal.ZERO;
        for (final InvestmentRow row : this.rows) {
            final BigDecimal allocation = row.allocation();
            if (allocation != null) {
                sum = sum.add(allocation);
            }
        }
        return sum;
    }

    private List<Investment> snapshot() {
        final List<Investment> out = new ArrayList<>();
        for (final InvestmentRow row : this.rows) {
            out.add(row.snapshot());
        }
        return out;
    }

    private static final class InvestmentRow extends HorizontalLayout {
        private final TextField labelField = new TextField("Label");
        private final MoneyField corpusField;
        private final NumberField growthField = percentage("Growth %");
        private final NumberField taxField = percentage("Tax %");
        private final NumberField stepUpField = percentage("Step-Up %");
        private final NumberField allocationField = percentage("Allocation %");

        InvestmentRow(UserPreferences preferences, Investment initial,
                      Consumer<InvestmentRow> onRemove, Runnable onChanged) {
            this.corpusField = new MoneyField("Current", preferences);

            this.labelField.setValueChangeMode(ValueChangeMode.LAZY);
            this.labelField.setValue(initial.getLabel() == null ? "" : initial.getLabel());
            this.corpusField.setValue(initial.getCurrentCorpus());
            this.growthField.setValue(toDouble(initial.getGrowthRatePct()));
            this.taxField.setValue(toDouble(initial.getWithdrawalTaxRatePct()));
            this.stepUpField.setValue(toDouble(initial.getStepUpPct()));
            this.allocationField.setValue(toDouble(initial.getAllocationPct()));

            this.labelField.addValueChangeListener(event -> onChanged.run());
            this.corpusField.addValueChangeListener(event -> onChanged.run());
            this.growthField.addValueChangeListener(event -> onChanged.run());
            this.taxField.addValueChangeListener(event -> onChanged.run());
            this.stepUpField.addValueChangeListener(event -> onChanged.run());
            this.allocationField.addValueChangeListener(event -> onChanged.run());

            final Button removeButton = RowControls.removeButton(() -> onRemove.accept(this));

            setWidthFull();
            setAlignItems(Alignment.BASELINE);
            addClassName("investment-row");
            addClassName("form-row");
            add(this.labelField, this.corpusField, this.growthField,
                    this.taxField, this.stepUpField, this.allocationField, removeButton);
            expand(this.labelField, this.corpusField);
        }

        Investment snapshot() {
            final Investment investment = new Investment();
            investment.setLabel(this.labelField.getValue());
            investment.setCurrentCorpus(this.corpusField.getValue());
            investment.setGrowthRatePct(toBigDecimal(this.growthField.getValue()));
            investment.setWithdrawalTaxRatePct(toBigDecimal(this.taxField.getValue()));
            investment.setStepUpPct(toBigDecimal(this.stepUpField.getValue()));
            investment.setAllocationPct(toBigDecimal(this.allocationField.getValue()));
            return investment;
        }

        BigDecimal allocation() {
            return toBigDecimal(this.allocationField.getValue());
        }

        private static NumberField percentage(String label) {
            final NumberField field = new NumberField(label);
            field.setStep(0.1);
            field.setStepButtonsVisible(false);
            field.setValueChangeMode(ValueChangeMode.LAZY);
            final Span suffix = new Span("%");
            suffix.getStyle().setColor("var(--vaadin-secondary-text-color, #71717a)");
            field.setSuffixComponent(suffix);
            return field;
        }

        private static Double toDouble(BigDecimal value) {
            return value == null ? null : value.doubleValue();
        }

        private static BigDecimal toBigDecimal(Double value) {
            return value == null ? null : BigDecimal.valueOf(value);
        }
    }
}
