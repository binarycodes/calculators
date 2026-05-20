package io.binarycodes.calculators.base.prefs;

/**
 * The three discrete font sizes exposed as a radio choice. Pixel sizes are
 * applied to the root {@code <html>} element so all {@code rem}-based Vaadin
 * styles scale proportionally.
 */
public enum FontSize {
    SMALL(14, "Small text"),
    MEDIUM(16, "Medium text"),
    LARGE(20, "Large text");

    private final int px;
    private final String accessibleName;

    FontSize(int px, String accessibleName) {
        this.px = px;
        this.accessibleName = accessibleName;
    }

    public int px() {
        return this.px;
    }

    public String accessibleName() {
        return this.accessibleName;
    }

    public static FontSize fromPx(int px) {
        FontSize best = MEDIUM;
        int bestDiff = Math.abs(MEDIUM.px - px);
        for (final FontSize s : values()) {
            final int diff = Math.abs(s.px - px);
            if (diff < bestDiff) {
                best = s;
                bestDiff = diff;
            }
        }
        return best;
    }
}
