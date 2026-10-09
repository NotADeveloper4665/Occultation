package com.limelight.utils;

import com.limelight.nvstream.http.NvApp;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class DesktopSelectionTest {
    @Test public void selectsAdvertisedDesktopWithoutAssumingAppId() {
        NvApp desktop = new NvApp("desktop", "", 42, false);
        assertSame(desktop, DesktopSelection.find(Arrays.asList(
                new NvApp("Game", "", 1, false), desktop), 0));
        assertSame(desktop, DesktopSelection.find(Collections.singletonList(desktop), 42));
    }
    @Test public void preservesAnotherRunningApp() {
        assertNull(DesktopSelection.find(Collections.singletonList(
                new NvApp("Desktop", "", 42, false)), 7));
    }
    @Test public void rejectsMissingInvalidAndAmbiguousDesktop() {
        assertNull(DesktopSelection.find(Collections.emptyList(), 0));
        assertNull(DesktopSelection.find(Collections.singletonList(new NvApp("Desktop")), 0));
        assertNull(DesktopSelection.find(Arrays.asList(new NvApp("Desktop", "", 1, false),
                new NvApp("Desktop", "", 2, false)), 0));
        assertNull(DesktopSelection.find(Collections.singletonList(
                new NvApp("Steam Big Picture", "", 3, false)), 0));
    }
}
