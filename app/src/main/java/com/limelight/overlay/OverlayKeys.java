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
        for (int i = 0; i < 24; i++) KEYS.put(0x70 + i, "F" + (i + 1));
        KEYS.put(0x10, "Shift (generic)"); KEYS.put(0x11, "Ctrl (generic)"); KEYS.put(0x12, "Alt (generic)");
        KEYS.put(0x5C, "Right Windows / Super"); KEYS.put(0x5D, "Menu");
        KEYS.put(0x13, "Pause / Break"); KEYS.put(0x2C, "Print Screen");
        KEYS.put(0x90, "Num Lock"); KEYS.put(0x91, "Scroll Lock");
        for (int i = 0; i < 10; i++) KEYS.put(0x60 + i, "Numpad " + i);
        KEYS.put(0x6A, "Numpad *"); KEYS.put(0x6B, "Numpad +"); KEYS.put(0x6C, "Numpad separator");
        KEYS.put(0x6D, "Numpad -"); KEYS.put(0x6E, "Numpad decimal"); KEYS.put(0x6F, "Numpad /");
        KEYS.put(0xAD, "Mute"); KEYS.put(0xAE, "Volume down"); KEYS.put(0xAF, "Volume up");
        KEYS.put(0xB0, "Next track"); KEYS.put(0xB1, "Previous track"); KEYS.put(0xB2, "Stop media"); KEYS.put(0xB3, "Play / pause");
        KEYS.put(0xA6, "Browser back"); KEYS.put(0xA7, "Browser forward"); KEYS.put(0xA8, "Browser refresh");
        KEYS.put(0xA9, "Browser stop"); KEYS.put(0xAA, "Browser search"); KEYS.put(0xAB, "Browser favorites"); KEYS.put(0xAC, "Browser home");
        KEYS.put(0x0C, "Clear"); KEYS.put(0x29, "Select"); KEYS.put(0x2A, "Print"); KEYS.put(0x2B, "Execute"); KEYS.put(0x2F, "Help");
        KEYS.put(0x15, "Kana / Hangul"); KEYS.put(0x19, "Kanji / Hanja"); KEYS.put(0x1C, "Convert"); KEYS.put(0x1D, "Non-convert");
        KEYS.put(0xE2, "OEM 102");
        KEYS.put(0xBA, ";"); KEYS.put(0xBB, "="); KEYS.put(0xBC, ",");
        KEYS.put(0xBD, "-"); KEYS.put(0xBE, "."); KEYS.put(0xBF, "/");
        KEYS.put(0xC0, "`"); KEYS.put(0xDB, "["); KEYS.put(0xDC, "\\"); KEYS.put(0xDD, "]"); KEYS.put(0xDE, "'");
    }
    public static boolean supported(int key) { return key >= 0x08 && key <= 0xFE; }
    public static String label(int key) {
        if (!supported(key)) return null;
        String name = KEYS.get(key);
        return name == null ? String.format(java.util.Locale.ROOT, "Key 0x%02X", key) : name;
    }
    public static boolean modifier(int key) {
        return key == 0x10 || key == 0x11 || key == 0x12 || key == 0x5B || key == 0x5C || (key >= 0xA0 && key <= 0xA5);
    }
    public static Map<Integer, String> all() { return new LinkedHashMap<>(KEYS); }
    private OverlayKeys() {}
}
