package com.limelight.interpolation;

import android.app.Activity;
import android.graphics.*;
import android.widget.ImageView;
import androidx.appcompat.app.AlertDialog;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/** Explicit frame-pair integration test; never changes a running stream. */
public final class RifeTestUi {
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private RifeTestUi() { }
    public static void show(Activity activity) {
        new AlertDialog.Builder(activity).setTitle("Test RIFE frame interpolation")
                .setItems(new String[]{"Vulkan GPU · RIFE v4", "NNAPI · RIFE Lite 4.25"},
                        (dialog, which) -> start(activity, which == 0)).setNegativeButton("Close", null).show();
    }
    private static void start(Activity activity, boolean vulkan) {
        if (!RUNNING.compareAndSet(false, true)) {
            new AlertDialog.Builder(activity).setMessage("A RIFE test is already running.").setPositiveButton("OK", null).show(); return;
        }
        AlertDialog progress = new AlertDialog.Builder(activity).setTitle("Testing RIFE")
                .setMessage("Loading the bundled model and generating a 64 × 64 middle frame. Driver compilation can take time.")
                .setCancelable(false).create();
        progress.show();
        new Thread(() -> {
            String report; Bitmap preview = null;
            try {
                final int width = 64, height = 64, size = width * height * 3;
                ByteBuffer first = ByteBuffer.allocateDirect(size), second = ByteBuffer.allocateDirect(size), output = ByteBuffer.allocateDirect(size);
                fill(first, width, height, 12); fill(second, width, height, 28);
                String device; long cold, worst = 0;
                if (vulkan) {
                    try (VulkanRife model = new VulkanRife(activity.getApplicationContext())) {
                        device = model.device(); long begin = System.nanoTime();
                        model.interpolate(first, second, output, width, height, 0.5f); cold = System.nanoTime() - begin;
                        for (int i = 0; i < 3; i++) { begin = System.nanoTime(); model.interpolate(first, second, output, width, height, 0.5f); worst = Math.max(worst, System.nanoTime() - begin); }
                    }
                } else {
                    try (NnapiRifeLite model = new NnapiRifeLite(activity.getApplicationContext(), width, height)) {
                        device = "NNAPI non-CPU requested; GPU/NPU identity unverified"; long begin = System.nanoTime();
                        model.interpolate(first, second, output, 0.5f); cold = System.nanoTime() - begin;
                        for (int i = 0; i < 3; i++) { begin = System.nanoTime(); model.interpolate(first, second, output, 0.5f); worst = Math.max(worst, System.nanoTime() - begin); }
                    }
                }
                preview = preview(first, output, second, width, height);
                report = String.format(java.util.Locale.ROOT,
                        "%s\nDevice: %s\nGenerated middle frame: 64 × 64 RGB\nCold execution: %.2f ms\nWorst of 3 warm executions: %.2f ms\nIncludes execution setup, input conversion and GPU transfers where applicable; excludes model loading.\n\nThis is a tiny functional test, not a 720p/1080p performance result. Frame generation is not connected to stream rendering.",
                        vulkan ? "RIFE v4 · ncnn Vulkan GPU" : "RIFE Lite 4.25 · strict NNAPI", device, cold / 1000000d, worst / 1000000d);
            } catch (Exception | LinkageError error) {
                report = (vulkan ? "Vulkan RIFE unavailable" : "RIFE Lite NNAPI unsupported or unavailable") +
                        "\n" + error.getClass().getSimpleName() + ": " + error.getMessage() +
                        "\n\nNo CPU retry was attempted. RIFE Lite contains operations such as GridSample that this NNAPI provider may not support. Failure does not mean the phone has no AI accelerator.";
            } finally { RUNNING.set(false); }
            final String text = report; final Bitmap image = preview;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) { if (image != null) image.recycle(); return; }
                progress.dismiss();
                AlertDialog.Builder result = new AlertDialog.Builder(activity).setTitle("RIFE interpolation result").setMessage(text)
                        .setPositiveButton("Copy report", (d, w) -> {
                            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("RIFE interpolation report", text));
                        }).setNegativeButton("Close", null);
                if (image != null) {
                    ImageView view = new ImageView(activity); view.setImageBitmap(image); view.setAdjustViewBounds(true);
                    view.setMinimumHeight((int)(120 * activity.getResources().getDisplayMetrics().density));
                    result.setView(view);
                }
                result.show();
            });
        }, "RifeFramePairTest").start();
    }
    private static void fill(ByteBuffer buffer, int width, int height, int boxX) {
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            boolean box = x >= boxX && x < boxX + 20 && y >= 20 && y < 44;
            int pos = (y * width + x) * 3;
            buffer.put(pos, (byte)(box ? 210 : 24)); buffer.put(pos + 1, (byte)(box ? 140 : 16)); buffer.put(pos + 2, (byte)(box ? 255 : 42));
        }
    }
    private static Bitmap preview(ByteBuffer first, ByteBuffer middle, ByteBuffer last, int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width * 3, height + 16, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap); canvas.drawColor(Color.rgb(18, 10, 30));
        ByteBuffer[] frames = {first, middle, last};
        for (int frame = 0; frame < 3; frame++) for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int pos = (y * width + x) * 3; ByteBuffer buffer = frames[frame];
            bitmap.setPixel(frame * width + x, y, Color.rgb(buffer.get(pos) & 255, buffer.get(pos + 1) & 255, buffer.get(pos + 2) & 255));
        }
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); paint.setColor(Color.rgb(220, 190, 255)); paint.setTextSize(9);
        canvas.drawText("First", 3, height + 11, paint); canvas.drawText("Generated", width + 3, height + 11, paint); canvas.drawText("Second", width * 2 + 3, height + 11, paint);
        return bitmap;
    }
}
