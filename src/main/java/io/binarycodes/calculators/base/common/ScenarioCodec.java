package io.binarycodes.calculators.base.common;

import io.binarycodes.calculators.base.money.SupportedCurrency;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Encodes a calculator's inputs (plus the active currency) into a single
 * opaque, URL-safe token for shareable links, and decodes it back.
 *
 * <p>The token is base64url (no padding) of a small JSON envelope:
 * <pre>{"v":1,"currency":"INR","inputs":{…}}</pre>
 * The {@code v} field lets the shape evolve without breaking old links.
 *
 * <p>Tokens arrive from user-controllable URLs, so {@link #decode} treats its
 * input as untrusted: it bounds the token and decoded sizes, requires a known
 * schema version and currency, and rejects numerically absurd values (e.g.
 * {@code 1E999999999}) whose later {@code toPlainString()} would expand to
 * gigabytes. It never does polymorphic deserialization — inputs come back as a
 * plain {@link JsonNode} that each store maps into its own bean, so a token
 * cannot instantiate arbitrary types.
 */
public final class ScenarioCodec {

    private static final int VERSION = 1;

    /**
     * Reject longer query tokens before doing any work.
     */
    private static final int MAX_TOKEN_CHARS = 8192;
    /**
     * Reject larger decoded payloads — also bounds how many list entries fit.
     */
    private static final int MAX_DECODED_BYTES = 32 * 1024;
    /**
     * No real money/percentage input needs more digits or scale than this.
     */
    private static final int MAX_DECIMAL_PRECISION = 100;
    private static final int MAX_DECIMAL_SCALE = 100;

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private ScenarioCodec() {
    }

    public static String encode(SupportedCurrency currency, JsonNode inputs) {
        final ObjectNode envelope = OBJECT_MAPPER.createObjectNode();
        envelope.put("v", VERSION);
        envelope.put("currency", currency.name());
        envelope.set("inputs", inputs);
        return ENCODER.encodeToString(envelope.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static Decoded decode(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Empty share token");
        }
        if (token.length() > MAX_TOKEN_CHARS) {
            throw new IllegalArgumentException("Share token too large");
        }

        final byte[] json;
        try {
            json = DECODER.decode(token);
        } catch (final IllegalArgumentException malformed) {
            throw new IllegalArgumentException("Share token is not valid base64url", malformed);
        }
        if (json.length > MAX_DECODED_BYTES) {
            throw new IllegalArgumentException("Share payload too large");
        }

        final JsonNode envelope;
        try {
            envelope = OBJECT_MAPPER.readTree(json);
        } catch (final Exception malformed) {
            throw new IllegalArgumentException("Share token is not valid JSON", malformed);
        }

        final JsonNode versionNode = envelope.get("v");
        if (versionNode == null || versionNode.asInt() != VERSION) {
            throw new IllegalArgumentException("Unsupported share-link version");
        }

        final SupportedCurrency currency = parseCurrency(envelope.get("currency"));

        final JsonNode inputs = envelope.get("inputs");
        if (inputs == null || !inputs.isObject()) {
            throw new IllegalArgumentException("Share token has no inputs");
        }
        rejectAbsurdDecimals(inputs);

        return new Decoded(currency, inputs);
    }

    private static SupportedCurrency parseCurrency(JsonNode node) {
        if (node == null || node.isNull()) {
            throw new IllegalArgumentException("Share token has no currency");
        }
        try {
            return SupportedCurrency.valueOf(node.asString());
        } catch (final IllegalArgumentException unknown) {
            throw new IllegalArgumentException("Unknown currency in share token", unknown);
        }
    }

    /**
     * Walk every leaf that parses as a number and reject values whose precision
     * or scale dwarfs anything a real input needs. The byte cap already bounds
     * the payload; this guards specifically against exponent tricks like
     * {@code 1E999999999} that are short to write but catastrophic to render.
     */
    private static void rejectAbsurdDecimals(JsonNode node) {
        if (node.isObject() || node.isArray()) {
            for (final JsonNode child : node) {
                rejectAbsurdDecimals(child);
            }
            return;
        }
        if (!node.isValueNode() || node.isBoolean() || node.isNull()) {
            return;
        }
        final BigDecimal value;
        try {
            value = new BigDecimal(node.asString());
        } catch (final NumberFormatException notANumber) {
            return; // a plain string such as a description — nothing to bound
        }
        if (value.precision() > MAX_DECIMAL_PRECISION || Math.abs(value.scale()) > MAX_DECIMAL_SCALE) {
            throw new IllegalArgumentException("Numeric value out of range in share token");
        }
    }

    /**
     * A decoded share token: the currency it was built under and its raw inputs JSON.
     */
    public record Decoded(SupportedCurrency currency, JsonNode inputs) {
    }
}
