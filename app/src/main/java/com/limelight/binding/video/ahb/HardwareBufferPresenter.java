package com.limelight.binding.video.ahb;

import android.annotation.SuppressLint;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.hardware.DataSpace;
import android.hardware.SyncFence;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;
import androidx.annotation.RequiresApi;
import java.time.Duration;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ExecutionException;

/** API 33 acquire fences + PRIVATE decoder buffers. All Vulkan calls own one thread. */
@RequiresApi(33)
public final class HardwareBufferPresenter implements AutoCloseable {
    private final HandlerThread thread = new HandlerThread("VulkanVideoPresent");
    private final Handler handler;
    private ImageReader reader;
    private long nativeHandle;
    private volatile boolean closed, failed;
    private final java.util.function.Consumer<String> failure;
    private final int fallbackModel, fallbackRange, scaleMode;

    public HardwareBufferPresenter(Surface display, int width, int height, int colorModel, int colorRange, int scaleMode, java.util.function.Consumer<String> failure) {
        if (android.os.Build.VERSION.SDK_INT < 33) throw new IllegalStateException("AHB Vulkan output requires Android 13+");
        this.failure = failure; fallbackModel = colorModel; fallbackRange = colorRange; this.scaleMode = scaleMode;
        thread.start(); handler = new Handler(thread.getLooper());
        FutureTask<Void> initialize = new FutureTask<>(() -> {
            System.loadLibrary("occultation-video-display");
            nativeHandle = nativeOpen(display);
            reader = ImageReader.newInstance(width, height, ImageFormat.PRIVATE, 3, HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE);
            reader.setOnImageAvailableListener(this::presentLatest, handler);
            return null;
        });
        handler.post(initialize);
        try { initialize.get(); }
        catch (InterruptedException error) {
            Thread.currentThread().interrupt(); close(); throw new IllegalStateException("AHB display initialization interrupted", error);
        } catch (ExecutionException error) { close(); throw new IllegalStateException("AHB Vulkan display unavailable", error.getCause()); }
    }
    public Surface decoderSurface() { return reader.getSurface(); }
    // Image.getDataSpace() has a broader IntDef than these bitfield extractors.
    // The extractors safely mask standard/range/transfer bits, including UNKNOWN.
    @SuppressLint("WrongConstant")
    private void presentLatest(ImageReader source) {
        try (Image image = source.acquireLatestImage()) {
            if (image == null || closed || failed) return;
            try (SyncFence fence = image.getFence()) {
                // A missing fence is already ready. A late producer frame is discarded.
                if (fence.isValid() && !fence.await(Duration.ofMillis(5))) return;
            }
            Rect crop = image.getCropRect();
            try (HardwareBuffer buffer = image.getHardwareBuffer()) {
                if (buffer == null) throw new IllegalStateException("Decoder returned no HardwareBuffer");
                // nativePresent waits for sampling/foreign ownership release before Image.close().
                int space = image.getDataSpace();
                int transfer = DataSpace.getTransfer(space);
                if (transfer == DataSpace.TRANSFER_ST2084 || transfer == DataSpace.TRANSFER_HLG)
                    throw new IllegalStateException("HDR transfer requires standard HDR output");
                int model = fallbackModel, range = fallbackRange;
                int standard = DataSpace.getStandard(space);
                if (standard == DataSpace.STANDARD_BT709) model = 1;
                else if (standard == DataSpace.STANDARD_BT2020) model = 2;
                else if (standard == DataSpace.STANDARD_BT601_525 || standard == DataSpace.STANDARD_BT601_625) model = 0;
                int dataRange = DataSpace.getRange(space);
                if (dataRange == DataSpace.RANGE_FULL) range = 1;
                else if (dataRange == DataSpace.RANGE_LIMITED) range = 0;
                nativePresent(nativeHandle, buffer, crop.left, crop.top, crop.right, crop.bottom, model, range, scaleMode);
            }
        } catch (Exception | LinkageError error) {
            if (!closed && !failed) { failed = true; failure.accept(error.toString()); }
        }
    }
    @Override public void close() {
        if (closed) return; closed = true;
        // Cleanup stays behind any in-flight GPU work on the owner thread. Never
        // release the ImageReader or its Images merely because a timeout elapsed.
        FutureTask<Void> cleanup = new FutureTask<>(() -> {
            if (reader != null) { reader.setOnImageAvailableListener(null, null); reader.close(); reader = null; }
            if (nativeHandle != 0) { nativeClose(nativeHandle); nativeHandle = 0; }
            thread.quitSafely(); return null;
        });
        handler.post(cleanup);
        if (Thread.currentThread() != thread) {
            boolean interrupted = false;
            for (;;) {
                try { cleanup.get(); break; }
                catch (InterruptedException error) { interrupted = true; }
                catch (ExecutionException error) { break; }
            }
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
    private static native long nativeOpen(Surface target);
    private static native void nativePresent(long handle, HardwareBuffer buffer, int left, int top, int right, int bottom, int model, int range, int scaleMode);
    private static native void nativeClose(long handle);
}
