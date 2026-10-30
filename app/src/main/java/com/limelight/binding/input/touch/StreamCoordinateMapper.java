package com.limelight.binding.input.touch;

/**
 * Converts coordinates local to a source view into the unscaled local
 * coordinate space of the stream surface.
 */
public final class StreamCoordinateMapper {
    private StreamCoordinateMapper() {}

    public static float[] mapToTarget(float sourceX, float sourceY,
                                      int sourceScreenX, int sourceScreenY,
                                      int targetScreenX, int targetScreenY,
                                      float targetScaleX, float targetScaleY) {
        float scaleX = targetScaleX > 0.0f ? targetScaleX : 1.0f;
        float scaleY = targetScaleY > 0.0f ? targetScaleY : 1.0f;
        return new float[] {
                (sourceX + sourceScreenX - targetScreenX) / scaleX,
                (sourceY + sourceScreenY - targetScreenY) / scaleY
        };
    }
}
