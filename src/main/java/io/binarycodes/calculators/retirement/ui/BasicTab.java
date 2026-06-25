package io.binarycodes.calculators.retirement.ui;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.validator.BigDecimalRangeValidator;
import com.vaadin.flow.data.validator.IntegerRangeValidator;
import com.vaadin.flow.signals.Signal;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.base.i18n.Translations;
import io.binarycodes.calculators.base.ui.MoneyField;
import io.binarycodes.calculators.base.ui.TabIndicator;
import io.binarycodes.calculators.retirement.domain.RetirementInputs;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static io.binarycodes.calculators.retirement.ui.FormFields.ageField;
import static io.binarycodes.calculators.retirement.ui.FormFields.bindPercentage;
import static io.binarycodes.calculators.retirement.ui.FormFields.buildSectionCard;
import static io.binarycodes.calculators.retirement.ui.FormFields.percentageField;
import static io.binarycodes.calculators.retirement.ui.FormFields.withPercentageSuffix;

/**
 * The "Basic" tab: timeline (ages) and current-finances inputs. Owns the
 * cross-age validators (each age must exceed the previous one) since all
 * three age fields live here.
 */
class BasicTab extends VerticalLayout implements TabIndicator.Source {

    private final IntegerField currentAge = ageField(Translations.get("field.currentAge"));
    private final IntegerField retireAge = ageField(Translations.get("field.retirementAge"));
    private final IntegerField lifeExp = ageField(Translations.get("field.lifeExpectancy"));
    private final MoneyField corpus;
    private final MoneyField monthlyExpenses;
    private final NumberField inflationPct = percentageField(Translations.get("field.inflationRate"));

    private final List<Signal<?>> fieldSignals = new ArrayList<>();

    BasicTab(Binder<RetirementInputs> binder, UserPreferences prefs) {
        this.corpus = new MoneyField(Translations.get("field.currentCorpus"), prefs);
        this.monthlyExpenses = new MoneyField(Translations.get("field.monthlyExpenses"), prefs);

        setPadding(false);
        setSpacing(true);
        add(
                buildSectionCard(Translations.get("section.retirement.timeline"),
                        this.currentAge, this.retireAge, this.lifeExp),
                buildSectionCard(Translations.get("section.retirement.currentFinances"),
                        this.corpus, this.monthlyExpenses,
                        withPercentageSuffix(this.inflationPct)));

        configureBindings(binder);
    }

    Stream<Signal<?>> fieldSignals() {
        return this.fieldSignals.stream();
    }

    private void configureBindings(Binder<RetirementInputs> binder) {
        this.fieldSignals.add(binder.forField(this.currentAge)
                .asRequired(Translations.get("validation.required"))
                .withValidator(new IntegerRangeValidator(Translations.get("validation.between", 1, 120), 1, 120))
                .bind(RetirementInputs::getCurrentAge, RetirementInputs::setCurrentAge)
                .valueSignal());

        this.fieldSignals.add(binder.forField(this.retireAge)
                .asRequired(Translations.get("validation.required"))
                .withValidator(new IntegerRangeValidator(Translations.get("validation.between", 1, 120), 1, 120))
                .withValidator(age -> isGreaterThan(age, this.currentAge.getValue()),
                        Translations.get("validation.greaterThanCurrentAge"))
                .bind(RetirementInputs::getRetireAge, RetirementInputs::setRetireAge)
                .valueSignal());

        this.fieldSignals.add(binder.forField(this.lifeExp)
                .asRequired(Translations.get("validation.required"))
                .withValidator(new IntegerRangeValidator(Translations.get("validation.between", 1, 120), 1, 120))
                .withValidator(age -> isGreaterThan(age, this.retireAge.getValue()),
                        Translations.get("validation.greaterThanRetirementAge"))
                .bind(RetirementInputs::getLifeExp, RetirementInputs::setLifeExp)
                .valueSignal());

        // When an earlier age changes, the dependent age's validator needs to
        // re-run so its error message updates.
        this.currentAge.addValueChangeListener(event -> binder.validate());
        this.retireAge.addValueChangeListener(event -> binder.validate());

        this.fieldSignals.add(binder.forField(this.corpus)
                .asRequired(Translations.get("validation.required"))
                .withValidator(new BigDecimalRangeValidator(Translations.get("validation.nonNegative"),
                        BigDecimal.ZERO, null))
                .bind(RetirementInputs::getCorpus, RetirementInputs::setCorpus)
                .valueSignal());

        this.fieldSignals.add(binder.forField(this.monthlyExpenses)
                .asRequired(Translations.get("validation.required"))
                .withValidator(new BigDecimalRangeValidator(Translations.get("validation.positive"),
                        BigDecimal.ZERO, null))
                .bind(RetirementInputs::getMonthlyExpenses, RetirementInputs::setMonthlyExpenses)
                .valueSignal());

        this.fieldSignals.add(bindPercentage(binder, this.inflationPct,
                RetirementInputs::getInflationPct, RetirementInputs::setInflationPct)
                .valueSignal());
    }

    private static boolean isGreaterThan(Integer value, Integer floor) {
        return value == null || floor == null || value > floor;
    }
}
