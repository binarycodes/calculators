package io.binarycodes.calculators.base.common;

import io.binarycodes.calculators.base.money.SupportedCurrency;

import java.util.Optional;

/**
 * A scenario recovered from a {@code ?s=} share token: the currency it was
 * shared in, plus the inputs mapped into the calculator's own bean.
 *
 * <p>{@link ScenarioCodec} only validates the envelope — version, currency, and
 * numeric sanity — so a structurally valid token can still carry an
 * {@code inputs} object whose fields are missing or the wrong shape, which the
 * stores' {@code fromJsonNode} mappers signal by throwing. {@link #parse} is the
 * single boundary where both failure modes collapse into an empty
 * {@link Optional}, so callers get one "invalid link" path and nothing partially
 * applied.
 *
 * @param <I> the calculator's input bean type
 */
public record SharedScenario<I>(SupportedCurrency currency, I inputs) {

    public static <I> Optional<SharedScenario<I>> parse(String token, InputsStore<I> inputsStore) {
        try {
            final ScenarioCodec.Decoded decoded = ScenarioCodec.decode(token);
            return Optional.of(new SharedScenario<>(decoded.currency(), inputsStore.fromJsonNode(decoded.inputs())));
        } catch (final RuntimeException invalid) {
            return Optional.empty();
        }
    }
}
