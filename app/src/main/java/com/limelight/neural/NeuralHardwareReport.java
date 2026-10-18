package com.limelight.neural;

import android.os.Build;

/** Read-only diagnostics. Does not initialize an inference engine or change stream rendering. */
public final class NeuralHardwareReport {
    private NeuralHardwareReport() { }
    private static native String nativeDevices();

    public static String collect() {
        StringBuilder report = new StringBuilder("Occultation neural upscaling hardware report\n");
        report.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL);
        report.append("\nAndroid API: ").append(Build.VERSION.SDK_INT);
        if (Build.VERSION.SDK_INT >= 31) report.append("\nSoC: ").append(Build.SOC_MANUFACTURER).append(' ').append(Build.SOC_MODEL);
        report.append("\nNeural upscaling: not active; no inference model is installed.\n");
        report.append("Google Tensor SDK currently lists G5/G6. Pixel 9a (G4) is outside that supported path.\n");
        report.append("NNAPI is deprecated on Android 15+. Device names below are diagnostic, not proof of TPU acceleration.\n\n");
        try {
            System.loadLibrary("occultation-neural-probe");
            report.append(nativeDevices());
        } catch (LinkageError | SecurityException e) {
            report.append("Native accelerator query unavailable: ").append(e.getClass().getSimpleName()).append('\n');
        }
        return report.toString();
    }
}
