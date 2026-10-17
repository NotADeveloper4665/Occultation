package com.limelight.overlay;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Portable, resolution-independent overlay configuration. Coordinates are normalized centers. */
public final class OverlayLayout {
    public static final int VERSION = 1;
    public int version = VERSION;
    public String id = UUID.randomUUID().toString();
    public String name = "Desktop";
    public String game = "";
    public List<Item> items = new ArrayList<>();

    public static final class Item {
        public String id;
        public String type;
        public float x, y, size = .12f, width = .4f, height = .55f;
        public int key;
        public Item(String id, String type, float x, float y, float size) {
            this.id = id; this.type = type; this.x = x; this.y = y; this.size = size;
        }
        public String label() {
            if ("key".equals(type)) return OverlayKeys.label(key);
            switch (type) {
                case "eye": return "Visibility";
                case "keyboard": return "Keyboard";
                case "home": return "Windows / Super";
                case "mouse": return "Full-screen mouse";
                default: return "Trackpad";
            }
        }
    }
    public static OverlayLayout defaults() {
        OverlayLayout layout = new OverlayLayout();
        layout.id = "builtin-desktop";
        layout.items.add(new Item("trackpad", "trackpad", .77f, .68f, .12f));
        layout.items.add(new Item("eye", "eye", .11f, .10f, .12f));
        // Tight, asymmetric cluster; each button remains circular.
        layout.items.add(new Item("keyboard", "keyboard", .14f, .89f, .17f));
        layout.items.add(new Item("home", "home", .085f, .77f, .12f));
        layout.items.add(new Item("mouse", "mouse", .22f, .81f, .12f));
        return layout;
    }
    public OverlayLayout copy() { return fromJson(toJson()); }
    public String toJson() { return new Gson().toJson(this); }
    public static OverlayLayout fromJson(String json) {
        if (json == null || json.length() > 262144) throw new IllegalArgumentException("Overlay file is too large");
        try {
            OverlayLayout layout = new Gson().fromJson(json, OverlayLayout.class);
            if (layout == null) throw new IllegalArgumentException("Empty overlay");
            layout.validate();
            return layout;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid overlay: " + e.getMessage(), e);
        }
    }
    public void validate() {
        if (version != VERSION) throw new IllegalArgumentException("Unsupported overlay version");
        if (id == null || id.length() > 80 || id.isEmpty() || name == null || name.trim().isEmpty()
                || name.length() > 80 || game == null || game.length() > 200) throw new IllegalArgumentException("Invalid layout name or game");
        if (items == null || items.size() < 5 || items.size() > 69) throw new IllegalArgumentException("Use at most 64 custom key buttons");
        Set<String> ids = new HashSet<>(), builtins = new HashSet<>();
        for (Item item : items) {
            if (item == null || item.id == null || item.id.length() > 80 || item.id.isEmpty() || !ids.add(item.id)) throw new IllegalArgumentException("Duplicate or invalid control ID");
            if (item.type == null || !(item.type.equals("key") || item.type.equals("eye") || item.type.equals("keyboard")
                    || item.type.equals("home") || item.type.equals("mouse") || item.type.equals("trackpad"))) throw new IllegalArgumentException("Unknown control type");
            if (!"key".equals(item.type) && !builtins.add(item.type)) throw new IllegalArgumentException("Duplicate built-in control");
            if ("key".equals(item.type) && OverlayKeys.label(item.key) == null) throw new IllegalArgumentException("Unsupported keyboard key");
            range(item.x, 0, 1); range(item.y, 0, 1); range(item.size, .05f, .6f);
            range(item.width, .1f, 1); range(item.height, .1f, 1);
        }
        if (builtins.size() != 5) throw new IllegalArgumentException("Keep the eye, keyboard, home, mouse and trackpad controls");
    }
    private static void range(float value, float min, float max) {
        if (Float.isNaN(value) || Float.isInfinite(value) || value < min || value > max) throw new IllegalArgumentException("Control position or size is out of range");
    }
}
