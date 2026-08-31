package io.binarycodes.calculators.it;

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
 * Covers {@code app.calculators.prefill-defaults=false}: a first-time visitor
 * gets a blank form instead of the shipped sample scenario, and the Reset
 * action — the only control that would put the sample scenario back over the
 * visitor's own inputs — is gone with it.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = "app.calculators.prefill-defaults=false")
@DisplayName("Prefill disabled — calculators open blank")
class PrefillDefaultsDisabledIT extends SpringPlaywrightIT {

    @Override
    public String getView() {
        return "loan";
    }

    @Test
    @DisplayName("The loan form opens with no interest rate when nothing is persisted")
    void loanForm_opensBlank() {
        NumberFieldElement.getByLabel(page, "Interest Rate").assertValue("");
    }

    @Test
    @DisplayName("The Reset action is not offered, so nothing can restore the defaults")
    void reset_isNotOffered() {
        assertThat(page.getByRole(AriaRole.BUTTON, new GetByRoleOptions().setName("Reset")))
                .hasCount(0);
        // Exact, so the per-section "Clear this section" buttons don't also match.
        assertThat(page.getByRole(AriaRole.BUTTON, new GetByRoleOptions().setName("Clear").setExact(true)))
                .isVisible();
    }
}
