package com.limelight.utils;

import com.limelight.nvstream.http.NvApp;
import java.util.List;

/** Resolve the advertised Desktop app without guessing IDs or quitting another session. */
public final class DesktopSelection {
    private DesktopSelection() {}

    public static NvApp find(List<NvApp> apps, int runningAppId) {
        NvApp desktop = null;
        for (NvApp app : apps) {
            if (app.isInitialized() && app.getAppId() > 0 &&
                    "Desktop".equalsIgnoreCase(app.getAppName().trim())) {
                if (desktop != null) return null;
                desktop = app;
            }
        }
        return desktop != null && (runningAppId == 0 || runningAppId == desktop.getAppId())
                ? desktop : null;
    }
}
