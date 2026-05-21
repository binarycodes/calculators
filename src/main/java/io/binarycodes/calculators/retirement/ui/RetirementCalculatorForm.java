package io.binarycodes.calculators.retirement.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.card.Card;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.Result;
import com.vaadin.flow.data.converter.Converter;
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
 * Contributions) and binds them to a {@link RetirementInputs} record via a
 * single {@link Binder}.</p>
 *
 * <p>The component itself extends {@link Card} so it composes cleanly with the
 * rest of the page, but it is styled in {@code retirement-view.css} with no
 * border / padding / background so it is visually transparent — the inner
 * section Cards remain the visible chrome, matching the previous layout
 * exactly.</p>
 *
 * <h3>API</h3>
 * <ul>
 *   <li>{@link #setInputs(RetirementInputs)} — populate all fields from a record.</li>
 *   <li>{@link #getInputs()} — read field values back as a new record.</li>
 *   <li>{@link #addInputChangeListener(Runnable)} — fires after any field
 *       changes, except while {@link #setInputs} is running.</li>
 * </ul>
 */
public class RetirementCalculatorForm extends VerticalLayout {

    private static final int DEFAULT_CURRENT_AGE = 35;
    private static final int DEFAULT_RETIRE_AGE = 60;
    private static final int DEFAULT_LIFE_EXP = 90;

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

    /**
     * Populate every field from {@code inputs}. Does not fire the change listener.
     */
    public void setInputs(RetirementInputs inputs) {
        this.suppressChangeEvents = true;
        try {
            this.binder.readRecord(inputs);
        } finally {
            this.suppressChangeEvents = false;
        }
    }

    /**
     * Build a new {@link RetirementInputs} from the current field values.
     */
    public RetirementInputs getInputs() {
        return new RetirementInputs(
                intOrDefault(this.currentAge.getValue(), DEFAULT_CURRENT_AGE),
                intOrDefault(this.retireAge.getValue(), DEFAULT_RETIRE_AGE),
                intOrDefault(this.lifeExp.getValue(), DEFAULT_LIFE_EXP),
                bigDecimalOrZero(this.corpus.getValue()),
                bigDecimalOrZero(this.monthlyExpenses.getValue()),
                bigDecimalOf(this.inflationPct.getValue()),
                bigDecimalOf(this.corpusReturnsPrePct.getValue()),
                bigDecimalOf(this.corpusReturnsPostPct.getValue()),
                bigDecimalOrZero(this.monthlyInvestmentPre.getValue()),
                bigDecimalOf(this.sipReturnsPrePct.getValue()),
                bigDecimalOrZero(this.monthlyInvestmentPost.getValue()),
                bigDecimalOf(this.sipReturnsPostPct.getValue())
        );
    }

    /**
     * Fired after any bound field changes (not during {@link #setInputs}).
     */
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
        // Read-only bindings; we re-build the immutable RetirementInputs in getInputs().
        this.binder.forField(this.currentAge).bind(RetirementInputs::currentAge, null);
        this.binder.forField(this.retireAge).bind(RetirementInputs::retireAge, null);
        this.binder.forField(this.lifeExp).bind(RetirementInputs::lifeExp, null);

        this.binder.forField(this.corpus).bind(RetirementInputs::corpus, null);
        this.binder.forField(this.monthlyExpenses).bind(RetirementInputs::monthlyExpenses, null);
        this.binder.forField(this.monthlyInvestmentPre).bind(RetirementInputs::monthlyInvPre, null);
        this.binder.forField(this.monthlyInvestmentPost).bind(RetirementInputs::monthlyInvPost, null);

        this.binder.forField(this.inflationPct)
                .withConverter(doubleToBigDecimalConverter())
                .bind(RetirementInputs::inflationPct, null);

        this.binder.forField(this.corpusReturnsPrePct)
                .withConverter(doubleToBigDecimalConverter())
                .bind(RetirementInputs::growthPrePct, null);

        this.binder.forField(this.corpusReturnsPostPct)
                .withConverter(doubleToBigDecimalConverter())
                .bind(RetirementInputs::growthPostPct, null);

        this.binder.forField(this.sipReturnsPrePct)
                .withConverter(doubleToBigDecimalConverter())
                .bind(RetirementInputs::sipGrowthPrePct, null);

        this.binder.forField(this.sipReturnsPostPct)
                .withConverter(doubleToBigDecimalConverter())
                .bind(RetirementInputs::sipGrowthPostPct, null);
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
        return field;
    }

    private static NumberField percentageField(String label) {
        final var field = new NumberField(label);
        field.setMin(0);
        field.setMax(100);
        field.setStep(0.1);
        field.setStepButtonsVisible(false);
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
                value -> Result.ok(value == null ? BigDecimal.ZERO : BigDecimal.valueOf(value)),
                value -> value == null ? 0.0d : value.doubleValue()
        );
    }

    private static int intOrDefault(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private static BigDecimal bigDecimalOf(Double value) {
        return value == null ? BigDecimal.ZERO : BigDecimal.valueOf(value);
    }

    private static BigDecimal bigDecimalOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
