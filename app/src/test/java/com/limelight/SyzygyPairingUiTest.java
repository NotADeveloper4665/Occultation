package com.limelight;

import android.app.AlertDialog;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import com.limelight.nvstream.http.ComputerDetails;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, shadows = {com.limelight.shadows.ShadowMoonBridge.class,
        com.limelight.shadows.ShadowGameManager.class})
public class SyzygyPairingUiTest {
    private ActivityController<PcView> controller;
    private PcView activity;

    @Before public void createActivity() {
        TestLogSuppressor.install();
        controller = Robolectric.buildActivity(PcView.class).create();
        activity = controller.get();
    }

    @After public void destroyActivity() {
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        if (dialog != null) dialog.dismiss();
        controller.destroy();
    }

    private AlertDialog showKeyDialog() {
        ReflectionHelpers.callInstanceMethod(activity, "showSyzygyKeyDialog",
                ReflectionHelpers.ClassParameter.from(ComputerDetails.class, new ComputerDetails()));
        return ShadowAlertDialog.getLatestAlertDialog();
    }

    @Test public void advertisedAutoPairingStillOffersPhraseWhenVpnIsUnavailable() {
        ComputerDetails computer = new ComputerDetails();
        computer.syzygyTailsPairing = true;
        ReflectionHelpers.callInstanceMethod(activity, "choosePairingMethod",
                ReflectionHelpers.ClassParameter.from(ComputerDetails.class, computer));
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertTrue(dialog.isShowing());
        assertEquals(activity.getString(R.string.syzygy_pair_action),
                dialog.getListView().getAdapter().getItem(0));
    }

    @Test public void malformedKeyStaysInDialogWithoutStartingPairing() {
        AlertDialog dialog = showKeyDialog();
        EditText input = dialog.findViewById(R.id.syzygyKeyText);
        input.setText("not-a-host-key");
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertTrue(dialog.isShowing());
        assertEquals(activity.getString(R.string.syzygy_key_invalid), input.getError().toString());
    }

    @Test public void dismissedKeyIsClearedAndExcludedFromSavedStateAndAutofill() {
        AlertDialog dialog = showKeyDialog();
        EditText input = dialog.findViewById(R.id.syzygyKeyText);
        assertFalse(input.isSaveEnabled());
        assertEquals(View.IMPORTANT_FOR_AUTOFILL_NO, input.getImportantForAutofill());
        input.setText("abacus abacus abacus abacus abacus abacus");
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("", input.getText().toString());
    }

    @Test public void discoveredHostOffersKeyAndPinMethods() {
        ReflectionHelpers.callInstanceMethod(activity, "choosePairingMethod",
                ReflectionHelpers.ClassParameter.from(ComputerDetails.class, new ComputerDetails()));
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertEquals(3, dialog.getListView().getAdapter().getCount());
        assertEquals(activity.getString(R.string.syzygy_pair_action),
                dialog.getListView().getAdapter().getItem(0).toString());
        dialog.getListView().performItemClick(null, 0, 0);
        assertNotNull(ShadowAlertDialog.getLatestAlertDialog().findViewById(R.id.syzygyKeyText));
    }
}
