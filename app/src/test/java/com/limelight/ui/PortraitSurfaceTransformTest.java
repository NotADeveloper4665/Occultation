package com.limelight.ui;

import org.junit.Test;
import static org.junit.Assert.*;

public class PortraitSurfaceTransformTest {
    @Test public void landscapeCornersMapToPortraitBufferCorners() {
        PortraitSurfaceTransform.Point topLeft =
                PortraitSurfaceTransform.landscapeToPortrait(0, 0, 1920, 1080);
        PortraitSurfaceTransform.Point topRight =
                PortraitSurfaceTransform.landscapeToPortrait(1920, 0, 1920, 1080);
        PortraitSurfaceTransform.Point bottomLeft =
                PortraitSurfaceTransform.landscapeToPortrait(0, 1080, 1920, 1080);
        PortraitSurfaceTransform.Point bottomRight =
                PortraitSurfaceTransform.landscapeToPortrait(1920, 1080, 1920, 1080);
        assertEquals(0, topLeft.x, 0.001);
        assertEquals(1920, topLeft.y, 0.001);
        assertEquals(0, topRight.y, 0.001);
        assertEquals(1080, bottomLeft.x, 0.001);
        assertEquals(1920, bottomRight.x, 0.001);
        assertEquals(0, bottomRight.y, 0.001);
    }

    @Test public void transformRoundTripsInteriorPoints() {
        PortraitSurfaceTransform.Point portrait =
                PortraitSurfaceTransform.landscapeToPortrait(480, 270, 1920, 1080);
        PortraitSurfaceTransform.Point landscape =
                PortraitSurfaceTransform.portraitToLandscape(portrait.x, portrait.y, 1080, 1920);
        assertEquals(480, landscape.x, 0.001);
        assertEquals(270, landscape.y, 0.001);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsZeroDimensions() {
        PortraitSurfaceTransform.landscapeToPortrait(0, 0, 0, 1080);
    }
}
