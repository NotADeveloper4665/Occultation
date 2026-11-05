package com.limelight.ui;

/**
 * Coordinate transforms for a portrait-sized producer surface carrying a
 * landscape-oriented stream. This is independent of Android display rotation;
 * callers must only enable it when the renderer actually applies the matching
 * clockwise GL rotation.
 */
public final class PortraitSurfaceTransform {
    private PortraitSurfaceTransform() {}

    public static final class Point {
        public final float x;
        public final float y;

        public Point(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    /** Landscape input coordinates -> portrait buffer coordinates (90° CW GL rotation). */
    public static Point landscapeToPortrait(float x, float y, float landscapeWidth, float landscapeHeight) {
        checkDimensions(landscapeWidth, landscapeHeight);
        float u = clamp(x / landscapeWidth);
        float v = clamp(y / landscapeHeight);
        return new Point(v * landscapeHeight, (1.0f - u) * landscapeWidth);
    }

    /** Portrait buffer coordinates -> landscape coordinates (inverse of the 90° CW transform). */
    public static Point portraitToLandscape(float x, float y, float portraitWidth, float portraitHeight) {
        checkDimensions(portraitWidth, portraitHeight);
        float u = clamp(x / portraitWidth);
        float v = clamp(y / portraitHeight);
        return new Point((1.0f - v) * portraitHeight, u * portraitWidth);
    }

    public static float landscapeWidthForPortraitBuffer(float portraitWidth, float portraitHeight) {
        checkDimensions(portraitWidth, portraitHeight);
        return portraitHeight;
    }

    public static float landscapeHeightForPortraitBuffer(float portraitWidth, float portraitHeight) {
        checkDimensions(portraitWidth, portraitHeight);
        return portraitWidth;
    }

    private static void checkDimensions(float width, float height) {
        if (!(width > 0.0f) || !(height > 0.0f) ||
                Float.isInfinite(width) || Float.isInfinite(height) ||
                Float.isNaN(width) || Float.isNaN(height)) {
            throw new IllegalArgumentException("Surface dimensions must be finite and positive");
        }
    }

    private static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
