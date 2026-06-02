package io.binarycodes.calculators.it;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.AriaRole;
import io.binarycodes.calculators.it.support.SpringPlaywrightIT;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.vaadin.addons.dramafinder.element.IntegerFieldElement;
import org.vaadin.addons.dramafinder.element.NumberFieldElement;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * End-to-end coverage for the retirement-calculator form: that the binder
 * tab fields drive the Signal-based recalculation pipeline, and that
 * cross-field validators surface invalid entries to the user.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class RetirementCalculatorFormIT extends SpringPlaywrightIT {

    @Override
    public String getView() {
        return "retirement";
    }

    @Test
    void defaultLoad_rendersAllSummaryCards() {
        for (final String title : new String[]{
                "Corpus at Retirement", "Annual Expenses at Retirement",
                "Corpus Lasts Until", "Final Corpus"}) {
            assertThat(summaryCard(title)).isVisible();
        }
    }

    @Test
    void editingInflationRate_updatesAnnualExpensesSummary() {
        final Locator annualExpenses = summaryCard("Annual Expenses at Retirement");
        final String before = annualExpenses.textContent();

        final NumberFieldElement inflation = NumberFieldElement.getByLabel(page, "Inflation Rate");
        inflation.assertVisible();
        inflation.setValue("12");

        assertThat(annualExpenses).not().hasText(before);
    }

    @Test
    void retireAgeBelowCurrentAge_isFlaggedInvalid() {
        final IntegerFieldElement currentAge = IntegerFieldElement.getByLabel(page, "Current Age");
        final IntegerFieldElement retireAge = IntegerFieldElement.getByLabel(page, "Retirement Age");

        currentAge.setValue("50");
        retireAge.setValue("40");

        retireAge.assertInvalid();
    }

    private Locator summaryCard(String title) {
        return page.getByRole(AriaRole.REGION,
                new com.microsoft.playwright.Page.GetByRoleOptions().setName(title));
    }
}
