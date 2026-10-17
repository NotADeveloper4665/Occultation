package com.limelight.overlay;

import android.os.Looper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk = 33)
public class OverlayEditorTest {
    @Test public void opensOfflineEditorAndRestoresDraftAcrossRecreation() {
        org.robolectric.android.controller.ActivityController<OverlayEditorActivity> controller = Robolectric.buildActivity(OverlayEditorActivity.class).setup();
        shadowOf(Looper.getMainLooper()).idle();
        assertNotNull(controller.get().findViewById(android.R.id.content));
        com.limelight.ui.DesktopControlsView preview = findPreview(controller.get().findViewById(android.R.id.content));
        assertNotNull(preview); assertTrue(preview.getHeight() > 0);
        assertEquals(16f / 9f, preview.getWidth() / (float)preview.getHeight(), .03f);
        controller.recreate(); shadowOf(Looper.getMainLooper()).idle();
        assertNotNull(controller.get().findViewById(android.R.id.content));
        controller.pause().stop().destroy();
    }
    private com.limelight.ui.DesktopControlsView findPreview(android.view.View view) {
        if (view instanceof com.limelight.ui.DesktopControlsView) return (com.limelight.ui.DesktopControlsView) view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                com.limelight.ui.DesktopControlsView found = findPreview(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

}
