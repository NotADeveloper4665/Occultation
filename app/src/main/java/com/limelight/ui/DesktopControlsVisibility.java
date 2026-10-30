package com.limelight.ui;

/** Pure visibility policy for the remote-desktop touch overlay. */
public final class DesktopControlsVisibility {
    private DesktopControlsVisibility() {}

    public static boolean shouldShow(boolean remoteDesktopMode, boolean firstVideoFrameRendered) {
        return remoteDesktopMode && firstVideoFrameRendered;
    }
}
