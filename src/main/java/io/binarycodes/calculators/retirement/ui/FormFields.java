package io.binarycodes.calculators.retirement.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.card.Card;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.Binder.Binding;
import com.vaadin.flow.data.binder.Result;
import com.vaadin.flow.data.binder.Setter;
import com.vaadin.flow.data.converter.Converter;
import com.vaadin.flow.data.validator.DoubleRangeValidator;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.function.ValueProvider;
import io.binarycodes.calculators.base.ui.FormCard;
import io.binarycodes.calculators.retirement.domain.RetirementInputs;

import java.math.BigDecimal;
import java.util.Arrays;

/**
 * Shared factories for the retirement-form field widgets and section
 * containers. Each tab consumes these helpers so the look and binding rules
 * stay consistent across tabs.
 */
final class FormFields {

    private FormFields() {
    }

    static IntegerField ageField(String label) {
        final var suffix = new Span("yrs");
        suffix.getStyle().setColor("var(--vaadin-secondary-text-color, #71717a)");

        final var field = new IntegerField(label);
        field.setMin(1);
        field.setMax(120);
        field.setStepButtonsVisible(false);
        field.setSuffixComponent(suffix);
        field.setValueChangeMode(ValueChangeMode.LAZY);
        return field;
    }

    static NumberField percentageField(String label) {
        final var field = new NumberField(label);
        field.setStep(0.1);
        field.setStepButtonsVisible(false);
        field.setValueChangeMode(ValueChangeMode.LAZY);
        return field;
    }

    static NumberField withPercentageSuffix(NumberField field) {
        if (field.getSuffixComponent() == null) {
            final var suffix = new Span("%");
            suffix.getStyle().setColor("var(--vaadin-secondary-text-color, #71717a)");
            field.setSuffixComponent(suffix);
        }
        return field;
    }

    static Component buildSectionCard(String title, Component... fields) {
        final var formLayout = new FormLayout();
        formLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("36em", 2),
                new FormLayout.ResponsiveStep("64em", 3),
                new FormLayout.ResponsiveStep("90em", 4));
        formLayout.add(fields);

        final var card = new FormCard(title);
        card.add(formLayout);
        card.setWidthFull();
        card.addClassNames("form-section");
        return card;
    }

    static Component buildNestedSectionCard(String title, Component... nestedSections) {
        final var wrapper = new VerticalLayout(nestedSections);
        wrapper.setPadding(false);
        wrapper.addClassNames("form-section-container");

        Arrays.stream(nestedSections).forEach(section -> section.addClassNames("inner-form-section"));

        final var card = new Card();
        card.setTitle(title);
        card.setWidthFull();
        card.add(wrapper);
        return card;
    }

    static Binding<RetirementInputs, BigDecimal> bindPercentage(
            Binder<RetirementInputs> binder, NumberField field,
            ValueProvider<RetirementInputs, BigDecimal> getter,
            Setter<RetirementInputs, BigDecimal> setter) {
        return binder.forField(field)
                .asRequired("Required")
                .withValidator(new DoubleRangeValidator("Must be between 0 and 100", 0d, 100d))
                .withConverter(doubleToBigDecimalConverter())
                .bind(getter, setter);
    }

    static Converter<Double, BigDecimal> doubleToBigDecimalConverter() {
        return Converter.from(
                value -> Result.ok(value == null ? null : BigDecimal.valueOf(value)),
                value -> value == null ? null : value.doubleValue()
        );
    }
}
