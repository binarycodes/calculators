package io.binarycodes.calculators.retirement.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.card.Card;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.BinderValidationStatus;
import com.vaadin.flow.data.binder.Result;
import com.vaadin.flow.data.binder.Setter;
import com.vaadin.flow.data.converter.Converter;
import com.vaadin.flow.data.validator.BigDecimalRangeValidator;
import com.vaadin.flow.data.validator.DoubleRangeValidator;
import com.vaadin.flow.data.validator.IntegerRangeValidator;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.function.ValueProvider;
import com.vaadin.flow.shared.Registration;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.base.ui.MoneyField;
import io.binarycodes.calculators.retirement.domain.RetirementInputs;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The retirement-calculator input form.
 *
 * <p>Renders all twelve input fields organised into four labelled sub-sections
 * (Timeline / Current Finances / Existing Corpus Returns / Monthly SIP
 * Contributions) and binds them to a {@link RetirementInputs} bean via a
 * single {@link Binder} with field-level validation.</p>
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

    private final IntegerField currentAge = ageField("Current Age");
    private final IntegerField retireAge = ageField("Retirement Age");
    private final IntegerField lifeExp = ageField("Life Expectancy");

    private final MoneyField corpus;
    private final MoneyField monthlyExpenses;
    private final MoneyField monthlyInvestmentPre;
    private final MoneyField monthlyInvestmentPost;

    private final NumberField inflationPct = percentageField("Inflation Rate");
    private final NumberField corpusReturnsPrePct = percentageField("Before Retirement");
    private final NumberField corpusReturnsPostPct = percentageField("After Retirement");
    private final NumberField sipReturnsPrePct = percentageField("Growth Percentage");
    private final NumberField sipReturnsPostPct = percentageField("Growth Percentage");

    private final Binder<RetirementInputs> binder = new Binder<>(RetirementInputs.class);
    private final List<Runnable> changeListeners = new ArrayList<>();
    private boolean suppressChangeEvents;

    public RetirementCalculatorForm(UserPreferences prefs) {
        addClassName("retirement-form");
        setPadding(false);
        setSpacing(true);
        setWidthFull();

        this.corpus = new MoneyField("Current Corpus", prefs);
        this.monthlyExpenses = new MoneyField("Monthly Expenses (today)", prefs);
        this.monthlyInvestmentPre = new MoneyField("Monthly SIP", prefs);
        this.monthlyInvestmentPost = new MoneyField("Monthly SIP", prefs);

        buildSections();
        configureBindings();

        this.binder.addValueChangeListener(event -> notifyChangeListeners());
    }

    public void setInputs(RetirementInputs inputs) {
        this.suppressChangeEvents = true;
        try {
            this.binder.readBean(inputs);
        } finally {
            this.suppressChangeEvents = false;
        }
    }

    public RetirementInputs getInputs() {
        final var target = new RetirementInputs();
        this.binder.writeBeanAsDraft(target);
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

    private void buildSections() {
        final var timelineSection = buildSectionCard("Timeline",
                this.currentAge, this.retireAge, this.lifeExp);
        final var currentFinancesSection = buildSectionCard("Current Finances",
                this.corpus, this.monthlyExpenses, withPercentageSuffix(this.inflationPct));
        final var corpusReturnsSection = buildSectionCard("Existing Corpus Returns",
                withPercentageSuffix(this.corpusReturnsPrePct),
                withPercentageSuffix(this.corpusReturnsPostPct));
        final var sipContributionsSection = buildNestedSectionCard("Monthly SIP Contributions",
                buildSectionCard("Before Retirement",
                        this.monthlyInvestmentPre, withPercentageSuffix(this.sipReturnsPrePct)),
                buildSectionCard("After Retirement",
                        this.monthlyInvestmentPost, withPercentageSuffix(this.sipReturnsPostPct))
        );

        add(timelineSection, currentFinancesSection, corpusReturnsSection, sipContributionsSection);
    }

    private void configureBindings() {
        this.binder.forField(this.currentAge)
                .asRequired("Required")
                .withValidator(new IntegerRangeValidator("Must be between 1 and 120", 1, 120))
                .bind(RetirementInputs::getCurrentAge, RetirementInputs::setCurrentAge);

        this.binder.forField(this.retireAge)
                .asRequired("Required")
                .withValidator(new IntegerRangeValidator("Must be between 1 and 120", 1, 120))
                .withValidator(age -> isGreaterThan(age, this.currentAge.getValue()),
                        "Must be greater than current age")
                .bind(RetirementInputs::getRetireAge, RetirementInputs::setRetireAge);

        this.binder.forField(this.lifeExp)
                .asRequired("Required")
                .withValidator(new IntegerRangeValidator("Must be between 1 and 120", 1, 120))
                .withValidator(age -> isGreaterThan(age, this.retireAge.getValue()),
                        "Must be greater than retirement age")
                .bind(RetirementInputs::getLifeExp, RetirementInputs::setLifeExp);

        // When an earlier age changes, the dependent age's validator needs to
        // re-run so its error message updates.
        this.currentAge.addValueChangeListener(e -> this.binder.validate());
        this.retireAge.addValueChangeListener(e -> this.binder.validate());

        this.binder.forField(this.corpus)
                .asRequired("Required")
                .withValidator(new BigDecimalRangeValidator("Must be non-negative",
                        BigDecimal.ZERO, null))
                .bind(RetirementInputs::getCorpus, RetirementInputs::setCorpus);

        this.binder.forField(this.monthlyExpenses)
                .asRequired("Required")
                .withValidator(new BigDecimalRangeValidator("Must be positive",
                        BigDecimal.ZERO, null))
                .bind(RetirementInputs::getMonthlyExpenses, RetirementInputs::setMonthlyExpenses);

        this.binder.forField(this.monthlyInvestmentPre)
                .asRequired("Required")
                .withValidator(new BigDecimalRangeValidator("Must be non-negative",
                        BigDecimal.ZERO, null))
                .bind(RetirementInputs::getMonthlyInvPre, RetirementInputs::setMonthlyInvPre);

        this.binder.forField(this.monthlyInvestmentPost)
                .asRequired("Required")
                .withValidator(new BigDecimalRangeValidator("Must be non-negative",
                        BigDecimal.ZERO, null))
                .bind(RetirementInputs::getMonthlyInvPost, RetirementInputs::setMonthlyInvPost);

        bindPercentage(this.inflationPct,
                RetirementInputs::getInflationPct, RetirementInputs::setInflationPct);
        bindPercentage(this.corpusReturnsPrePct,
                RetirementInputs::getGrowthPrePct, RetirementInputs::setGrowthPrePct);
        bindPercentage(this.corpusReturnsPostPct,
                RetirementInputs::getGrowthPostPct, RetirementInputs::setGrowthPostPct);
        bindPercentage(this.sipReturnsPrePct,
                RetirementInputs::getSipGrowthPrePct, RetirementInputs::setSipGrowthPrePct);
        bindPercentage(this.sipReturnsPostPct,
                RetirementInputs::getSipGrowthPostPct, RetirementInputs::setSipGrowthPostPct);
    }

    private void bindPercentage(NumberField field,
                                ValueProvider<RetirementInputs, BigDecimal> getter,
                                Setter<RetirementInputs, BigDecimal> setter) {
        this.binder.forField(field)
                .asRequired("Required")
                .withValidator(new DoubleRangeValidator("Must be between 0 and 100", 0d, 100d))
                .withConverter(doubleToBigDecimalConverter())
                .bind(getter, setter);
    }

    private static boolean isGreaterThan(Integer value, Integer floor) {
        return value == null || floor == null || value > floor;
    }

    private void notifyChangeListeners() {
        if (this.suppressChangeEvents) {
            return;
        }
        this.changeListeners.forEach(Runnable::run);
    }

    private static IntegerField ageField(String label) {
        final var suffix = new Span("yrs");
        suffix.getStyle().setColor("var(--vaadin-secondary-text-color, #71717a)");

        final var field = new IntegerField(label);
        field.setMin(1);
        field.setMax(120);
        field.setStepButtonsVisible(false);
        field.setSuffixComponent(suffix);
        field.setValueChangeMode(ValueChangeMode.LAZY);
        return field;
    }

    private static NumberField percentageField(String label) {
        final var field = new NumberField(label);
        field.setMin(0);
        field.setMax(100);
        field.setStep(0.1);
        field.setStepButtonsVisible(false);
        field.setValueChangeMode(ValueChangeMode.LAZY);
        return field;
    }

    private static NumberField withPercentageSuffix(NumberField field) {
        if (field.getSuffixComponent() == null) {
            final var suffix = new Span("%");
            suffix.getStyle().setColor("var(--vaadin-secondary-text-color, #71717a)");
            field.setSuffixComponent(suffix);
        }
        return field;
    }

    private static Component buildSectionCard(String title, Component... fields) {
        final var formLayout = new FormLayout();
        formLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("36em", 2),
                new FormLayout.ResponsiveStep("64em", 3),
                new FormLayout.ResponsiveStep("90em", 4));
        formLayout.add(fields);

        final var card = new Card();
        card.setTitle(title);
        card.add(formLayout);
        card.setWidthFull();
        card.addClassNames("form-section");
        return card;
    }

    private static Component buildNestedSectionCard(String title, Component... nestedSections) {
        final var wrapper = new VerticalLayout(nestedSections);
        wrapper.setPadding(false);
        wrapper.addClassNames("form-section-container");

        Arrays.stream(nestedSections).forEach(section -> section.addClassNames("inner-form-section"));

        final var card = new Card();
        card.setTitle(title);
        card.setWidthFull();
        card.add(wrapper);
        return card;
    }

    private static Converter<Double, BigDecimal> doubleToBigDecimalConverter() {
        return Converter.from(
                value -> Result.ok(value == null ? null : BigDecimal.valueOf(value)),
                value -> value == null ? null : value.doubleValue()
        );
    }
}
