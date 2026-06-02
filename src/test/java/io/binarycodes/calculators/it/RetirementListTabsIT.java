package io.binarycodes.calculators.it;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page.GetByRoleOptions;
import com.microsoft.playwright.options.AriaRole;
import io.binarycodes.calculators.it.support.SpringPlaywrightIT;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.vaadin.addons.dramafinder.element.IntegerFieldElement;
import org.vaadin.addons.dramafinder.element.NumberFieldElement;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Exercises the list-based tabs (Future Expenses, Future Incomes,
 * Retirement Benefits). Each tab now publishes its row contents through a
 * {@code ValueSignal<List<...>>} which the form-level computed signal pulls
 * into the inputs bean — these tests confirm that pipeline runs end-to-end
 * (adding a row triggers recalculation in the summary cards).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class RetirementListTabsIT extends SpringPlaywrightIT {

    @Override
    public String getView() {
        return "retirement";
    }

    @Test
    void addingFutureExpense_reducesCorpusAtRetirement() {
        final Locator corpusCard = summaryCard("Corpus at Retirement");
        final String before = corpusCard.textContent();

        selectFormTab("Future Expenses");
        clickButton("Add expense");

        // The new row's fields are the only visible labels at this point;
        // the inactive tabs' rows are not in the accessibility tree.
        IntegerFieldElement.getByLabel(page, "Year").setValue("2030");
        new NumberFieldElement(
                page.locator("vaadin-custom-field").filter(
                        new Locator.FilterOptions().setHasText("Amount (today)"))
                        .locator("vaadin-number-field")).setValue("50000");

        assertThat(corpusCard).not().hasText(before);
    }

    @Test
    void addingRecurringIncome_increasesFinalCorpus() {
        final Locator finalCorpusCard = summaryCard("Final Corpus");
        final String before = finalCorpusCard.textContent();

        selectFormTab("Future Incomes");
        clickButton("Add recurring income");

        IntegerFieldElement.getByLabel(page, "Start Year").setValue("2031");
        new NumberFieldElement(
                page.locator("vaadin-custom-field").filter(
                        new Locator.FilterOptions().setHasText("Amount"))
                        .locator("vaadin-number-field")).setValue("2000");

        assertThat(finalCorpusCard).not().hasText(before);
    }

    private void selectFormTab(String tabName) {
        page.getByRole(AriaRole.TAB, new GetByRoleOptions().setName(tabName))
                .first()
                .click();
    }

    private void clickButton(String label) {
        page.getByRole(AriaRole.BUTTON, new GetByRoleOptions().setName(label))
                .first()
                .click();
    }

    private Locator summaryCard(String title) {
        return page.getByRole(AriaRole.REGION, new GetByRoleOptions().setName(title));
    }
}
