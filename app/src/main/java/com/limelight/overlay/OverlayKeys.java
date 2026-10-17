package com.limelight.overlay;

import java.util.LinkedHashMap;
import java.util.Map;

/** Supported protocol virtual keys. Labels are derived, never supplied by imported files. */
public final class OverlayKeys {
    private static final Map<Integer, String> KEYS = new LinkedHashMap<>();
    static {
        KEYS.put(0xA0, "Shift"); KEYS.put(0xA1, "Right Shift");
        KEYS.put(0xA2, "Ctrl"); KEYS.put(0xA3, "Right Ctrl");
        KEYS.put(0xA4, "Alt"); KEYS.put(0xA5, "Right Alt");
        KEYS.put(0x5B, "Windows / Super");
        KEYS.put(0x20, "Space"); KEYS.put(0x0D, "Enter"); KEYS.put(0x1B, "Esc");
        KEYS.put(0x09, "Tab"); KEYS.put(0x08, "Backspace"); KEYS.put(0x2E, "Delete");
        KEYS.put(0x26, "Up"); KEYS.put(0x28, "Down"); KEYS.put(0x25, "Left"); KEYS.put(0x27, "Right");
        KEYS.put(0x24, "Home"); KEYS.put(0x23, "End"); KEYS.put(0x21, "Page Up"); KEYS.put(0x22, "Page Down");
        KEYS.put(0x2D, "Insert"); KEYS.put(0x14, "Caps Lock");
        for (int i = 'A'; i <= 'Z'; i++) KEYS.put(i, Character.toString((char)i));
        for (int i = '0'; i <= '9'; i++) KEYS.put(i, Character.toString((char)i));
        for (int i = 0; i < 12; i++) KEYS.put(0x70 + i, "F" + (i + 1));
        KEYS.put(0xBA, ";"); KEYS.put(0xBB, "="); KEYS.put(0xBC, ",");
        KEYS.put(0xBD, "-"); KEYS.put(0xBE, "."); KEYS.put(0xBF, "/");
        KEYS.put(0xC0, "`"); KEYS.put(0xDB, "["); KEYS.put(0xDC, "\\"); KEYS.put(0xDD, "]"); KEYS.put(0xDE, "'");
    }
    public static String label(int key) { return KEYS.get(key); }
    public static Map<Integer, String> all() { return new LinkedHashMap<>(KEYS); }
    private OverlayKeys() {}
}
