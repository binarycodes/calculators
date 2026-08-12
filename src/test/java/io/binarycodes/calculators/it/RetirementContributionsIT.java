package io.binarycodes.calculators.it;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page.GetByRoleOptions;
import com.microsoft.playwright.options.AriaRole;
import io.binarycodes.calculators.it.support.SpringPlaywrightIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.vaadin.addons.dramafinder.element.NumberFieldElement;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * End-to-end coverage for the retirement contribution rows: editing a
 * contribution amount on the Investments tab drives the Signal recalculation
 * so the summary cards change.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DisplayName("Retirement form — contribution rows drive recalculation")
class RetirementContributionsIT extends SpringPlaywrightIT {

    @Override
    public String getView() {
        return "retirement";
    }

    @Test
    @DisplayName("Editing a contribution amount updates the corpus-at-retirement card")
    void editingContributionAmount_updatesCorpusAtRetirement() {
        final Locator corpusCard = summaryCard("Corpus at Retirement");
        final String before = corpusCard.textContent();

        selectFormTab("Investments");

        // The Investments tab's only money field is the seeded pre-retirement
        // contribution's amount; raising it must lift the corpus at retirement.
        final NumberFieldElement amount = new NumberFieldElement(
                page.locator("vaadin-custom-field")
                        .filter(new Locator.FilterOptions().setHasText("Amount"))
                        .locator("vaadin-number-field"));
        amount.setValue("500000");

        assertThat(corpusCard).not().hasText(before);
    }

    private void selectFormTab(String tabName) {
        page.getByRole(AriaRole.TAB, new GetByRoleOptions().setName(tabName)).first().click();
    }

    private Locator summaryCard(String title) {
        return page.getByRole(AriaRole.REGION, new GetByRoleOptions().setName(title));
    }
}
