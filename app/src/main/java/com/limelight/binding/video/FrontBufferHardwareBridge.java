package com.limelight.binding.video;

import android.hardware.HardwareBuffer;

import androidx.annotation.Keep;
import androidx.annotation.RequiresApi;

/** JNI bridge for EGLImage-backed HardwareBuffer framebuffer objects (API 33 path). */
@Keep
@RequiresApi(33)
public final class FrontBufferHardwareBridge {
    static {
        System.loadLibrary("occultation_frontbuffer");
    }

    private FrontBufferHardwareBridge() { }

    public static native long nativeCreateFbo(HardwareBuffer buffer);
    public static native boolean nativeBindFbo(long handle);
    public static native void nativeUnbindFbo();
    public static native void nativeDestroyFbo(long handle);
}
