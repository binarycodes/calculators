package io.binarycodes.calculators.retirement.ui;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.validator.BigDecimalRangeValidator;
import com.vaadin.flow.data.validator.IntegerRangeValidator;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.base.ui.MoneyField;
import io.binarycodes.calculators.retirement.domain.RetirementInputs;

import java.math.BigDecimal;

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
class BasicTab extends VerticalLayout {

    private final IntegerField currentAge = ageField("Current Age");
    private final IntegerField retireAge = ageField("Retirement Age");
    private final IntegerField lifeExp = ageField("Life Expectancy");
    private final MoneyField corpus;
    private final MoneyField monthlyExpenses;
    private final NumberField inflationPct = percentageField("Inflation Rate");

    BasicTab(Binder<RetirementInputs> binder, UserPreferences prefs) {
        this.corpus = new MoneyField("Current Corpus", prefs);
        this.monthlyExpenses = new MoneyField("Monthly Expenses (today)", prefs);

        setPadding(false);
        setSpacing(true);
        add(
                buildSectionCard("Timeline",
                        this.currentAge, this.retireAge, this.lifeExp),
                buildSectionCard("Current Finances",
                        this.corpus, this.monthlyExpenses,
                        withPercentageSuffix(this.inflationPct)));

        configureBindings(binder);
    }

    private void configureBindings(Binder<RetirementInputs> binder) {
        binder.forField(this.currentAge)
                .asRequired("Required")
                .withValidator(new IntegerRangeValidator("Must be between 1 and 120", 1, 120))
                .bind(RetirementInputs::getCurrentAge, RetirementInputs::setCurrentAge);

        binder.forField(this.retireAge)
                .asRequired("Required")
                .withValidator(new IntegerRangeValidator("Must be between 1 and 120", 1, 120))
                .withValidator(age -> isGreaterThan(age, this.currentAge.getValue()),
                        "Must be greater than current age")
                .bind(RetirementInputs::getRetireAge, RetirementInputs::setRetireAge);

        binder.forField(this.lifeExp)
                .asRequired("Required")
                .withValidator(new IntegerRangeValidator("Must be between 1 and 120", 1, 120))
                .withValidator(age -> isGreaterThan(age, this.retireAge.getValue()),
                        "Must be greater than retirement age")
                .bind(RetirementInputs::getLifeExp, RetirementInputs::setLifeExp);

        // When an earlier age changes, the dependent age's validator needs to
        // re-run so its error message updates.
        this.currentAge.addValueChangeListener(e -> binder.validate());
        this.retireAge.addValueChangeListener(e -> binder.validate());

        binder.forField(this.corpus)
                .asRequired("Required")
                .withValidator(new BigDecimalRangeValidator("Must be non-negative",
                        BigDecimal.ZERO, null))
                .bind(RetirementInputs::getCorpus, RetirementInputs::setCorpus);

        binder.forField(this.monthlyExpenses)
                .asRequired("Required")
                .withValidator(new BigDecimalRangeValidator("Must be positive",
                        BigDecimal.ZERO, null))
                .bind(RetirementInputs::getMonthlyExpenses, RetirementInputs::setMonthlyExpenses);

        bindPercentage(binder, this.inflationPct,
                RetirementInputs::getInflationPct, RetirementInputs::setInflationPct);
    }

    private static boolean isGreaterThan(Integer value, Integer floor) {
        return value == null || floor == null || value > floor;
    }
}
