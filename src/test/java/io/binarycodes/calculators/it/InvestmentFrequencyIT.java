package io.binarycodes.calculators.it;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page.GetByRoleOptions;
import com.microsoft.playwright.options.AriaRole;
import io.binarycodes.calculators.it.support.SpringPlaywrightIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.vaadin.addons.dramafinder.element.SelectElement;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * End-to-end coverage for the shared contribution-frequency dropdown on the
 * investment form: all four options render, and picking a wider cadence drives
 * the Signal-based recalculation so the summary cards actually change.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DisplayName("Investment form — contribution frequency")
class InvestmentFrequencyIT extends SpringPlaywrightIT {

    @Override
    public String getView() {
        return "investment";
    }

    @Test
    @DisplayName("The dropdown offers Monthly, Quarterly, Half-Yearly and Yearly")
    void frequencyDropdown_offersAllFourOptions() {
        contributionFrequency().getLocator().locator("vaadin-select-value-button").click();

        for (final String option : new String[]{"Monthly", "Quarterly", "Half-Yearly", "Yearly"}) {
            // Exact match — "Yearly" would otherwise also match "Half-Yearly".
            assertThat(page.getByRole(AriaRole.OPTION,
                    new GetByRoleOptions().setName(option).setExact(true)))
                    .isVisible();
        }
    }

    @Test
    @DisplayName("Switching to Quarterly recomputes Total Invested")
    void switchingToQuarterly_recomputesTotalInvested() {
        final Locator totalInvested = summaryCard("Total Invested");
        final String before = totalInvested.textContent();

        // Default is Monthly; a quarterly cadence contributes a third as often,
        // so Total Invested must change.
        contributionFrequency().selectItem("Quarterly");

        assertThat(totalInvested).not().hasText(before);
    }

    private SelectElement contributionFrequency() {
        final Locator select = page.locator("vaadin-custom-field")
                .filter(new Locator.FilterOptions().setHasText("Contribution frequency"))
                .locator("vaadin-select");
        return new SelectElement(select);
    }

    private Locator summaryCard(String title) {
        return page.getByRole(AriaRole.REGION, new GetByRoleOptions().setName(title));
    }
}
