package com.limelight.interpolation;

import android.content.Context;
import java.io.*;
import java.security.*;
import com.google.gson.*;

/** Installs only bundled, hash-pinned models. No arbitrary code or model downloads on phone. */
final class ModelAssets {
    private ModelAssets() { }
    static synchronized File install(Context context, String asset, String expected) throws IOException {
        File root = new File(context.getFilesDir(), "interpolation-models");
        if (!root.exists() && !root.mkdirs()) throw new IOException("Cannot create model directory");
        File destination = new File(root, asset.replace('/', '_'));
        if (destination.isFile() && hash(destination).equals(expected)) return destination;
        File temp = File.createTempFile("model-", ".part", root);
        try {
            try (InputStream input = context.getAssets().open("interpolation/" + asset);
                 FileOutputStream output = new FileOutputStream(temp)) {
                byte[] block = new byte[65536]; int count;
                while ((count = input.read(block)) != -1) output.write(block, 0, count);
                output.getFD().sync();
            }
            if (!hash(temp).equals(expected)) throw new IOException("Bundled model checksum mismatch");
            if (destination.exists() && !destination.delete()) throw new IOException("Cannot replace model");
            if (!temp.renameTo(destination)) throw new IOException("Cannot install model");
            return destination;
        } finally { if (temp.exists()) temp.delete(); }
    }
    static JsonObject provenance(Context context) throws IOException {
        try (Reader reader = new InputStreamReader(context.getAssets().open("interpolation/model-provenance.json"), java.nio.charset.StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
    static String hash(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new FileInputStream(file)) {
                byte[] block = new byte[65536]; int count;
                while ((count = input.read(block)) != -1) digest.update(block, 0, count);
            }
            StringBuilder hex = new StringBuilder();
            for (byte value : digest.digest()) hex.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            return hex.toString();
        } catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
}
