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
 * The default side of {@code app.calculators.prefill-defaults}: pins the
 * out-of-the-box behaviour so the conditional Reset button can't be hidden for
 * everyone by accident.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DisplayName("Prefill enabled — the shipped default")
class PrefillDefaultsEnabledIT extends SpringPlaywrightIT {

    @Override
    public String getView() {
        return "loan";
    }

    @Test
    @DisplayName("The loan form opens on the sample scenario, with Reset offered")
    void loanForm_opensPrefilled_andOffersReset() {
        NumberFieldElement.getByLabel(page, "Interest Rate").assertValue("8.5");

        assertThat(page.getByRole(AriaRole.BUTTON, new GetByRoleOptions().setName("Reset")))
                .isVisible();
    }

    @Test
    @DisplayName("Reset restores the per-currency defaults over an edit")
    void reset_restoresDefaults() {
        NumberFieldElement.getByLabel(page, "Interest Rate").setValue("11.25");

        page.getByRole(AriaRole.BUTTON, new GetByRoleOptions().setName("Reset")).click();

        NumberFieldElement.getByLabel(page, "Interest Rate").assertValue("8.5");
    }
}
