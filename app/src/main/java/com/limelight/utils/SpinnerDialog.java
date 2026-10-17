package com.limelight.utils;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import com.limelight.R;
import java.util.ArrayList;

/** Shared loading surface. All view and registry access stays on the main thread. */
public class SpinnerDialog implements Runnable, DialogInterface.OnCancelListener {
    private final Activity activity;
    private final String title;
    private final boolean finish;
    private volatile String message;
    private volatile boolean dismissed;
    private Dialog progress;
    private static final ArrayList<SpinnerDialog> rundownDialogs = new ArrayList<>();

    private SpinnerDialog(Activity activity, String title, String message, boolean finish) {
        this.activity = activity;
        this.title = title;
        this.message = message;
        this.finish = finish;
    }

    public static SpinnerDialog displayDialog(Activity activity, String title, String message, boolean finish) {
        SpinnerDialog spinner = new SpinnerDialog(activity, title, message, finish);
        activity.runOnUiThread(spinner);
        return spinner;
    }

    public static void closeDialogs(Activity activity) {
        activity.runOnUiThread(() -> {
            for (SpinnerDialog dialog : new ArrayList<>(rundownDialogs)) {
                if (dialog.activity == activity) dialog.dismiss();
            }
        });
    }

    public void dismiss() {
        dismissed = true;
        activity.runOnUiThread(() -> {
            rundownDialogs.remove(this);
            if (progress != null && progress.isShowing()) progress.dismiss();
        });
    }

    public void setMessage(String message) {
        this.message = message;
        activity.runOnUiThread(() -> {
            if (!dismissed && progress != null) {
                ((TextView) progress.findViewById(R.id.loading_message)).setText(this.message);
            }
        });
    }

    @Override public void run() {
        if (dismissed || progress != null || activity.isFinishing() || activity.isDestroyed()) return;
        progress = new Dialog(activity);
        progress.requestWindowFeature(Window.FEATURE_NO_TITLE);
        progress.setContentView(R.layout.dialog_loading);
        ((TextView) progress.findViewById(R.id.loading_title)).setText(title);
        ((TextView) progress.findViewById(R.id.loading_message)).setText(message);
        progress.setCancelable(finish);
        progress.setCanceledOnTouchOutside(false);
        progress.setOnCancelListener(this);
        progress.setOnDismissListener(dialog -> rundownDialogs.remove(this));
        Window window = progress.getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        progress.show();
        int margin = Math.round(32 * activity.getResources().getDisplayMetrics().density);
        int maxWidth = Math.round(480 * activity.getResources().getDisplayMetrics().density);
        int available = activity.getResources().getDisplayMetrics().widthPixels - margin;
        window.setLayout(Math.min(maxWidth, Math.max(1, available)), WindowManager.LayoutParams.WRAP_CONTENT);
        rundownDialogs.add(this);
    }

    @Override public void onCancel(DialogInterface dialog) {
        dismissed = true;
        rundownDialogs.remove(this);
        if (finish) activity.finish();
    }
}
