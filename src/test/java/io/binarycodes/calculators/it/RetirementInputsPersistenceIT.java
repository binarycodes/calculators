package io.binarycodes.calculators.it;

import io.binarycodes.calculators.it.support.SpringPlaywrightIT;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.vaadin.addons.dramafinder.element.NumberFieldElement;

/**
 * Verifies that the {@code RetirementInputsStore} persists edits to the
 * browser's local storage and that {@code RetirementView.onAttach} restores
 * them on the next visit (covers the persistence round-trip that has no
 * pure-Java tests).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class RetirementInputsPersistenceIT extends SpringPlaywrightIT {

    @Override
    public String getView() {
        return "retirement";
    }

    @Test
    void editedInflationRate_survivesPageReload() {
        final NumberFieldElement inflation = NumberFieldElement.getByLabel(page, "Inflation Rate");
        inflation.setValue("7.5");
        inflation.assertValue("7.5");

        // RetirementInputsStore.save() is an async WebStorage.setItem() call
        // — wait until the JSON blob in localStorage actually reflects the
        // new inflation rate before triggering a navigation that would race
        // the unsent client RPC.
        page.waitForFunction(
                "() => {"
                        + "const raw = localStorage.getItem('rc_inputs');"
                        + "if (!raw) return false;"
                        + "try {"
                        + "  const all = JSON.parse(raw);"
                        + "  return Object.values(all).some(v => v && String(v.inflation) === '7.5');"
                        + "} catch (e) { return false; }"
                        + "}");

        // Force a full navigation + Vaadin re-bootstrap so the value can only
        // come back via the persistence store, not from in-memory state.
        page.reload();
        page.waitForFunction(WAIT_FOR_VAADIN_SCRIPT);

        NumberFieldElement.getByLabel(page, "Inflation Rate").assertValue("7.5");
    }
}
