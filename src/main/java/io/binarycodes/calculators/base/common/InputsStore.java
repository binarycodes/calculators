package io.binarycodes.calculators.base.common;

import io.binarycodes.calculators.base.money.SupportedCurrency;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Per-currency store of a calculator's inputs, backed by browser localStorage,
 * plus the JSON (de)serialisation reused by shareable links.
 *
 * @param <I> the calculator's input bean type
 */
public interface InputsStore<I> {

    /** Load all currencies from localStorage; {@code onLoaded} runs once the async fetch completes. */
    void load(Consumer<Map<SupportedCurrency, I>> onLoaded);

    I get(SupportedCurrency currency);

    void save(SupportedCurrency currency, I inputs);

    ObjectNode toJsonNode(I inputs);

    I fromJsonNode(JsonNode node);
}
