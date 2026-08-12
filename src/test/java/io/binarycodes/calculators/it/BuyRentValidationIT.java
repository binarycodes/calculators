package io.binarycodes.calculators.it;

import io.binarycodes.calculators.it.support.SpringPlaywrightIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.vaadin.addons.dramafinder.element.IntegerFieldElement;

/**
 * The analysis horizon must cover at least the loan term — projecting fewer
 * years than the mortgage runs would cut it off mid-amortization.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DisplayName("Buy vs Rent — horizon vs loan-term validation")
class BuyRentValidationIT extends SpringPlaywrightIT {

    @Override
    public String getView() {
        return "buyrent";
    }

    @Test
    @DisplayName("A horizon shorter than the loan term is flagged invalid")
    void horizonShorterThanLoanTerm_isFlaggedInvalid() {
        final IntegerFieldElement loanTerm = IntegerFieldElement.getByLabel(page, "Loan Term");
        final IntegerFieldElement horizon = IntegerFieldElement.getByLabel(page, "Analysis Horizon");

        loanTerm.setValue("20");
        horizon.setValue("10");

        horizon.assertInvalid();
    }
}
