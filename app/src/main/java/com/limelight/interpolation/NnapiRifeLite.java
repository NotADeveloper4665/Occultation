package com.limelight.interpolation;

import android.content.Context;
import android.os.Build;
import ai.onnxruntime.*;
import ai.onnxruntime.providers.NNAPIFlags;
import java.io.*;
import java.nio.*;
import java.util.*;

/** RIFE Lite ONNX NNAPI workflow. Strictly rejects ORT and NNAPI CPU fallback.
 * NNAPI may select GPU or NPU; success alone does not identify a Tensor TPU.
 */
@androidx.annotation.RequiresApi(29)
public final class NnapiRifeLite implements AutoCloseable {
    private final OrtEnvironment environment;
    private final OrtSession session;
    private final int width, height;
    private final FloatBuffer input;
    private boolean closed;
    public NnapiRifeLite(Context context, int width, int height) throws IOException, OrtException {
        if (Build.VERSION.SDK_INT < 29) throw new IllegalStateException("Strict NNAPI device exclusion requires Android 10+");
        if (width < 32 || height < 32 || width > 1920 || height > 1080 || width % 32 != 0 || height % 32 != 0)
            throw new IllegalArgumentException("RIFE Lite requires dimensions divisible by 32");
        this.width = width; this.height = height;
        File model = ModelAssets.install(context, "rife-lite-4.25.onnx",
                ModelAssets.provenance(context).getAsJsonObject("lite").get("model_sha256").getAsString());
        environment = OrtEnvironment.getEnvironment();
        try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
            options.setIntraOpNumThreads(1);
            options.setInterOpNumThreads(1);
            options.setSymbolicDimensionValue("batch", 1);
            options.setSymbolicDimensionValue("height", height);
            options.setSymbolicDimensionValue("width", width);
            options.addNnapi(EnumSet.of(NNAPIFlags.CPU_DISABLED));
            // CPU_DISABLED only excludes NNAPI CPU devices. Also prohibit ORT
            // kernels from silently executing unsupported parts of the graph.
            options.addConfigEntry("session.disable_cpu_ep_fallback", "1");
            session = environment.createSession(model.getAbsolutePath(), options);
        }
        input = ByteBuffer.allocateDirect(width * height * 7 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    }
    /** RGB24 input/output, [first RGB, second RGB, timestep] packed into NCHW. */
    public synchronized void interpolate(ByteBuffer first, ByteBuffer second, ByteBuffer output, float time) throws OrtException {
        if (closed) throw new IllegalStateException("RIFE Lite session closed");
        int pixels = width * height, bytes = pixels * 3;
        if (first == null || second == null || output == null || first.capacity() < bytes || second.capacity() < bytes ||
                output.capacity() < bytes || output.isReadOnly() || (Float.isNaN(time) || Float.isInfinite(time)) || time <= 0 || time >= 1)
            throw new IllegalArgumentException("Invalid RIFE Lite RGB buffers or timestep");
        input.clear();
        for (int channel = 0; channel < 3; channel++)
            for (int pixel = 0; pixel < pixels; pixel++) input.put((first.get(pixel * 3 + channel) & 255) / 255f);
        for (int channel = 0; channel < 3; channel++)
            for (int pixel = 0; pixel < pixels; pixel++) input.put((second.get(pixel * 3 + channel) & 255) / 255f);
        for (int pixel = 0; pixel < pixels; pixel++) input.put(time);
        input.flip();
        try (OnnxTensor tensor = OnnxTensor.createTensor(environment, input, new long[]{1, 7, height, width});
             OrtSession.Result result = session.run(Collections.singletonMap("input", tensor))) {
            OnnxValue value = result.get(0);
            if (!(value instanceof OnnxTensor)) throw new IllegalStateException("Unexpected RIFE output");
            OnnxTensor generated = (OnnxTensor) value;
            if (!Arrays.equals(generated.getInfo().getShape(), new long[]{1, 3, height, width}))
                throw new IllegalStateException("Unexpected RIFE output shape");
            FloatBuffer floats = generated.getFloatBuffer();
            if (floats.remaining() != bytes) throw new IllegalStateException("Unexpected RIFE output size");
            for (int channel = 0; channel < 3; channel++) {
                for (int pixel = 0; pixel < pixels; pixel++) {
                    float sample = floats.get(channel * pixels + pixel);
                    if (Float.isNaN(sample) || Float.isInfinite(sample)) throw new IllegalStateException("Non-finite RIFE output");
                    output.put(pixel * 3 + channel, (byte)Math.round(Math.max(0, Math.min(1, sample)) * 255));
                }
            }
        }
    }
    @Override public synchronized void close() throws OrtException { if (!closed) { closed = true; session.close(); } }
}
