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
        android.view.ViewGroup root = (android.view.ViewGroup) ((android.view.ViewGroup) controller.get().findViewById(android.R.id.content)).getChildAt(0);
        android.view.ViewGroup fields = (android.view.ViewGroup) root.getChildAt(1);
        ((android.widget.EditText) fields.getChildAt(0)).setText(""); // Incomplete names must survive rotation.
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

    @Test public void assignAndDeleteControlsAreVisibleAndDeletePersists() {
        org.robolectric.android.controller.ActivityController<OverlayEditorActivity> controller = Robolectric.buildActivity(OverlayEditorActivity.class).setup();
        shadowOf(Looper.getMainLooper()).idle();
        android.view.ViewGroup root = (android.view.ViewGroup) ((android.view.ViewGroup)controller.get().findViewById(android.R.id.content)).getChildAt(0);
        android.widget.Spinner controls = (android.widget.Spinner) root.getChildAt(4);
        controls.setSelection(2); shadowOf(Looper.getMainLooper()).idle();
        android.widget.Button delete = findButton(root, "Delete button");
        assertNotNull(delete); assertTrue(delete.getWidth() > 0);
        assertNotNull(findButton(root, "Assign action"));
        delete.performClick();
        org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle(); findButton(root, "Save").performClick();
        assertEquals(4, new OverlayStore(controller.get()).active().items.size());
        controller.pause().stop().destroy();
    }
    private android.widget.Button findButton(android.view.View view, String text) {
        if (view instanceof android.widget.Button && text.contentEquals(((android.widget.Button)view).getText())) return (android.widget.Button)view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup)view;
            for (int i = 0; i < group.getChildCount(); i++) {
                android.widget.Button result = findButton(group.getChildAt(i), text);
                if (result != null) return result;
            }
        }
        return null;
    }

}
