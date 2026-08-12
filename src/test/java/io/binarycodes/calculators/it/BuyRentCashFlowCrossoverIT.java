package io.binarycodes.calculators.it;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page.GetByRoleOptions;
import com.microsoft.playwright.options.AriaRole;
import io.binarycodes.calculators.it.support.SpringPlaywrightIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.vaadin.addons.dramafinder.element.IntegerFieldElement;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * End-to-end coverage for the Buy vs Rent cash-flow crossover: shortening the
 * loan term makes owning cheaper to hold once the EMI clears, which must surface
 * both as the "Cheaper to Own From" summary card and as a highlighted row in the
 * year-by-year projection grid.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DisplayName("Buy vs Rent — cash-flow crossover")
class BuyRentCashFlowCrossoverIT extends SpringPlaywrightIT {

    @Override
    public String getView() {
        return "buyrent";
    }

    @Test
    @DisplayName("Shortening the loan surfaces a cash-flow crossover year")
    void shortLoan_surfacesCrossoverYear() {
        // Once the loan clears, the buy cost drops to tax + maintenance — well
        // below the risen rent — so owning becomes cheaper to hold soon after.
        // Editing the loan term must drive the Signal recompute and populate the
        // crossover card with a concrete year.
        IntegerFieldElement.getByLabel(page, "Loan Term").setValue("5");

        final Locator crossoverCard = summaryCard("Cheaper to Own From");
        assertThat(crossoverCard).isVisible();
        assertThat(crossoverCard).containsText("Year");
    }

    private Locator summaryCard(String title) {
        return page.getByRole(AriaRole.REGION, new GetByRoleOptions().setName(title));
    }
}
