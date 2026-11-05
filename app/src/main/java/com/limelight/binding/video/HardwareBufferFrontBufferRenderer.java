package com.limelight.binding.video;

import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.HardwareBuffer;
import android.hardware.SyncFence;
import android.opengl.EGL14;
import android.opengl.EGL15;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.EGLSync;
import android.opengl.EGLExt;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceControl;

import androidx.annotation.RequiresApi;
import androidx.core.util.Consumer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * API 33+ SurfaceControl HardwareBuffer presenter for Mali fallback and MediaTek
 * portrait-producer mode. The EGL window surface stays current; this path never
 * calls eglSwapBuffers. Video frames are rendered into composer-overlay buffers
 * and attached with a presentation fence and a release callback.
 */
@RequiresApi(33)
public final class HardwareBufferFrontBufferRenderer implements AutoCloseable {
    private static final String TAG = "OccultationHardwareBuffer";
    private static final int EGL_SYNC_NATIVE_FENCE_ANDROID = 0x3144;
    private static final int BUFFER_COUNT = 3;
    private static final int EGL_MUTABLE_RENDER_BUFFER_KHR = 0x30A8;

    private static final float[] QUAD = {
            -1f, -1f, 0f, 1f,
             1f, -1f, 1f, 1f,
            -1f,  1f, 0f, 0f,
             1f,  1f, 1f, 0f
    };

    private static final class BufferSlot {
        final HardwareBuffer buffer;
        final long fbo;
        boolean inUse;

        BufferSlot(HardwareBuffer buffer, long fbo) {
            this.buffer = buffer;
            this.fbo = fbo;
        }
    }

    private final HandlerThread thread = new HandlerThread("OccultationHardwareBufferGL");
    private final Handler handler;
    private final ExecutorService releaseWorkers = Executors.newSingleThreadExecutor(r -> {
        Thread worker = new Thread(r, "OccultationBufferRelease");
        worker.setDaemon(true);
        return worker;
    });
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object poolLock = new Object();
    private final List<BufferSlot> buffers = new ArrayList<>();
    private final Surface displaySurface;
    private final SurfaceControl parentSurfaceControl;
    private final int streamWidth;
    private final int streamHeight;
    private final boolean portraitProducer;
    private final Consumer<String> status;

    private int bufferWidth;
    private int bufferHeight;
    private EGLDisplay display = EGL14.EGL_NO_DISPLAY;
    private EGLContext context = EGL14.EGL_NO_CONTEXT;
    private EGLSurface windowSurface = EGL14.EGL_NO_SURFACE;
    private SurfaceTexture texture;
    private Surface decoderSurface;
    private SurfaceControl childSurfaceControl;
    private int textureId;
    private int program;
    private int positionLocation;
    private int texCoordLocation;
    private int matrixLocation;
    private int samplerLocation;
    private int rotateLocation;
    private boolean nativeFenceSyncAvailable;
    private FloatBuffer vertices;
    private final float[] textureMatrix = new float[16];

    public HardwareBufferFrontBufferRenderer(Surface displaySurface, Object parentControl,
                                              int streamWidth, int streamHeight,
                                              boolean portraitProducer, Consumer<String> status) {
        if (displaySurface == null || !displaySurface.isValid() || !(parentControl instanceof SurfaceControl)) {
            throw new IllegalArgumentException("HardwareBuffer path requires a valid Surface and parent SurfaceControl");
        }
        if (streamWidth <= 0 || streamHeight <= 0 || streamWidth < streamHeight) {
            throw new IllegalArgumentException("HardwareBuffer path requires landscape stream dimensions");
        }
        this.displaySurface = displaySurface;
        this.parentSurfaceControl = (SurfaceControl) parentControl;
        this.streamWidth = streamWidth;
        this.streamHeight = streamHeight;
        this.portraitProducer = portraitProducer;
        this.status = status == null ? message -> { } : status;
        this.bufferWidth = portraitProducer ? streamHeight : streamWidth;
        this.bufferHeight = portraitProducer ? streamWidth : streamHeight;

        thread.start();
        handler = new Handler(thread.getLooper());
        FutureTask<Void> initialize = new FutureTask<>(() -> {
            initializeOnGlThread();
            return null;
        });
        handler.post(initialize);
        try {
            initialize.get(10, TimeUnit.SECONDS);
        } catch (Exception error) {
            close();
            Throwable cause = error.getCause() != null ? error.getCause() : error;
            throw new IllegalStateException("HardwareBuffer/SurfaceControl presenter unavailable", cause);
        }
    }

    public Surface decoderSurface() {
        if (closed.get() || decoderSurface == null) {
            throw new IllegalStateException("HardwareBuffer decoder Surface is unavailable");
        }
        return decoderSurface;
    }

    public boolean isPortraitTransformActive() {
        return portraitProducer;
    }

    private void initializeOnGlThread() {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (display == EGL14.EGL_NO_DISPLAY) throw new IllegalStateException("eglGetDisplay failed");
        int[] versions = new int[2];
        if (!EGL14.eglInitialize(display, versions, 0, versions, 1)) {
            throw new IllegalStateException("eglInitialize failed");
        }
        String extensions = EGL14.eglQueryString(display, EGL14.EGL_EXTENSIONS);
        if (extensions == null || !extensions.contains("EGL_KHR_image_base")) {
            throw new IllegalStateException("EGL_KHR_image_base is unavailable");
        }
        nativeFenceSyncAvailable = extensions.contains("EGL_ANDROID_native_fence_sync");

        int[] configAttributes = {
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] configCount = new int[1];
        if (!EGL14.eglChooseConfig(display, configAttributes, 0, configs, 0, 1, configCount, 0) ||
                configCount[0] == 0) {
            throw new IllegalStateException("No compatible EGL config for HardwareBuffer path");
        }
        int[] contextAttributes = { EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE };
        context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
                contextAttributes, 0);
        if (context == EGL14.EGL_NO_CONTEXT) throw new IllegalStateException("eglCreateContext failed");

        // Keep the original window surface current for drivers that require it.
        // This path renders to HardwareBuffer FBOs and deliberately never swaps this surface.
        windowSurface = EGL14.eglCreateWindowSurface(display, configs[0], displaySurface,
                new int[]{EGL14.EGL_NONE}, 0);
        if (windowSurface == EGL14.EGL_NO_SURFACE) {
            throw new IllegalStateException("eglCreateWindowSurface failed for HardwareBuffer path");
        }
        if (!EGL14.eglMakeCurrent(display, windowSurface, windowSurface, context)) {
            throw new IllegalStateException("eglMakeCurrent failed for HardwareBuffer path");
        }

        createExternalTexture();
        createProgram();
        createChildSurface();
        allocateBuffers();

        texture = new SurfaceTexture(textureId);
        texture.setDefaultBufferSize(streamWidth, streamHeight);
        texture.setOnFrameAvailableListener(this::onFrameAvailable, handler);
        decoderSurface = new Surface(texture);
        status.accept(portraitProducer
                ? "HardwareBuffer SurfaceControl path active (portrait producer)"
                : "HardwareBuffer SurfaceControl path active");
    }

    private void createChildSurface() {
        childSurfaceControl = new SurfaceControl.Builder()
                .setName("Occultation-FrontBuffer")
                .setParent(parentSurfaceControl)
                .build();
        SurfaceControl.Transaction transaction = new SurfaceControl.Transaction();
        try {
            transaction.setBufferSize(childSurfaceControl, bufferWidth, bufferHeight);
            transaction.setPosition(childSurfaceControl, 0f, 0f);
            transaction.setWindowCrop(childSurfaceControl, new Rect(0, 0, bufferWidth, bufferHeight));
            transaction.setLayer(childSurfaceControl, Integer.MAX_VALUE - 1);
            transaction.show(childSurfaceControl);
            transaction.apply();
        } finally {
            transaction.close();
        }
    }

    private void allocateBuffers() {
        long usage = HardwareBuffer.USAGE_COMPOSER_OVERLAY |
                HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE |
                HardwareBuffer.USAGE_GPU_COLOR_OUTPUT;
        for (int i = 0; i < BUFFER_COUNT; i++) {
            HardwareBuffer buffer = HardwareBuffer.create(bufferWidth, bufferHeight,
                    HardwareBuffer.RGBA_8888, 1, usage);
            if (buffer == null) throw new IllegalStateException("HardwareBuffer allocation returned null");
            long fbo = FrontBufferHardwareBridge.nativeCreateFbo(buffer);
            if (fbo == 0) {
                buffer.close();
                throw new IllegalStateException("Could not import HardwareBuffer as EGLImage/FBO");
            }
            buffers.add(new BufferSlot(buffer, fbo));
        }
    }

    private void createExternalTexture() {
        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0);
        textureId = ids[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
    }

    private void createProgram() {
        String vertex = "attribute vec4 aPosition; attribute vec4 aTexCoord; uniform mat4 uTexMatrix; " +
                "varying vec2 vTexCoord; void main(){ gl_Position=aPosition; vTexCoord=(uTexMatrix*aTexCoord).xy; }";
        String fragment = "#extension GL_OES_EGL_image_external : require\nprecision mediump float; " +
                "uniform samplerExternalOES sTexture; uniform int uRotatePortrait; varying vec2 vTexCoord; " +
                "void main(){ vec2 uv=vTexCoord; if(uRotatePortrait==1) uv=vec2(1.0-vTexCoord.y,vTexCoord.x); " +
                "gl_FragColor=texture2D(sTexture,uv); }";
        int vs = compileShader(GLES20.GL_VERTEX_SHADER, vertex);
        int fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragment);
        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vs);
        GLES20.glAttachShader(program, fs);
        GLES20.glLinkProgram(program);
        GLES20.glDeleteShader(vs);
        GLES20.glDeleteShader(fs);
        int[] result = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, result, 0);
        if (result[0] == 0) {
            String log = GLES20.glGetProgramInfoLog(program);
            throw new IllegalStateException("HardwareBuffer shader link failed: " + log);
        }
        positionLocation = GLES20.glGetAttribLocation(program, "aPosition");
        texCoordLocation = GLES20.glGetAttribLocation(program, "aTexCoord");
        matrixLocation = GLES20.glGetUniformLocation(program, "uTexMatrix");
        samplerLocation = GLES20.glGetUniformLocation(program, "sTexture");
        rotateLocation = GLES20.glGetUniformLocation(program, "uRotatePortrait");
        vertices = ByteBuffer.allocateDirect(QUAD.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        vertices.put(QUAD).position(0);
    }

    private static int compileShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] result = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, result, 0);
        if (result[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new IllegalStateException("HardwareBuffer shader compile failed: " + log);
        }
        return shader;
    }

    private BufferSlot acquireBuffer() {
        synchronized (poolLock) {
            for (BufferSlot slot : buffers) {
                if (!slot.inUse) {
                    slot.inUse = true;
                    return slot;
                }
            }
        }
        return null;
    }

    private void releaseBuffer(BufferSlot slot, SyncFence fence) {
        Runnable releaseTask = () -> {
            try {
                if (fence != null && fence.isValid()) fence.awaitForever();
            } catch (RuntimeException error) {
                Log.w(TAG, "Release fence wait failed", error);
            } finally {
                if (fence != null) fence.close();
                synchronized (poolLock) {
                    slot.inUse = false;
                    poolLock.notifyAll();
                }
            }
        };
        try {
            releaseWorkers.execute(releaseTask);
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            releaseTask.run();
        }
    }

    private SyncFence createPresentationFence() {
        if (!nativeFenceSyncAvailable) {
            // Correctness takes priority over asynchronous fences on drivers
            // that do not expose EGL_ANDROID_native_fence_sync.
            GLES20.glFinish();
            return null;
        }
        try {
            EGLSync sync = EGL15.eglCreateSync(display, EGL_SYNC_NATIVE_FENCE_ANDROID,
                    new long[]{EGL14.EGL_NONE}, 0);
            if (sync == null) {
                GLES20.glFinish();
                return null;
            }
            GLES20.glFlush();
            SyncFence fence = EGLExt.eglDupNativeFenceFDANDROID(display, sync);
            EGL15.eglDestroySync(display, sync);
            if (fence == null || !fence.isValid()) {
                if (fence != null) fence.close();
                GLES20.glFinish();
                return null;
            }
            return fence;
        } catch (RuntimeException error) {
            GLES20.glFinish();
            return null;
        }
    }

    private void onFrameAvailable(SurfaceTexture source) {
        if (closed.get()) return;
        BufferSlot slot = acquireBuffer();
        if (slot == null) return; // Drop rather than queue frames behind the display.
        try {
            source.updateTexImage();
            source.getTransformMatrix(textureMatrix);
            if (!FrontBufferHardwareBridge.nativeBindFbo(slot.fbo)) {
                throw new IllegalStateException("Could not bind HardwareBuffer FBO");
            }
            GLES20.glViewport(0, 0, bufferWidth, bufferHeight);
            GLES20.glClearColor(0f, 0f, 0f, 1f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glUseProgram(program);
            vertices.position(0);
            GLES20.glEnableVertexAttribArray(positionLocation);
            GLES20.glVertexAttribPointer(positionLocation, 2, GLES20.GL_FLOAT, false, 16, vertices);
            vertices.position(2);
            GLES20.glEnableVertexAttribArray(texCoordLocation);
            GLES20.glVertexAttribPointer(texCoordLocation, 2, GLES20.GL_FLOAT, false, 16, vertices);
            GLES20.glUniformMatrix4fv(matrixLocation, 1, false, textureMatrix, 0);
            GLES20.glUniform1i(rotateLocation, portraitProducer ? 1 : 0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
            GLES20.glUniform1i(samplerLocation, 0);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            FrontBufferHardwareBridge.nativeUnbindFbo();

            SyncFence acquireFence = createPresentationFence();
            SurfaceControl.Transaction transaction = new SurfaceControl.Transaction();
            try {
                transaction.setBuffer(childSurfaceControl, slot.buffer, acquireFence,
                        releaseFence -> releaseBuffer(slot, releaseFence));
                transaction.apply();
            } finally {
                transaction.close();
                if (acquireFence != null) acquireFence.close();
            }
        } catch (Exception error) {
            FrontBufferHardwareBridge.nativeUnbindFbo();
            synchronized (poolLock) {
                slot.inUse = false;
                poolLock.notifyAll();
            }
            if (!closed.get()) Log.e(TAG, "HardwareBuffer frame presentation failed", error);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        FutureTask<Void> cleanup = new FutureTask<>(() -> {
            if (childSurfaceControl != null) {
                SurfaceControl.Transaction transaction = new SurfaceControl.Transaction();
                try {
                    transaction.remove(childSurfaceControl);
                    transaction.apply();
                } finally {
                    transaction.close();
                }
                childSurfaceControl.release();
                childSurfaceControl = null;
            }

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            synchronized (poolLock) {
                while (buffers.stream().anyMatch(slot -> slot.inUse) && System.nanoTime() < deadline) {
                    try { poolLock.wait(50); }
                    catch (InterruptedException error) { Thread.currentThread().interrupt(); break; }
                }
            }

            for (BufferSlot slot : buffers) {
                FrontBufferHardwareBridge.nativeDestroyFbo(slot.fbo);
                slot.buffer.close();
            }
            buffers.clear();
            if (texture != null) {
                texture.setOnFrameAvailableListener(null);
                texture.release();
                texture = null;
            }
            if (decoderSurface != null) {
                decoderSurface.release();
                decoderSurface = null;
            }
            if (program != 0) {
                GLES20.glDeleteProgram(program);
                program = 0;
            }
            if (textureId != 0) {
                int[] ids = {textureId};
                GLES20.glDeleteTextures(1, ids, 0);
                textureId = 0;
            }
            if (display != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                if (windowSurface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(display, windowSurface);
                    windowSurface = EGL14.EGL_NO_SURFACE;
                }
                if (context != EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(display, context);
                    context = EGL14.EGL_NO_CONTEXT;
                }
                EGL14.eglTerminate(display);
                display = EGL14.EGL_NO_DISPLAY;
            }
            thread.quitSafely();
            return null;
        });
        handler.post(cleanup);
        if (Thread.currentThread() != thread) {
            try { cleanup.get(4, TimeUnit.SECONDS); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            catch (Exception error) { Log.w(TAG, "HardwareBuffer cleanup did not finish cleanly", error); }
        }
        releaseWorkers.shutdown();
        try { releaseWorkers.awaitTermination(2, TimeUnit.SECONDS); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); }
    }
}
