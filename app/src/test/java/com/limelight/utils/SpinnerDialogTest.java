package com.limelight.utils;

import android.app.Activity;
import android.app.Dialog;
import android.os.Looper;
import android.widget.TextView;
import com.limelight.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class) @Config(sdk = 33)
public class SpinnerDialogTest {
    @Test public void liveStageUpdatesAndDismissIsIdempotent() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        SpinnerDialog spinner = SpinnerDialog.displayDialog(activity, "Connecting", "Starting", true);
        Dialog dialog = ShadowDialog.getLatestDialog();
        assertEquals("Connecting", ((TextView)dialog.findViewById(R.id.loading_title)).getText().toString());
        spinner.setMessage("Starting video");
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("Starting video", ((TextView)dialog.findViewById(R.id.loading_message)).getText().toString());
        spinner.dismiss(); spinner.dismiss(); spinner.setMessage("Late callback"); spinner.run();
        shadowOf(Looper.getMainLooper()).idle();
        assertFalse(dialog.isShowing()); assertFalse(activity.isFinishing());
        activity.finish();
    }
    @Test public void connectionCancellationFinishesActivity() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        SpinnerDialog.displayDialog(activity, "Connecting", "Starting", true);
        ShadowDialog.getLatestDialog().cancel();
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(activity.isFinishing());
    }
    @Test public void closeOnlyDismissesDialogsOwnedByActivity() {
        Activity first = Robolectric.buildActivity(Activity.class).setup().get();
        Activity second = Robolectric.buildActivity(Activity.class).setup().get();
        SpinnerDialog.displayDialog(first, "Apps", "Refreshing", false);
        Dialog firstDialog = ShadowDialog.getLatestDialog();
        SpinnerDialog.displayDialog(second, "Connecting", "Starting", true);
        Dialog secondDialog = ShadowDialog.getLatestDialog();
        SpinnerDialog.closeDialogs(first);
        assertFalse(firstDialog.isShowing()); assertTrue(secondDialog.isShowing());
        SpinnerDialog.closeDialogs(second); first.finish(); second.finish();
    }
    @Test public void finishedActivityDoesNotOpenDialog() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Dialog before = ShadowDialog.getLatestDialog();
        activity.finish();
        SpinnerDialog spinner = SpinnerDialog.displayDialog(activity, "Connecting", "Starting", true);
        spinner.setMessage("Late callback"); spinner.dismiss();
        assertSame(before, ShadowDialog.getLatestDialog());
    }
}
