package io.binarycodes.calculators.base.ui;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.signals.Signal;
import io.binarycodes.calculators.base.common.CalculatorDefaults;
import io.binarycodes.calculators.base.common.InputsStore;
import io.binarycodes.calculators.base.common.ScenarioCodec;
import io.binarycodes.calculators.base.common.SharedScenario;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.base.prefs.UserPreferences;

import java.util.Optional;

/**
 * Common scaffolding for every calculator screen: the title header (with the
 * Share button), the input form, the Calculate / Reset action row, and the full
 * persistence + shareable-link lifecycle. Subclasses add their own summary
 * cards, charts, and grids after {@code super(...)} and implement
 * {@link #updateResults()} to render a fresh calculation.
 *
 * @param <I> the calculator's input bean type
 * @param <F> the concrete form component, which is both a {@link Component} and
 *            a {@link CalculatorForm} so it can be laid out and driven here
 */
public abstract class BaseCalculatorView<I, F extends Component & CalculatorForm<I>>
        extends VerticalLayout implements BeforeEnterObserver, HasDynamicTitle {

    protected final UserPreferences preferences;
    protected final F form;

    private final InputsStore<I> inputsStore;
    private final CalculatorDefaults<I> defaults;
    private final String routeSegment;
    private final String titleKey;

    private final ShareLinkButton shareButton;

    /** A {@code ?s=} share token captured on entry, consumed once inputs have loaded. */
    private String pendingShareToken;

    protected BaseCalculatorView(UserPreferences preferences,
                                 InputsStore<I> inputsStore,
                                 CalculatorDefaults<I> defaults,
                                 F form,
                                 String routeSegment,
                                 String titleKey) {
        this.preferences = preferences;
        this.inputsStore = inputsStore;
        this.defaults = defaults;
        this.form = form;
        this.routeSegment = routeSegment;
        this.titleKey = titleKey;
        this.shareButton = new ShareLinkButton(getTranslation(titleKey));

        addClassName(routeSegment + "-view");
        setWidthFull();
        setPadding(true);
        setSpacing(true);

        add(buildHeader(getTranslation(titleKey)), form, buildActionRow());

        // Persist and recalculate on any field change; skip the initial run that
        // fires with the form's empty starting state before onAttach loads inputs.
        Signal.effect(this, context -> {
            this.form.inputsSignal().get();
            if (context.isInitialRun()) {
                return;
            }
            onInputChanged();
        });

        preferences.addChangeListener(ignored -> onPreferencesChanged());
    }

    /** Render the summary cards, charts, and grids from the current (valid) form state. */
    protected abstract void updateResults();

    @Override
    public String getPageTitle() {
        return getTranslation(this.titleKey);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        this.pendingShareToken = event.getLocation().getQueryParameters()
                .getSingleParameter("s").orElse(null);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        // Browser localStorage is read asynchronously, so wait for both prefs and
        // inputs before populating the form.
        this.preferences.loadFromBrowser(() ->
                this.inputsStore.load(unused -> {
                    if (!applyShareTokenIfPresent()) {
                        populateFromPersistedOrDefault(this.preferences.currency());
                    }
                    recalculate();
                })
        );
    }

    private Component buildHeader(String title) {
        final HorizontalLayout header = new HorizontalLayout(new H2(title), this.shareButton);
        header.addClassName("view-header");
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setSpacing(true);
        return header;
    }

    private HorizontalLayout buildActionRow() {
        final Button calculateButton = new Button(getTranslation("action.calculate"), event -> recalculate());
        calculateButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        // Reset restores the per-currency defaults; Clear blanks the form entirely.
        final Button resetButton = new Button(getTranslation("action.reset"), event -> resetToDefaults());

        final Button clearButton = new Button(getTranslation("action.clear"), event -> clearInputs());
        clearButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        final HorizontalLayout primaryActions = new HorizontalLayout(calculateButton, resetButton);
        primaryActions.setSpacing(true);

        final HorizontalLayout actionRow = new HorizontalLayout(primaryActions, clearButton);
        actionRow.addClassName("action-row");
        actionRow.setWidthFull();
        actionRow.setAlignItems(FlexComponent.Alignment.CENTER);
        actionRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        return actionRow;
    }

    /** Refresh the share token, then let the subclass render results. */
    protected final void recalculate() {
        refreshShareToken();
        updateResults();
    }

    private void onInputChanged() {
        this.inputsStore.save(this.preferences.currency(), this.form.getInputs());
        recalculate();
    }

    private void onPreferencesChanged() {
        populateFromPersistedOrDefault(this.preferences.currency());
        recalculate();
    }

    private void populateFromPersistedOrDefault(SupportedCurrency currency) {
        I inputs = this.inputsStore.get(currency);
        if (inputs == null) {
            inputs = this.defaults.forCurrency(currency);
        }
        this.form.setInputs(inputs);
    }

    private void resetToDefaults() {
        final SupportedCurrency currency = this.preferences.currency();
        final I defaultInputs = this.defaults.forCurrency(currency);
        this.inputsStore.save(currency, defaultInputs);
        this.form.setInputs(defaultInputs);
        recalculate();
    }

    private void clearInputs() {
        this.form.clear();
        this.inputsStore.save(this.preferences.currency(), this.form.getInputs());
        recalculate();
    }

    /**
     * Apply a captured {@code ?s=} share token: switch currency, load the decoded
     * inputs (overriding the persisted/default load), and clear the token from the
     * address bar. Returns {@code false} when there's no token or it's invalid, so
     * the caller falls back to the normal load.
     */
    private boolean applyShareTokenIfPresent() {
        final String token = this.pendingShareToken;
        this.pendingShareToken = null;
        if (token == null) {
            return false;
        }
        final Optional<SharedScenario<I>> scenario = SharedScenario.parse(token, this.inputsStore);
        if (scenario.isEmpty()) {
            Notification.show(getTranslation("share.invalid"), 3000, Notification.Position.MIDDLE);
            return false;
        }
        // Set currency first: it notifies listeners and repopulates the form from
        // store/defaults, but the explicit setInputs below runs after and wins.
        this.preferences.setCurrency(scenario.get().currency());
        final SupportedCurrency currency = this.preferences.currency();
        final I inputs = scenario.get().inputs();
        this.inputsStore.save(currency, inputs);
        this.form.setInputs(inputs);
        getUI().ifPresent(ui -> ui.getPage().getHistory().replaceState(null, this.routeSegment));
        return true;
    }

    /** Keep the Share button's token in sync with the current form so a click copies a fresh link. */
    private void refreshShareToken() {
        try {
            this.shareButton.setToken(ScenarioCodec.encode(this.preferences.currency(),
                    this.inputsStore.toJsonNode(this.form.getInputs())));
        } catch (final RuntimeException ignore) {
            // A half-edited form can't be serialised yet; the next recalculate retries.
        }
    }
}
