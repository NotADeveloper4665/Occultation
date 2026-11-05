package com.limelight.binding.video;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.RequiresApi;
import androidx.core.util.Consumer;

import java.util.Locale;

/** Hardware-selected front-buffer strategy dispatcher. */
public final class FrontBufferRenderer implements AutoCloseable {
    private static final String TAG = "OccultationFrontBuffer";
    private final Surface displaySurface;
    private final Surface decoderSurface;
    private final Runnable closeAction;
    private final boolean singleBufferActive;
    private final boolean portraitTransformActive;

    @RequiresApi(33)
    private static final class HardwareBackendResult {
        final Surface decoderSurface;
        final Runnable closeAction;
        HardwareBackendResult(HardwareBufferFrontBufferRenderer backend) {
            decoderSurface = backend.decoderSurface();
            closeAction = backend::close;
        }
    }

    public FrontBufferRenderer(Surface displaySurface, Object parentSurfaceControl,
                               int width, int height, Consumer<String> status) {
        if (Build.VERSION.SDK_INT < 24) {
            throw new IllegalStateException("Front-buffer rendering requires Android 7.0 or newer");
        }
        if (displaySurface == null || !displaySurface.isValid()) {
            throw new IllegalArgumentException("Front-buffer display Surface is invalid");
        }
        this.displaySurface = displaySurface;

        boolean mediaTek = isMediaTekDevice();
        String renderer = mediaTek ? "mediatek" : probeRenderer(displaySurface).toLowerCase(Locale.ROOT);
        if (mediaTek) {
            if (Build.VERSION.SDK_INT < 33) {
                throw new IllegalStateException("MediaTek portrait producer requires Android 13 (API 33)");
            }
            HardwareBackendResult backend = createHardwareBackend(
                    displaySurface, parentSurfaceControl, width, height, true, status);
            decoderSurface = backend.decoderSurface;
            closeAction = backend.closeAction;
            singleBufferActive = false;
            portraitTransformActive = true;
            return;
        }

        if (renderer.contains("adreno")) {
            AdrenoEglSingleBufferRenderer backend =
                    new AdrenoEglSingleBufferRenderer(displaySurface, width, height, status);
            decoderSurface = backend.decoderSurface();
            closeAction = backend::close;
            singleBufferActive = backend.isSingleBufferActive();
            portraitTransformActive = false;
            return;
        }

        if (renderer.contains("mali") || renderer.contains("arm")) {
            if (Build.VERSION.SDK_INT < 33) {
                throw new IllegalStateException("Mali HardwareBuffer path requires Android 13 (API 33)");
            }
            HardwareBackendResult backend = createHardwareBackend(
                    displaySurface, parentSurfaceControl, width, height, false, status);
            decoderSurface = backend.decoderSurface;
            closeAction = backend.closeAction;
            singleBufferActive = false;
            portraitTransformActive = false;
            if (status != null) {
                status.accept(renderer.contains("mali-g57")
                        ? "Mali-G57 using HardwareBuffer SurfaceControl fallback"
                        : "Mali using HardwareBuffer SurfaceControl path");
            }
            return;
        }

        throw new IllegalStateException("Unsupported GPU for front-buffer rendering: " + renderer);
    }

    @RequiresApi(33)
    private static HardwareBackendResult createHardwareBackend(
            Surface surface, Object parent, int width, int height, boolean portrait,
            Consumer<String> status) {
        if (Build.VERSION.SDK_INT < 33 || parent == null) {
            throw new IllegalStateException("HardwareBuffer SurfaceControl path requires API 33 and a parent SurfaceControl");
        }
        return new HardwareBackendResult(
                new HardwareBufferFrontBufferRenderer(surface, parent, width, height, portrait, status));
    }

    private static boolean isMediaTekDevice() {
        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase(Locale.ROOT);
        String hardware = Build.HARDWARE == null ? "" : Build.HARDWARE.toLowerCase(Locale.ROOT);
        String board = Build.BOARD == null ? "" : Build.BOARD.toLowerCase(Locale.ROOT);
        return manufacturer.contains("mediatek") || hardware.startsWith("mt") || board.startsWith("mt");
    }

    private static String probeRenderer(Surface surface) {
        EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (display == EGL14.EGL_NO_DISPLAY) return "";
        EGLContext context = EGL14.EGL_NO_CONTEXT;
        EGLSurface window = EGL14.EGL_NO_SURFACE;
        try {
            int[] versions = new int[2];
            if (!EGL14.eglInitialize(display, versions, 0, versions, 1)) return "";
            int[] configAttributes = {
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_NONE
            };
            EGLConfig[] configs = new EGLConfig[1];
            int[] count = new int[1];
            if (!EGL14.eglChooseConfig(display, configAttributes, 0, configs, 0, 1, count, 0) || count[0] == 0) return "";
            context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
                    new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
            if (context == EGL14.EGL_NO_CONTEXT) return "";
            window = EGL14.eglCreateWindowSurface(display, configs[0], surface,
                    new int[]{EGL14.EGL_NONE}, 0);
            if (window == EGL14.EGL_NO_SURFACE || !EGL14.eglMakeCurrent(display, window, window, context)) return "";
            String vendor = GLES20.glGetString(GLES20.GL_VENDOR);
            String name = GLES20.glGetString(GLES20.GL_RENDERER);
            return (vendor == null ? "" : vendor) + " " + (name == null ? "" : name);
        } catch (RuntimeException error) {
            Log.w(TAG, "GPU renderer probe failed", error);
            return "";
        } finally {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            if (window != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, window);
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context);
            EGL14.eglTerminate(display);
        }
    }

    public Surface decoderSurface() { return decoderSurface; }
    public Surface displaySurface() { return displaySurface; }
    public boolean isSingleBufferActive() { return singleBufferActive; }
    public boolean isPortraitTransformActive() { return portraitTransformActive; }

    @Override
    public void close() {
        closeAction.run();
    }
}
