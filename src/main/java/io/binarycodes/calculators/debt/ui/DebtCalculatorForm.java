package io.binarycodes.calculators.debt.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.BinderValidationStatus;
import com.vaadin.flow.data.binder.Result;
import com.vaadin.flow.data.binder.ValidationResult;
import com.vaadin.flow.data.converter.Converter;
import com.vaadin.flow.data.validator.BigDecimalRangeValidator;
import com.vaadin.flow.data.validator.DoubleRangeValidator;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ValueSignal;
import io.binarycodes.calculators.base.i18n.Translations;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.base.ui.CalculatorForm;
import io.binarycodes.calculators.base.ui.FormCard;
import io.binarycodes.calculators.base.ui.MoneyField;
import io.binarycodes.calculators.base.ui.PercentageField;
import io.binarycodes.calculators.base.ui.RowControls;
import io.binarycodes.calculators.debt.domain.Debt;
import io.binarycodes.calculators.debt.domain.DebtPlanInputs;
import io.binarycodes.calculators.debt.domain.PayoffStrategy;
import io.binarycodes.calculators.debt.domain.Windfall;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Input form for the debt-payoff planner. A debts card holds an add/remove list
 * of {@link DebtRow}s (name, balance, APR, and the two minimum styles, with the
 * promo-APR window behind an "advanced" disclosure); a plan card carries the
 * payoff strategy, the extra monthly payment, and an optional inflation rate for
 * the today's-money interest totals. The debts list and every scalar field are
 * exposed through {@link #inputsSignal()} so the view recalculates live.
 */
public class DebtCalculatorForm extends VerticalLayout implements CalculatorForm<DebtPlanInputs> {

    private final UserPreferences preferences;
    private final Binder<DebtPlanInputs> binder = new Binder<>(DebtPlanInputs.class);

    private final DebtsSection debtsSection = new DebtsSection();
    private final WindfallsSection windfallsSection = new WindfallsSection();
    private final RadioButtonGroup<PayoffStrategy> strategy = new RadioButtonGroup<>();
    private final MoneyField extraPerMonth;
    private final NumberField extraStepUpPct =
            PercentageField.create(Translations.get("field.debt.extraStepUp"));
    private final NumberField inflationRatePct =
            PercentageField.create(Translations.get("field.debt.inflationRate"));

    private FormCard debtsCard;
    private final Span strategyDescription = secondaryText("");

    private Signal<?> strategySignal;
    private Signal<?> extraPerMonthSignal;
    private Signal<?> extraStepUpSignal;
    private Signal<?> inflationRateSignal;

    private final Signal<DebtPlanInputs> inputsSignal;

    public DebtCalculatorForm(UserPreferences preferences) {
        this.preferences = preferences;
        addClassName("debt-form");
        setPadding(false);
        setSpacing(true);
        setWidthFull();

        this.extraPerMonth = new MoneyField(Translations.get("field.debt.extraPerMonth"), preferences);

        configureStrategy();
        configureBindings();

        add(buildDebtsCard(), buildPlanCard(), buildWindfallsCard());

        this.inputsSignal = Signal.computed(() -> {
            this.debtsSection.signal.get();
            this.windfallsSection.signal.get();
            this.strategySignal.get();
            this.extraPerMonthSignal.get();
            this.extraStepUpSignal.get();
            this.inflationRateSignal.get();
            return buildInputs();
        });
    }

    @Override
    public Signal<DebtPlanInputs> inputsSignal() {
        return this.inputsSignal;
    }

    @Override
    public void setInputs(DebtPlanInputs inputs) {
        this.binder.readBean(inputs);
        if (this.strategy.isEmpty()) {
            this.strategy.setValue(PayoffStrategy.AVALANCHE);
        }
        this.debtsSection.set(inputs.getDebts());
        this.windfallsSection.set(inputs.getWindfalls());
    }

    @Override
    public void clear() {
        setInputs(new DebtPlanInputs());
    }

    @Override
    public DebtPlanInputs getInputs() {
        return buildInputs();
    }

    @Override
    public void showValidationMessages(String calculationError) {
        FormCard.refreshGenericErrors(this);
        if (calculationError != null && !this.debtsCard.hasInvalidField()) {
            this.debtsCard.showError(calculationError);
        }
    }

    /** Valid once every debt row is complete, there is at least one debt, and every windfall row is complete. */
    public boolean isValid() {
        return this.binder.isValid() && this.debtsSection.hasDebts() && this.debtsSection.isValid()
                && this.windfallsSection.isValid();
    }

    public BinderValidationStatus<DebtPlanInputs> validate() {
        return this.binder.validate();
    }

    /** Visible for tests: the description shown beneath the strategy toggle. */
    String strategyDescriptionText() {
        return this.strategyDescription.getText();
    }

    private DebtPlanInputs buildInputs() {
        final var target = new DebtPlanInputs();
        this.binder.writeBeanAsDraft(target);
        target.setDebts(this.debtsSection.snapshot());
        target.setWindfalls(this.windfallsSection.snapshot());
        return target;
    }

    private void configureStrategy() {
        this.strategy.setItems(PayoffStrategy.values());
        this.strategy.setItemLabelGenerator(DebtCalculatorForm::strategyLabel);
        this.strategy.setValue(PayoffStrategy.AVALANCHE);
        this.strategy.addClassName("segmented-toggle");
        // The description below the toggle explains whichever strategy is selected.
        this.strategyDescription.addClassName("subsection-hint");
        this.strategyDescription.setText(strategyDescription(this.strategy.getValue()));
        this.strategy.addValueChangeListener(
                event -> this.strategyDescription.setText(strategyDescription(event.getValue())));
    }

    private void configureBindings() {
        this.strategySignal = this.binder.forField(this.strategy)
                .bind(DebtPlanInputs::getStrategy, DebtPlanInputs::setStrategy)
                .valueSignal();

        this.extraPerMonthSignal = this.binder.forField(this.extraPerMonth)
                .withValidator(new BigDecimalRangeValidator(Translations.get("validation.nonNegative"),
                        BigDecimal.ZERO, null))
                .bind(DebtPlanInputs::getExtraPerMonth, DebtPlanInputs::setExtraPerMonth)
                .valueSignal();

        this.extraStepUpSignal = this.binder.forField(this.extraStepUpPct)
                .withValidator(new DoubleRangeValidator(Translations.get("validation.between", 0, 50), 0d, 50d))
                .withConverter(doubleToBigDecimalConverter())
                .bind(DebtPlanInputs::getExtraStepUpPct, DebtPlanInputs::setExtraStepUpPct)
                .valueSignal();

        this.inflationRateSignal = this.binder.forField(this.inflationRatePct)
                .withValidator(new DoubleRangeValidator(Translations.get("validation.between", 0, 20), 0d, 20d))
                .withConverter(doubleToBigDecimalConverter())
                .bind(DebtPlanInputs::getInflationRatePct, DebtPlanInputs::setInflationRatePct)
                .valueSignal();
    }

    private Component buildDebtsCard() {
        final Span intro = secondaryText(Translations.get("debt.debts.intro"));

        final Button addButton = new Button(Translations.get("debt.addDebt"), VaadinIcon.PLUS.create(),
                event -> this.debtsSection.add(new Debt()));
        addButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        final var content = new VerticalLayout(intro, this.debtsSection.rowsContainer, addButton);
        content.setPadding(false);
        content.setSpacing(true);

        this.debtsCard = new FormCard(Translations.get("section.debt.debts"));
        this.debtsCard.setWidthFull();
        this.debtsCard.addClassName("form-section");
        this.debtsCard.add(content);
        return this.debtsCard;
    }

    private Component buildPlanCard() {
        final FormLayout layout = new FormLayout();
        layout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("36em", 2),
                new FormLayout.ResponsiveStep("64em", 3));
        layout.add(this.extraPerMonth, withPercentageSuffix(this.extraStepUpPct),
                withPercentageSuffix(this.inflationRatePct));

        final var content = new VerticalLayout(this.strategy, this.strategyDescription, layout);
        content.setPadding(false);
        content.setSpacing(true);

        final FormCard card = new FormCard(Translations.get("section.debt.plan"));
        card.setWidthFull();
        card.addClassName("form-section");
        card.add(content);
        return card;
    }

    private Component buildWindfallsCard() {
        final Span intro = secondaryText(Translations.get("debt.windfalls.intro"));

        final Button addButton = new Button(Translations.get("debt.addWindfall"), VaadinIcon.PLUS.create(),
                event -> this.windfallsSection.add(new Windfall()));
        addButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        final var content = new VerticalLayout(intro, this.windfallsSection.rowsContainer, addButton);
        content.setPadding(false);
        content.setSpacing(true);

        final FormCard card = new FormCard(Translations.get("section.debt.windfalls"));
        card.setWidthFull();
        card.addClassName("form-section");
        card.add(content);
        return card;
    }

    private static String strategyLabel(PayoffStrategy strategy) {
        return switch (strategy) {
            case SNOWBALL -> Translations.get("debt.strategy.snowball");
            case CUSTOM -> Translations.get("debt.strategy.custom");
            case AVALANCHE -> Translations.get("debt.strategy.avalanche");
        };
    }

    private static String strategyDescription(PayoffStrategy strategy) {
        return switch (strategy == null ? PayoffStrategy.AVALANCHE : strategy) {
            case SNOWBALL -> Translations.get("debt.strategy.snowball.desc");
            case CUSTOM -> Translations.get("debt.strategy.custom.desc");
            case AVALANCHE -> Translations.get("debt.strategy.avalanche.desc");
        };
    }

    /** The debts list: rows container + authoritative list + published snapshot. */
    private final class DebtsSection {
        private final VerticalLayout rowsContainer = new VerticalLayout();
        private final List<DebtRow> rows = new ArrayList<>();
        private final ValueSignal<List<Debt>> signal = new ValueSignal<>(List.of());

        DebtsSection() {
            this.rowsContainer.setPadding(false);
            this.rowsContainer.setSpacing(true);
            this.rowsContainer.setWidthFull();
        }

        private void add(Debt initial) {
            final DebtRow row = new DebtRow(DebtCalculatorForm.this.preferences, initial,
                    this::remove, this::moveUp, this::moveDown, this::publish);
            this.rows.add(row);
            this.rowsContainer.add(row);
            publish();
        }

        private void remove(DebtRow row) {
            if (this.rows.remove(row)) {
                this.rowsContainer.remove(row);
                publish();
            }
        }

        private void moveUp(DebtRow row) {
            final int index = this.rows.indexOf(row);
            if (index > 0) {
                swap(index, index - 1);
            }
        }

        private void moveDown(DebtRow row) {
            final int index = this.rows.indexOf(row);
            if (index >= 0 && index < this.rows.size() - 1) {
                swap(index, index + 1);
            }
        }

        private void swap(int first, int second) {
            Collections.swap(this.rows, first, second);
            // Re-adding the same row instances preserves their field state while
            // reordering them in the DOM; the custom strategy pays in this order.
            this.rowsContainer.removeAll();
            this.rows.forEach(this.rowsContainer::add);
            publish();
        }

        private void publish() {
            this.signal.set(snapshot());
        }

        private List<Debt> snapshot() {
            final List<Debt> out = new ArrayList<>();
            for (final DebtRow row : this.rows) {
                out.add(row.snapshot());
            }
            return out;
        }

        private void set(List<Debt> debts) {
            this.rowsContainer.removeAll();
            this.rows.clear();
            if (debts != null) {
                for (final Debt debt : debts) {
                    add(debt);
                }
            }
            publish();
        }

        private boolean hasDebts() {
            return !this.rows.isEmpty();
        }

        private boolean isValid() {
            return this.rows.stream().allMatch(DebtRow::isValid);
        }
    }

    private static final class DebtRow extends VerticalLayout {
        private final TextField nameField = new TextField(Translations.get("field.debt.name"));
        private final MoneyField balanceField;
        private final NumberField aprField = PercentageField.create(Translations.get("field.debt.apr"));
        private final MoneyField minimumPaymentField;
        private final NumberField minimumPctField = PercentageField.create(Translations.get("field.debt.minimumPct"));
        private final NumberField promoAprField = PercentageField.create(Translations.get("field.debt.promoApr"));
        private final IntegerField promoMonthsField = monthsField(Translations.get("field.debt.promoMonths"));
        private final Binder<Debt> binder = new Binder<>(Debt.class);

        DebtRow(UserPreferences preferences, Debt initial, Consumer<DebtRow> onRemove,
                Consumer<DebtRow> onMoveUp, Consumer<DebtRow> onMoveDown, Runnable onChanged) {
            this.balanceField = new MoneyField(Translations.get("field.debt.balance"), preferences);
            this.minimumPaymentField = new MoneyField(Translations.get("field.debt.minimumPayment"), preferences);
            this.nameField.setValueChangeMode(ValueChangeMode.LAZY);

            this.nameField.setValue(initial.getName() == null ? "" : initial.getName());
            this.balanceField.setValue(initial.getBalance());
            this.aprField.setValue(toDouble(initial.getAprPct()));
            this.minimumPaymentField.setValue(initial.getMinimumPayment());
            this.minimumPctField.setValue(toDouble(initial.getMinimumPct()));
            this.promoAprField.setValue(toDouble(initial.getPromoAprPct()));
            this.promoMonthsField.setValue(initial.getPromoMonths());

            configureBindings();

            for (final Component field : new Component[]{this.nameField, this.balanceField, this.aprField,
                    this.minimumPaymentField, this.minimumPctField, this.promoAprField, this.promoMonthsField}) {
                ((com.vaadin.flow.component.HasValue<?, ?>) field).addValueChangeListener(event -> onChanged.run());
            }
            this.binder.addStatusChangeListener(event -> onChanged.run());

            final Button moveUpButton = iconButton(VaadinIcon.ARROW_UP, "row.moveUp", () -> onMoveUp.accept(this));
            final Button moveDownButton = iconButton(VaadinIcon.ARROW_DOWN, "row.moveDown", () -> onMoveDown.accept(this));
            final Button removeButton = RowControls.removeButton(() -> onRemove.accept(this));

            final HorizontalLayout mainRow = new HorizontalLayout(this.nameField, this.balanceField,
                    withPercentageSuffix(this.aprField), this.minimumPaymentField,
                    withPercentageSuffix(this.minimumPctField), moveUpButton, moveDownButton, removeButton);
            mainRow.setWidthFull();
            mainRow.setAlignItems(Alignment.BASELINE);
            mainRow.addClassName("form-row");
            mainRow.expand(this.nameField);

            setPadding(false);
            setSpacing(false);
            setWidthFull();
            addClassName("debt-row");
            add(mainRow, buildAdvanced());
        }

        private Component buildAdvanced() {
            final FormLayout layout = new FormLayout();
            layout.setResponsiveSteps(
                    new FormLayout.ResponsiveStep("0", 1),
                    new FormLayout.ResponsiveStep("36em", 2));
            layout.add(withPercentageSuffix(this.promoAprField), this.promoMonthsField);

            final Details advanced = new Details(Translations.get("debt.advanced"), layout);
            advanced.addThemeName("small");
            advanced.setWidthFull();
            return advanced;
        }

        private void configureBindings() {
            this.binder.forField(this.nameField)
                    .asRequired(Translations.get("validation.required"))
                    .bind(Debt::getName, Debt::setName);
            this.binder.forField(this.balanceField)
                    .asRequired(Translations.get("validation.required"))
                    .withValidator(new BigDecimalRangeValidator(Translations.get("validation.positive"),
                            new BigDecimal("0.01"), null))
                    .bind(Debt::getBalance, Debt::setBalance);
            this.binder.forField(this.aprField)
                    .asRequired(Translations.get("validation.required"))
                    .withValidator(new DoubleRangeValidator(Translations.get("validation.between", 0, 100), 0d, 100d))
                    .withConverter(doubleToBigDecimalConverter())
                    .bind(Debt::getAprPct, Debt::setAprPct);
            this.binder.forField(this.minimumPaymentField)
                    .withValidator(new BigDecimalRangeValidator(Translations.get("validation.nonNegative"),
                            BigDecimal.ZERO, null))
                    .bind(Debt::getMinimumPayment, Debt::setMinimumPayment);
            this.binder.forField(this.minimumPctField)
                    .withValidator(new DoubleRangeValidator(Translations.get("validation.between", 0, 100), 0d, 100d))
                    .withConverter(doubleToBigDecimalConverter())
                    .bind(Debt::getMinimumPct, Debt::setMinimumPct);
            this.binder.forField(this.promoAprField)
                    .withValidator(new DoubleRangeValidator(Translations.get("validation.between", 0, 100), 0d, 100d))
                    .withConverter(doubleToBigDecimalConverter())
                    .bind(Debt::getPromoAprPct, Debt::setPromoAprPct);
            this.binder.forField(this.promoMonthsField)
                    .withValidator((value, context) -> value == null || (value >= 0 && value <= 240)
                            ? ValidationResult.ok()
                            : ValidationResult.error(Translations.get("validation.between", 0, 240)))
                    .bind(Debt::getPromoMonths, Debt::setPromoMonths);
            this.binder.validate();
        }

        private boolean isValid() {
            return this.binder.isValid();
        }

        private Debt snapshot() {
            final Debt debt = new Debt();
            this.binder.writeBeanAsDraft(debt);
            return debt;
        }

        private static Double toDouble(BigDecimal value) {
            return value == null ? null : value.doubleValue();
        }
    }

    /** The optional one-off windfalls list: rows container + authoritative list + published snapshot. */
    private final class WindfallsSection {
        private final VerticalLayout rowsContainer = new VerticalLayout();
        private final List<WindfallRow> rows = new ArrayList<>();
        private final ValueSignal<List<Windfall>> signal = new ValueSignal<>(List.of());

        WindfallsSection() {
            this.rowsContainer.setPadding(false);
            this.rowsContainer.setSpacing(true);
            this.rowsContainer.setWidthFull();
        }

        private void add(Windfall initial) {
            final WindfallRow row = new WindfallRow(DebtCalculatorForm.this.preferences, initial,
                    this::remove, this::publish);
            this.rows.add(row);
            this.rowsContainer.add(row);
            publish();
        }

        private void remove(WindfallRow row) {
            if (this.rows.remove(row)) {
                this.rowsContainer.remove(row);
                publish();
            }
        }

        private void publish() {
            this.signal.set(snapshot());
        }

        private List<Windfall> snapshot() {
            final List<Windfall> out = new ArrayList<>();
            for (final WindfallRow row : this.rows) {
                out.add(row.snapshot());
            }
            return out;
        }

        private void set(List<Windfall> windfalls) {
            this.rowsContainer.removeAll();
            this.rows.clear();
            if (windfalls != null) {
                for (final Windfall windfall : windfalls) {
                    add(windfall);
                }
            }
            publish();
        }

        private boolean isValid() {
            return this.rows.stream().allMatch(WindfallRow::isValid);
        }
    }

    private static final class WindfallRow extends HorizontalLayout {
        private final IntegerField monthField = monthNumberField(Translations.get("field.debt.windfallMonth"));
        private final MoneyField amountField;
        private final Binder<Windfall> binder = new Binder<>(Windfall.class);

        WindfallRow(UserPreferences preferences, Windfall initial, Consumer<WindfallRow> onRemove, Runnable onChanged) {
            this.amountField = new MoneyField(Translations.get("field.debt.windfallAmount"), preferences);

            this.monthField.setValue(initial.getMonth());
            this.amountField.setValue(initial.getAmount());

            this.binder.forField(this.monthField)
                    .asRequired(Translations.get("validation.required"))
                    .withValidator((value, context) -> value != null && value >= 1 && value <= 600
                            ? ValidationResult.ok()
                            : ValidationResult.error(Translations.get("validation.between", 1, 600)))
                    .bind(Windfall::getMonth, Windfall::setMonth);
            this.binder.forField(this.amountField)
                    .asRequired(Translations.get("validation.required"))
                    .withValidator(new BigDecimalRangeValidator(Translations.get("validation.positive"),
                            new BigDecimal("0.01"), null))
                    .bind(Windfall::getAmount, Windfall::setAmount);
            this.binder.validate();

            this.monthField.addValueChangeListener(event -> onChanged.run());
            this.amountField.addValueChangeListener(event -> onChanged.run());
            this.binder.addStatusChangeListener(event -> onChanged.run());

            final Button removeButton = RowControls.removeButton(() -> onRemove.accept(this));

            setWidthFull();
            setAlignItems(Alignment.BASELINE);
            addClassName("form-row");
            add(this.monthField, this.amountField, removeButton);
            expand(this.amountField);
        }

        private boolean isValid() {
            return this.binder.isValid();
        }

        private Windfall snapshot() {
            final Windfall windfall = new Windfall();
            this.binder.writeBeanAsDraft(windfall);
            return windfall;
        }
    }

    private static Button iconButton(VaadinIcon icon, String ariaLabelKey, Runnable action) {
        final Button button = new Button(icon.create(), event -> action.run());
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        button.setAriaLabel(Translations.get(ariaLabelKey));
        return button;
    }

    private static IntegerField monthNumberField(String label) {
        final IntegerField field = new IntegerField(label);
        field.setStepButtonsVisible(false);
        field.setMin(1);
        field.setValueChangeMode(ValueChangeMode.LAZY);
        return field;
    }

    private static IntegerField monthsField(String label) {
        final IntegerField field = new IntegerField(label);
        field.setStepButtonsVisible(false);
        field.setMin(0);
        field.setSuffixComponent(secondaryText(Translations.get("unit.mo")));
        field.setValueChangeMode(ValueChangeMode.LAZY);
        return field;
    }

    private static NumberField withPercentageSuffix(NumberField field) {
        if (field.getSuffixComponent() == null) {
            field.setSuffixComponent(secondaryText(Translations.get("unit.percent")));
        }
        return field;
    }

    private static Span secondaryText(String text) {
        final Span span = new Span(text);
        span.getStyle().setColor("var(--vaadin-secondary-text-color, #71717a)");
        return span;
    }

    private static Converter<Double, BigDecimal> doubleToBigDecimalConverter() {
        return Converter.from(
                value -> Result.ok(value == null ? null : BigDecimal.valueOf(value)),
                value -> value == null ? null : value.doubleValue());
    }
}
