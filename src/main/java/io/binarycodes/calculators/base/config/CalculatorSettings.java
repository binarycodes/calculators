package io.binarycodes.calculators.base.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Deployment-wide calculator behaviour, configurable via {@code app.calculators.*}.
 *
 * <p>{@code prefillDefaults} decides what a visitor sees on a calculator with
 * nothing persisted yet: the sample scenario shipped in the classpath defaults,
 * or a blank form. Turning it off suits a deployment where invented numbers
 * would read as advice, and takes the Reset action with it — restoring the
 * sample scenario is the one thing that overwrites a visitor's own inputs.
 */
@ConfigurationProperties("app.calculators")
public record CalculatorSettings(@DefaultValue("true") boolean prefillDefaults) {
}
