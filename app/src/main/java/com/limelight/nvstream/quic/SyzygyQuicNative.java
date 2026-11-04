package com.limelight.nvstream.quic;

import androidx.annotation.Keep;

/** JNI boundary for the pinned QUIC v1 transport. */
@Keep
public final class SyzygyQuicNative {
    static {
        System.loadLibrary("occultation_quic");
    }

    private SyzygyQuicNative() { }

    public static native long nativeConnect(String host, int port, String certificateSha256);
    public static native String nativeReadAuthChallenge(long handle);
    public static native boolean nativeAuthenticate(long handle, String proofLine);
    public static native int nativeOpenBi(long handle, String prefaceLine);
    public static native boolean nativeStreamWrite(long handle, int streamId, byte[] data);
    public static native boolean nativeStreamFinish(long handle, int streamId);
    /** Returns null on timeout and an empty array on end-of-stream. */
    public static native byte[] nativeStreamRead(long handle, int streamId, int maxBytes, int timeoutMs);
    public static native boolean nativeSendDatagram(long handle, byte[] data);
    /** Returns null when no datagram arrives before timeout. */
    public static native byte[] nativeReceiveDatagram(long handle, int timeoutMs);
    public static native void nativeClose(long handle);
}
