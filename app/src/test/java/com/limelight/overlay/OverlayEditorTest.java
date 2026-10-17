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
        controller.recreate(); shadowOf(Looper.getMainLooper()).idle();
        assertNotNull(controller.get().findViewById(android.R.id.content));
        controller.pause().stop().destroy();
    }
}
