package io.binarycodes.calculators.it;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page.GetByRoleOptions;
import com.microsoft.playwright.Page.GetByTextOptions;
import com.microsoft.playwright.options.AriaRole;
import io.binarycodes.calculators.it.support.SpringPlaywrightIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.vaadin.addons.dramafinder.element.NumberFieldElement;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * End-to-end coverage that the debt planner's inputs drive its output: raising
 * the inflation rate re-deflates the total-interest card's today's-money line,
 * and switching the payoff strategy re-points the "vs other strategy" card.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DisplayName("Debt Planner — inputs drive the plan")
class DebtPlanIT extends SpringPlaywrightIT {

    @Override
    public String getView() {
        return "debt";
    }

    @Test
    @DisplayName("The total-interest card shows today's money and reacts to inflation")
    void totalInterestCard_showsTodaysMoney_andReactsToInflation() {
        final Locator totalInterest = summaryCard("Total Interest");
        assertThat(totalInterest).containsText("today's money");
        final String before = totalInterest.textContent();

        NumberFieldElement.getByLabel(page, "Inflation Rate").setValue("20");

        assertThat(totalInterest).not().hasText(before);
        assertThat(totalInterest).containsText("today's money");
    }

    @Test
    @DisplayName("Switching Avalanche to Snowball re-points the comparison card")
    void switchingStrategy_repointsTheComparisonCard() {
        // With Avalanche selected the comparison is against Snowball...
        assertThat(page.getByText("vs Snowball", new GetByTextOptions().setExact(false))).isVisible();

        // Click the radio-button component itself (the segmented toggle hides the
        // native input), scoped so the matching card heading — a div — is excluded.
        page.locator("vaadin-radio-button")
                .filter(new Locator.FilterOptions().setHasText("Snowball (smallest balance first)"))
                .click();

        // ...and after switching, it flips to compare against Avalanche.
        assertThat(page.getByText("vs Avalanche", new GetByTextOptions().setExact(false))).isVisible();
    }

    private Locator summaryCard(String title) {
        return page.getByRole(AriaRole.REGION, new GetByRoleOptions().setName(title));
    }
}
