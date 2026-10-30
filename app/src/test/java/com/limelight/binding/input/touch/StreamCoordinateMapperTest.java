package com.limelight.binding.input.touch;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class StreamCoordinateMapperTest {
    @Test
    public void mapsBackgroundCoordinatesIntoScaledStreamSurface() {
        float[] mapped = StreamCoordinateMapper.mapToTarget(
                60.0f, 80.0f,
                50, 100,
                100, 160,
                2.0f, 2.0f);

        assertEquals(5.0f, mapped[0], 0.001f);
        assertEquals(10.0f, mapped[1], 0.001f);
    }

    @Test
    public void treatsInvalidScaleAsOne() {
        float[] mapped = StreamCoordinateMapper.mapToTarget(
                30.0f, 40.0f,
                10, 20,
                0, 0,
                0.0f, -1.0f);

        assertEquals(40.0f, mapped[0], 0.001f);
        assertEquals(60.0f, mapped[1], 0.001f);
    }
}
