package io.binarycodes.calculators.retirement.ui;

import com.vaadin.browserless.BrowserlessExtension;
import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.NumberField;
import io.binarycodes.calculators.base.common.Frequency;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.retirement.domain.Contribution;
import io.binarycodes.calculators.retirement.domain.RetirementInputs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Smoke test that contribution rows render their step-up field and round-trip
 * cleanly through the form. Catches binding-level regressions (renamed getters,
 * missing row wiring) that the calculator unit tests can't.
 */
class RetirementCalculatorFormBrowserlessTest extends BrowserlessTest {

    @RegisterExtension
    static final BrowserlessExtension EXTENSION = new BrowserlessExtension()
            .withServices(UserPreferences.class);

    @Override
    protected Set<String> scanPackages() {
        // Default discovery picks up RetirementView via @RouteAlias(""), and the
        // mock setup then tries to render it through MainLayout — whose
        // constructor takes UserPreferences and can't be no-arg-instantiated by
        // the mock. Scope discovery to an empty package so no routes are found
        // and setup doesn't dump a navigation stack trace to stderr.
        return Set.of("io.binarycodes.calculators.test.noroutes");
    }

    @Test
    void renders_a_step_up_field_per_contribution_row() {
        final var form = new RetirementCalculatorForm(new UserPreferences());
        // Seed one pre-retirement and one post-retirement contribution row so
        // each renders its own Step Up field.
        final RetirementInputs seed = new RetirementInputs();
        seed.setPreRetirementContributions(List.of(new Contribution(
                BigDecimal.valueOf(150_000), Frequency.MONTHLY,
                BigDecimal.valueOf(12), BigDecimal.valueOf(10), BigDecimal.ZERO)));
        seed.setPostRetirementContributions(List.of(new Contribution(
                BigDecimal.valueOf(50_000), Frequency.MONTHLY,
                BigDecimal.valueOf(8), BigDecimal.valueOf(5), BigDecimal.ZERO)));
        form.setInputs(seed);

        // TabSheet swaps the selected tab's content into the component tree on
        // the next client round trip, so attach the form to a UI and flush
        // before searching for fields in the (initially-inactive) Investments tab.
        UI.getCurrent().add(form);
        find(TabSheet.class, form).single().setSelectedIndex(1);
        roundTrip();

        final List<NumberField> stepUpFields = find(NumberField.class, form)
                .withCaption("Step Up Percentage (Yearly)").all();

        assertEquals(2, stepUpFields.size(),
                "expected one Step Up field per contribution row (1 pre + 1 post)");
    }

    @Test
    void step_up_values_round_trip_through_binder() {
        final var form = new RetirementCalculatorForm(new UserPreferences());

        final RetirementInputs initial = new RetirementInputs();
        initial.setCurrentAge(38);
        initial.setRetireAge(45);
        initial.setLifeExp(90);
        initial.setCorpus(BigDecimal.valueOf(15_000_000));
        initial.setMonthlyExpenses(BigDecimal.valueOf(100_000));
        initial.setInflationPct(BigDecimal.valueOf(8));
        initial.setGrowthPrePct(BigDecimal.valueOf(12));
        initial.setGrowthPostPct(BigDecimal.valueOf(8));
        initial.setCorpusTaxRatePct(BigDecimal.valueOf(5));
        initial.setPreRetirementContributions(List.of(new Contribution(
                BigDecimal.valueOf(150_000), Frequency.MONTHLY,
                BigDecimal.valueOf(12), BigDecimal.valueOf(10), BigDecimal.valueOf(15))));
        initial.setPostRetirementContributions(List.of(new Contribution(
                BigDecimal.valueOf(50_000), Frequency.QUARTERLY,
                BigDecimal.valueOf(8), BigDecimal.valueOf(5), BigDecimal.valueOf(20))));
        form.setInputs(initial);

        final RetirementInputs roundTripped = form.getInputs();

        assertEquals(1, roundTripped.getPreRetirementContributions().size());
        final Contribution pre = roundTripped.getPreRetirementContributions().getFirst();
        assertNotNull(pre.getStepUpPct());
        assertEquals(0, BigDecimal.valueOf(10).compareTo(pre.getStepUpPct()),
                "pre step-up must survive the row round-trip");
        assertEquals(Frequency.MONTHLY, pre.getFrequency(), "pre frequency must survive");

        assertEquals(1, roundTripped.getPostRetirementContributions().size());
        final Contribution post = roundTripped.getPostRetirementContributions().getFirst();
        assertEquals(0, BigDecimal.valueOf(5).compareTo(post.getStepUpPct()),
                "post step-up must survive the row round-trip");
        assertEquals(Frequency.QUARTERLY, post.getFrequency(), "post frequency must survive");
    }
}
