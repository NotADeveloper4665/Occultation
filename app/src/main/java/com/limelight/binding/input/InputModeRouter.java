package com.limelight.binding.input;

/** Single source of truth for touchscreen-to-stream routing. */
public final class InputModeRouter {
    public static final int MULTI_TOUCH = 0;
    public static final int ABSOLUTE_MOUSE = 1;
    public static final int TRACKPAD_NATURAL = 2;
    public static final int TRACKPAD_GAMING = 3;
    public static final int DISABLED = 4;
    public static final int ABSOLUTE_MOUSE_SWAPPED = 5;
    public static final int REMOTE_DESKTOP = 6;
    public static final int NATIVE_TOUCH_ONLY = 7;

    public enum TouchRoute {
        NATIVE_TOUCH_WITH_MOUSE_FALLBACK, NATIVE_TOUCH_ONLY,
        ABSOLUTE_MOUSE, TRACKPAD, DISABLED, REMOTE_DESKTOP
    }

    private InputModeRouter() {}

    public static int normalize(int mode) {
        return mode >= MULTI_TOUCH && mode <= NATIVE_TOUCH_ONLY ? mode : MULTI_TOUCH;
    }

    public static TouchRoute routeFor(int mode) {
        switch (normalize(mode)) {
            case MULTI_TOUCH: return TouchRoute.NATIVE_TOUCH_WITH_MOUSE_FALLBACK;
            case NATIVE_TOUCH_ONLY: return TouchRoute.NATIVE_TOUCH_ONLY;
            case ABSOLUTE_MOUSE:
            case ABSOLUTE_MOUSE_SWAPPED: return TouchRoute.ABSOLUTE_MOUSE;
            case TRACKPAD_NATURAL:
            case TRACKPAD_GAMING: return TouchRoute.TRACKPAD;
            case REMOTE_DESKTOP: return TouchRoute.REMOTE_DESKTOP;
            case DISABLED:
            default: return TouchRoute.DISABLED;
        }
    }

    public static boolean consumesTouchWhenNativeUnsupported(int mode) {
        return routeFor(mode) == TouchRoute.NATIVE_TOUCH_ONLY;
    }

    public static boolean usesDesktopControls(int mode) {
        return routeFor(mode) == TouchRoute.REMOTE_DESKTOP;
    }

    public static boolean isDisabled(int mode) {
        return routeFor(mode) == TouchRoute.DISABLED;
    }

    /** A successful native event is consumed; only legacy multi-touch may fall back. */
    public static boolean consumeNativeTouchResult(int mode, boolean delivered) {
        return delivered || consumesTouchWhenNativeUnsupported(mode);
    }
}
