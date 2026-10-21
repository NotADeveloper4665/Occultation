package com.limelight.neural;

import java.util.concurrent.atomic.AtomicBoolean;

/** Explicit diagnostics only. Never enables or changes the streaming renderer. */
public final class NnapiDiagnostics {
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private NnapiDiagnostics() { }
    private static native String nativeSelfTest();

    /** Call off the UI thread. Serializes across activity recreation. */
    public static String runSelfTest() {
        if (!RUNNING.compareAndSet(false, true)) return "An accelerator test is already running.\n";
        try {
            System.loadLibrary("occultation-neural-probe");
            return nativeSelfTest();
        } catch (LinkageError | SecurityException error) {
            return "NNAPI execution test unavailable: " + error.getClass().getSimpleName() + "\n";
        } finally {
            RUNNING.set(false);
        }
    }
}
