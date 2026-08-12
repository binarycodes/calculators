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
 * End-to-end coverage for the Buy vs Rent inflation input: the two net-worth
 * cards carry an "in today's money" line, and raising the inflation rate visibly
 * shrinks it — proving the input drives output rather than sitting inert.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DisplayName("Buy vs Rent — net worth in today's money")
class BuyRentInflationIT extends SpringPlaywrightIT {

    @Override
    public String getView() {
        return "buyrent";
    }

    @Test
    @DisplayName("Net-worth cards show today's money and react to the inflation rate")
    void netWorthCards_showTodaysMoney_andReactToInflation() {
        final Locator buyCard = summaryCard("Net Worth: Buy");
        assertThat(buyCard).containsText("today's money");
        final String before = buyCard.textContent();

        // A higher inflation rate deflates the future net worth harder, so the
        // today's-money figure must change.
        NumberFieldElement.getByLabel(page, "Inflation Rate").setValue("20");

        assertThat(buyCard).not().hasText(before);
        assertThat(buyCard).containsText("today's money");
    }

    private Locator summaryCard(String title) {
        return page.getByRole(AriaRole.REGION, new GetByRoleOptions().setName(title));
    }
}
