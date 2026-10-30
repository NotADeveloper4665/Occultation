package com.limelight.ui;

import org.junit.Test;
import static org.junit.Assert.*;

public class DesktopControlsVisibilityTest {
    @Test
    public void hidesControlsBeforeFirstVideoFrame() {
        assertFalse(DesktopControlsVisibility.shouldShow(true, false));
    }

    @Test
    public void showsControlsAfterFirstVideoFrameInRemoteDesktopMode() {
        assertTrue(DesktopControlsVisibility.shouldShow(true, true));
    }

    @Test
    public void keepsControlsHiddenOutsideRemoteDesktopMode() {
        assertFalse(DesktopControlsVisibility.shouldShow(false, false));
        assertFalse(DesktopControlsVisibility.shouldShow(false, true));
    }
}
