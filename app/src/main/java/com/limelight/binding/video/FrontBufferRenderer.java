package com.limelight.binding.video;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import androidx.core.util.Consumer;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Experimental SurfaceTexture-to-EGL presenter. It uses EGL_KHR_mutable_render_buffer
 * single-buffer mode only when the device advertises and accepts it. Otherwise the
 * caller keeps the original MediaCodec Surface path.
 */
public final class FrontBufferRenderer implements AutoCloseable {
    private static final String TAG = "OccultationFrontBuffer";
    private static final int EGL_MUTABLE_RENDER_BUFFER_KHR = 0x30A8;
    private static final int EGL_RENDER_BUFFER = 0x3086;
    private static final int EGL_SINGLE_BUFFER = 0x3085;
    private static final int EGL_BACK_BUFFER = 0x3084;
    private static final int EGL_FRONT_BUFFER_AUTO_REFRESH_ANDROID = 0x314C;

    private static final float[] QUAD = {
            -1f, -1f, 0f, 1f,
             1f, -1f, 1f, 1f,
            -1f,  1f, 0f, 0f,
             1f,  1f, 1f, 0f
    };

    private final HandlerThread thread = new HandlerThread("OccultationFrontBufferGL");
    private final Handler handler;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Surface displaySurface;
    private final int streamWidth;
    private final int streamHeight;
    private final Consumer<String> status;

    private EGLDisplay display = EGL14.EGL_NO_DISPLAY;
    private EGLContext context = EGL14.EGL_NO_CONTEXT;
    private EGLSurface windowSurface = EGL14.EGL_NO_SURFACE;
    private SurfaceTexture texture;
    private Surface decoderSurface;
    private int textureId;
    private int program;
    private int positionLocation;
    private int texCoordLocation;
    private int matrixLocation;
    private int samplerLocation;
    private int viewportWidth;
    private int viewportHeight;
    private volatile boolean singleBufferActive;
    private volatile boolean failed;
    private final float[] textureMatrix = new float[16];
    private FloatBuffer vertices;

    public FrontBufferRenderer(Surface displaySurface, int streamWidth, int streamHeight,
                               Consumer<String> status) {
        if (displaySurface == null || !displaySurface.isValid()) {
            throw new IllegalArgumentException("Front-buffer target Surface is invalid");
        }
        if (streamWidth <= 0 || streamHeight <= 0 || streamWidth < streamHeight) {
            throw new IllegalArgumentException("Front-buffer path currently requires a landscape stream");
        }
        this.displaySurface = displaySurface;
        this.streamWidth = streamWidth;
        this.streamHeight = streamHeight;
        this.status = status == null ? message -> { } : status;
        thread.start();
        handler = new Handler(thread.getLooper());

        FutureTask<Void> initialize = new FutureTask<>(() -> {
            initializeOnGlThread();
            return null;
        });
        handler.post(initialize);
        try {
            initialize.get(8, TimeUnit.SECONDS);
        } catch (Exception error) {
            close();
            Throwable cause = error instanceof ExecutionException ? error.getCause() : error;
            throw new IllegalStateException("EGL single-buffer presenter unavailable", cause);
        }
    }

    public Surface displaySurface() { return displaySurface; }

    public Surface decoderSurface() {
        if (closed.get() || decoderSurface == null) {
            throw new IllegalStateException("Front-buffer decoder Surface is unavailable");
        }
        return decoderSurface;
    }

    public boolean isSingleBufferActive() {
        return singleBufferActive;
    }

    private void initializeOnGlThread() {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (display == EGL14.EGL_NO_DISPLAY) throw new IllegalStateException("eglGetDisplay failed");
        int[] versions = new int[2];
        if (!EGL14.eglInitialize(display, versions, 0, versions, 1)) {
            throw new IllegalStateException("eglInitialize failed");
        }
        String extensions = EGL14.eglQueryString(display, EGL14.EGL_EXTENSIONS);
        if (extensions == null || !extensions.contains("EGL_KHR_mutable_render_buffer")) {
            throw new IllegalStateException("EGL_KHR_mutable_render_buffer is not supported");
        }

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
            throw new IllegalStateException("No compatible EGL window configuration");
        }

        int[] contextAttributes = { EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE };
        context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
                contextAttributes, 0);
        if (context == EGL14.EGL_NO_CONTEXT) throw new IllegalStateException("eglCreateContext failed");

        int[] surfaceAttributes = {
                EGL_MUTABLE_RENDER_BUFFER_KHR, EGL14.EGL_TRUE,
                EGL14.EGL_NONE
        };
        windowSurface = EGL14.eglCreateWindowSurface(display, configs[0], displaySurface,
                surfaceAttributes, 0);
        if (windowSurface == EGL14.EGL_NO_SURFACE) {
            throw new IllegalStateException("eglCreateWindowSurface failed");
        }
        if (!EGL14.eglSurfaceAttrib(display, windowSurface, EGL_RENDER_BUFFER, EGL_SINGLE_BUFFER)) {
            throw new IllegalStateException("EGL rejected EGL_SINGLE_BUFFER");
        }
        singleBufferActive = true;
        if (extensions.contains("EGL_ANDROID_front_buffer_auto_refresh")) {
            EGL14.eglSurfaceAttrib(display, windowSurface, EGL_FRONT_BUFFER_AUTO_REFRESH_ANDROID, EGL14.EGL_TRUE);
        }
        if (!EGL14.eglMakeCurrent(display, windowSurface, windowSurface, context)) {
            throw new IllegalStateException("eglMakeCurrent failed");
        }
        int[] dimension = new int[1];
        if (!EGL14.eglQuerySurface(display, windowSurface, EGL14.EGL_WIDTH, dimension, 0)) {
            throw new IllegalStateException("Could not query front-buffer width");
        }
        viewportWidth = dimension[0];
        if (!EGL14.eglQuerySurface(display, windowSurface, EGL14.EGL_HEIGHT, dimension, 0)) {
            throw new IllegalStateException("Could not query front-buffer height");
        }
        viewportHeight = dimension[0];
        if (viewportWidth <= 0 || viewportHeight <= 0 || viewportWidth < viewportHeight) {
            throw new IllegalStateException("Front-buffer surface is not landscape");
        }

        createExternalTexture();
        program = createProgram();
        positionLocation = GLES20.glGetAttribLocation(program, "aPosition");
        texCoordLocation = GLES20.glGetAttribLocation(program, "aTexCoord");
        matrixLocation = GLES20.glGetUniformLocation(program, "uTexMatrix");
        samplerLocation = GLES20.glGetUniformLocation(program, "sTexture");
        vertices = ByteBuffer.allocateDirect(QUAD.length * 4).order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        vertices.put(QUAD).position(0);

        texture = new SurfaceTexture(textureId);
        texture.setDefaultBufferSize(streamWidth, streamHeight);
        texture.setOnFrameAvailableListener(this::onFrameAvailable, handler);
        decoderSurface = new Surface(texture);
        status.accept("EGL single-buffer front-buffer presentation active");
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

    private static int compileShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] status = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new IllegalStateException("Front-buffer shader compilation failed: " + log);
        }
        return shader;
    }

    private static int createProgram() {
        String vertex = "attribute vec4 aPosition; attribute vec4 aTexCoord; uniform mat4 uTexMatrix; " +
                "varying vec2 vTexCoord; void main(){ gl_Position=aPosition; vTexCoord=(uTexMatrix*aTexCoord).xy; }";
        String fragment = "#extension GL_OES_EGL_image_external : require\nprecision mediump float; " +
                "uniform samplerExternalOES sTexture; varying vec2 vTexCoord; " +
                "void main(){ gl_FragColor=texture2D(sTexture,vTexCoord); }";
        int vs = compileShader(GLES20.GL_VERTEX_SHADER, vertex);
        int fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragment);
        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vs);
        GLES20.glAttachShader(program, fs);
        GLES20.glLinkProgram(program);
        GLES20.glDeleteShader(vs);
        GLES20.glDeleteShader(fs);
        int[] status = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0);
        if (status[0] == 0) {
            String log = GLES20.glGetProgramInfoLog(program);
            GLES20.glDeleteProgram(program);
            throw new IllegalStateException("Front-buffer shader link failed: " + log);
        }
        return program;
    }

    private void onFrameAvailable(SurfaceTexture source) {
        if (closed.get() || failed) return;
        try {
            source.updateTexImage();
            source.getTransformMatrix(textureMatrix);
            drawFrame();
        } catch (RuntimeException error) {
            if (singleBufferActive && EGL14.eglSurfaceAttrib(display, windowSurface,
                    EGL_RENDER_BUFFER, EGL_BACK_BUFFER)) {
                singleBufferActive = false;
                status.accept("Front-buffer presentation failed; switched to EGL back-buffer mode");
                try { drawFrame(); } catch (RuntimeException fallbackError) {
                    failed = true;
                    Log.e(TAG, "Fallback EGL presentation failed", fallbackError);
                }
            } else {
                failed = true;
                Log.e(TAG, "Front-buffer presentation failed", error);
            }
        }
    }

    private void drawFrame() {
        if (!EGL14.eglMakeCurrent(display, windowSurface, windowSurface, context)) {
            throw new IllegalStateException("eglMakeCurrent failed while presenting");
        }
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight);
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
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        GLES20.glUniform1i(samplerLocation, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glFlush();
        if (!EGL14.eglSwapBuffers(display, windowSurface)) {
            throw new IllegalStateException("eglSwapBuffers failed: 0x" +
                    Integer.toHexString(EGL14.eglGetError()));
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        FutureTask<Void> cleanup = new FutureTask<>(() -> {
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
                int[] ids = { textureId };
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
            try { cleanup.get(3, TimeUnit.SECONDS); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            catch (Exception error) { Log.w(TAG, "Front-buffer cleanup did not finish cleanly", error); }
        }
    }
}
