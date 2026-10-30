package com.limelight.binding.input;

import org.junit.Test;
import static org.junit.Assert.*;

public class InputModeRouterTest {
    @Test public void nativeTouchOnlyNeverFallsBackToMouse() {
        assertEquals(InputModeRouter.TouchRoute.NATIVE_TOUCH_ONLY,
                InputModeRouter.routeFor(InputModeRouter.NATIVE_TOUCH_ONLY));
        assertTrue(InputModeRouter.consumesTouchWhenNativeUnsupported(InputModeRouter.NATIVE_TOUCH_ONLY));
        assertTrue(InputModeRouter.consumeNativeTouchResult(InputModeRouter.NATIVE_TOUCH_ONLY, false));
    }

    @Test public void legacyMultiTouchRetainsCompatibilityFallback() {
        assertEquals(InputModeRouter.TouchRoute.NATIVE_TOUCH_WITH_MOUSE_FALLBACK,
                InputModeRouter.routeFor(InputModeRouter.MULTI_TOUCH));
        assertFalse(InputModeRouter.consumeNativeTouchResult(InputModeRouter.MULTI_TOUCH, false));
        assertTrue(InputModeRouter.consumeNativeTouchResult(InputModeRouter.MULTI_TOUCH, true));
    }

    @Test public void desktopAndTrackpadModesHaveDistinctRoutes() {
        assertTrue(InputModeRouter.usesDesktopControls(InputModeRouter.REMOTE_DESKTOP));
        assertFalse(InputModeRouter.usesDesktopControls(InputModeRouter.TRACKPAD_NATURAL));
        assertTrue(InputModeRouter.isDisabled(InputModeRouter.DISABLED));
    }

    @Test public void invalidSavedModeFallsBackToMultiTouch() {
        assertEquals(InputModeRouter.MULTI_TOUCH, InputModeRouter.normalize(99));
    }
}
