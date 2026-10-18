package com.limelight.interpolation;

import android.content.Context;
import android.os.Build;
import java.io.*;
import java.nio.ByteBuffer;
import com.google.gson.*;

/** Real ncnn RIFE v4 Vulkan inference. GPU backend; does not claim Tensor execution. */
public final class VulkanRife implements AutoCloseable {
    private long handle;
    public VulkanRife(Context context) throws IOException {
        if (Build.VERSION.SDK_INT < 24) throw new IllegalStateException("RIFE Vulkan requires Android 7 or newer");
        JsonObject provenance = ModelAssets.provenance(context);
        File root = new File(context.getFilesDir(), "rife-vulkan-v4");
        if (!root.exists() && !root.mkdirs()) throw new IOException("Cannot create Vulkan model directory");
        for (JsonElement entry : provenance.getAsJsonArray("files")) {
            JsonObject file = entry.getAsJsonObject(); String path = file.get("path").getAsString();
            if (!path.startsWith("models/rife-v4/")) continue;
            String name = path.substring("models/".length());
            String expected = file.get("sha256").getAsString();
            File installed = ModelAssets.install(context, name, expected);
            File destination = new File(root, installed.getName().substring("rife-v4_".length()));
            if (!destination.isFile() || !ModelAssets.hash(destination).equals(expected)) {
                try (InputStream input = new FileInputStream(installed); OutputStream output = new FileOutputStream(destination)) {
                    byte[] block = new byte[65536]; int count;
                    while ((count = input.read(block)) != -1) output.write(block, 0, count);
                }
            }
        }
        System.loadLibrary("occultation-rife-vulkan");
        handle = nativeOpen(root.getAbsolutePath());
        if (handle == 0) throw new IllegalStateException("RIFE Vulkan initialization failed");
    }
    /** Packed RGB24. Calls are serialized; callers own all buffers until return. */
    public synchronized void interpolate(ByteBuffer first, ByteBuffer second, ByteBuffer output, int width, int height, float time) {
        if (handle == 0) throw new IllegalStateException("RIFE session closed");
        if (first == null || second == null || output == null || output.isReadOnly()) throw new IllegalArgumentException("Invalid RGB buffers");
        nativeInterpolate(handle, first, second, output, width, height, time);
    }
    public synchronized String device() { return nativeDevice(handle); }
    @Override public synchronized void close() { if (handle != 0) { nativeClose(handle); handle = 0; } }
    private static native long nativeOpen(String modelDirectory);
    private static native void nativeInterpolate(long handle, ByteBuffer a, ByteBuffer b, ByteBuffer out, int width, int height, float time);
    private static native String nativeDevice(long handle);
    private static native void nativeClose(long handle);
}
