package com.limelight.binding.video.ahb;

import java.util.Locale;

/** Pure policy; never guesses that Tensor is MediaTek merely because both use Mali. */
public final class DisplayPolicy {
    private DisplayPolicy() { }
    public static boolean isMediaTek(String manufacturer, String hardware, String soc) {
        String value = ((manufacturer == null ? "" : manufacturer) + " " +
                (hardware == null ? "" : hardware) + " " + (soc == null ? "" : soc)).toLowerCase(Locale.ROOT);
        return value.contains("mediatek") || value.matches(".*\\bmt[0-9]{4}[a-z0-9]*\\b.*");
    }
    public static boolean useAhb(int api, boolean hdr, boolean vulkanRequested, boolean mediatekRequested, boolean mediatek) {
        return api >= 33 && !hdr && (vulkanRequested || (mediatekRequested && mediatek));
    }
    public static boolean softwareCodecName(String name) {
        if (name == null) return true;
        String n = name.toLowerCase(Locale.ROOT);
        return n.startsWith("c2.android.") || n.startsWith("c2.google.") || n.startsWith("omx.google.") || n.contains(".sw.");
    }
}
