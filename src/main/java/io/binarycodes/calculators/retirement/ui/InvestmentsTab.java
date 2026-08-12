package io.binarycodes.calculators.retirement.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.validator.BigDecimalRangeValidator;
import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ValueSignal;
import io.binarycodes.calculators.base.common.Frequency;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.base.i18n.Translations;
import io.binarycodes.calculators.base.ui.FormCard;
import io.binarycodes.calculators.base.ui.FrequencyField;
import io.binarycodes.calculators.base.ui.MoneyField;
import io.binarycodes.calculators.base.ui.RowControls;
import io.binarycodes.calculators.base.ui.TabIndicator;
import io.binarycodes.calculators.retirement.domain.Contribution;
import io.binarycodes.calculators.retirement.domain.RetirementInputs;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static io.binarycodes.calculators.retirement.ui.FormFields.bindPercentage;
import static io.binarycodes.calculators.retirement.ui.FormFields.buildSectionCard;
import static io.binarycodes.calculators.retirement.ui.FormFields.percentageField;
import static io.binarycodes.calculators.retirement.ui.FormFields.withPercentageSuffix;

/**
 * The "Investments" tab: the existing-corpus growth/tax card, plus two lists of
 * contribution streams — one funded during the working years, one during
 * retirement. Each stream carries its own amount, frequency, growth rate,
 * yearly step-up, and tax rate, and is exposed as a {@link Signal} so the parent
 * form can fold it into the overall inputs.
 */
class InvestmentsTab extends VerticalLayout implements TabIndicator.Source {

    private final UserPreferences prefs;

    private final NumberField corpusReturnsPrePct = percentageField(Translations.get("field.beforeRetirement"));
    private final NumberField corpusReturnsPostPct = percentageField(Translations.get("field.afterRetirement"));
    private final NumberField corpusTaxRatePct = percentageField(Translations.get("field.taxRate"));

    private final ContributionSection preSection = new ContributionSection();
    private final ContributionSection postSection = new ContributionSection();

    private final List<Signal<?>> fieldSignals = new ArrayList<>();

    InvestmentsTab(Binder<RetirementInputs> binder, UserPreferences prefs) {
        this.prefs = prefs;

        setPadding(false);
        setSpacing(true);
        add(
                buildSectionCard(Translations.get("section.retirement.existingCorpusReturns"),
                        withPercentageSuffix(this.corpusReturnsPrePct),
                        withPercentageSuffix(this.corpusReturnsPostPct),
                        withPercentageSuffix(this.corpusTaxRatePct)),
                this.preSection.buildCard(Translations.get("section.retirement.preContributions"),
                        Translations.get("retirement.contrib.preIntro")),
                this.postSection.buildCard(Translations.get("section.retirement.postContributions"),
                        Translations.get("retirement.contrib.postIntro")));

        configureBindings(binder);
    }

    Stream<Signal<?>> fieldSignals() {
        return this.fieldSignals.stream();
    }

    Signal<List<Contribution>> preContributionsSignal() {
        return this.preSection.signal.asReadonly();
    }

    Signal<List<Contribution>> postContributionsSignal() {
        return this.postSection.signal.asReadonly();
    }

    List<Contribution> getPreContributions() {
        return this.preSection.snapshot();
    }

    List<Contribution> getPostContributions() {
        return this.postSection.snapshot();
    }

    void setPreContributions(List<Contribution> contributions) {
        this.preSection.set(contributions);
    }

    void setPostContributions(List<Contribution> contributions) {
        this.postSection.set(contributions);
    }

    /** Every added row must carry a positive amount — a blank/zero row is invalid. */
    boolean isValid() {
        return this.preSection.isValid() && this.postSection.isValid();
    }

    private void configureBindings(Binder<RetirementInputs> binder) {
        this.fieldSignals.add(bindPercentage(binder, this.corpusReturnsPrePct,
                RetirementInputs::getGrowthPrePct, RetirementInputs::setGrowthPrePct).valueSignal());
        this.fieldSignals.add(bindPercentage(binder, this.corpusReturnsPostPct,
                RetirementInputs::getGrowthPostPct, RetirementInputs::setGrowthPostPct).valueSignal());
        this.fieldSignals.add(bindPercentage(binder, this.corpusTaxRatePct,
                RetirementInputs::getCorpusTaxRatePct, RetirementInputs::setCorpusTaxRatePct).valueSignal());
    }

    /** One Pre/Post contribution list: rows container + authoritative list + published snapshot. */
    private final class ContributionSection {
        private final VerticalLayout rowsContainer = new VerticalLayout();
        private final List<ContributionRow> rows = new ArrayList<>();
        private final ValueSignal<List<Contribution>> signal = new ValueSignal<>(List.of());

        ContributionSection() {
            this.rowsContainer.setPadding(false);
            this.rowsContainer.setSpacing(true);
            this.rowsContainer.setWidthFull();
        }

        Component buildCard(String title, String introText) {
            final Span intro = new Span(introText);
            intro.getStyle().setColor("var(--vaadin-secondary-text-color, #71717a)");

            final Button addButton = new Button(Translations.get("retirement.contrib.add"),
                    VaadinIcon.PLUS.create(), event -> add(new Contribution()));
            addButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

            final var inner = new VerticalLayout(intro, this.rowsContainer, addButton);
            inner.setPadding(false);
            inner.setSpacing(true);

            final FormCard card = new FormCard(title);
            card.setWidthFull();
            card.add(inner);
            return card;
        }

        private void add(Contribution initial) {
            final ContributionRow row = new ContributionRow(InvestmentsTab.this.prefs, initial,
                    this::remove, this::publish);
            this.rows.add(row);
            this.rowsContainer.add(row);
            publish();
        }

        private void remove(ContributionRow row) {
            if (this.rows.remove(row)) {
                this.rowsContainer.remove(row);
                publish();
            }
        }

        private void publish() {
            this.signal.set(snapshot());
        }

        private List<Contribution> snapshot() {
            final List<Contribution> out = new ArrayList<>();
            for (final ContributionRow row : this.rows) {
                out.add(row.snapshot());
            }
            return out;
        }

        private void set(List<Contribution> contributions) {
            this.rowsContainer.removeAll();
            this.rows.clear();
            if (contributions != null) {
                for (final Contribution contribution : contributions) {
                    add(contribution);
                }
            }
            publish();
        }

        private boolean isValid() {
            return this.rows.stream().allMatch(ContributionRow::isValid);
        }
    }

    private static final class ContributionRow extends HorizontalLayout {
        private final MoneyField amountField;
        private final FrequencyField frequencyField = new FrequencyField(Translations.get("field.frequency"));
        private final NumberField growthField = withPercentageSuffix(percentageField(Translations.get("field.growthPercentage")));
        private final NumberField stepUpField = withPercentageSuffix(percentageField(Translations.get("field.stepUpYearlyPct")));
        private final NumberField taxField = withPercentageSuffix(percentageField(Translations.get("field.taxRate")));
        private final Binder<Contribution> binder = new Binder<>(Contribution.class);

        ContributionRow(UserPreferences prefs, Contribution initial,
                        Consumer<ContributionRow> onRemove, Runnable onChanged) {
            this.amountField = new MoneyField(Translations.get("field.amount"), prefs);

            this.frequencyField.setValue(initial.getFrequency() == null ? Frequency.MONTHLY : initial.getFrequency());
            this.amountField.setValue(initial.getAmount());
            this.growthField.setValue(toDouble(initial.getGrowthPct()));
            this.stepUpField.setValue(toDouble(initial.getStepUpPct()));
            this.taxField.setValue(toDouble(initial.getTaxRatePct()));

            this.amountField.addValueChangeListener(event -> onChanged.run());
            this.frequencyField.addValueChangeListener(event -> onChanged.run());
            this.growthField.addValueChangeListener(event -> onChanged.run());
            this.stepUpField.addValueChangeListener(event -> onChanged.run());
            this.taxField.addValueChangeListener(event -> onChanged.run());

            final Button removeButton = RowControls.removeButton(() -> onRemove.accept(this));

            setWidthFull();
            setAlignItems(Alignment.BASELINE);
            addClassName("form-row");
            add(this.amountField, this.frequencyField, this.growthField, this.stepUpField, this.taxField, removeButton);
            expand(this.amountField);

            this.binder.forField(this.amountField)
                    .asRequired(Translations.get("validation.required"))
                    .withValidator(new BigDecimalRangeValidator(Translations.get("validation.positive"),
                            new BigDecimal("0.01"), null))
                    .bind(Contribution::getAmount, Contribution::setAmount);
            this.binder.validate();
            this.binder.addStatusChangeListener(event -> onChanged.run());
        }

        boolean isValid() {
            final BigDecimal amount = this.amountField.getValue();
            return amount != null && amount.signum() > 0;
        }

        Contribution snapshot() {
            final Contribution out = new Contribution();
            out.setAmount(this.amountField.getValue());
            out.setFrequency(this.frequencyField.getValue());
            out.setGrowthPct(toBigDecimal(this.growthField.getValue()));
            out.setStepUpPct(toBigDecimal(this.stepUpField.getValue()));
            out.setTaxRatePct(toBigDecimal(this.taxField.getValue()));
            return out;
        }

        private static Double toDouble(BigDecimal value) {
            return value == null ? null : value.doubleValue();
        }

        private static BigDecimal toBigDecimal(Double value) {
            return value == null ? null : BigDecimal.valueOf(value);
        }
    }
}
