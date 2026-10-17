package com.limelight.overlay;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Portable, resolution-independent overlay configuration. Coordinates are normalized centers. */
public final class OverlayLayout {
    public static final int VERSION = 2;
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
        public String action;
        public int[] keys;
        public String function() { return action == null ? type : action; }
        public boolean editableButton() { return !"eye".equals(type) && !"trackpad".equals(type); }
        public int[] keyboardKeys() { return keys == null ? new int[]{key} : keys.clone(); }
        public void assign(String function, int[] binding) {
            if (!editableButton()) throw new IllegalArgumentException("The visibility toggle and trackpad are dedicated controls");
            action = function;
            keys = binding == null ? null : binding.clone();
            if (keys != null && keys.length > 0) key = keys[0];
        }
        public Item(String id, String type, float x, float y, float size) {
            this.id = id; this.type = type; this.x = x; this.y = y; this.size = size;
        }
        public String label() {
            if ("key".equals(function())) {
                StringBuilder label = new StringBuilder();
                for (int code : keyboardKeys()) { if (label.length() > 0) label.append(" + "); label.append(OverlayKeys.label(code)); }
                return label.toString();
            }
            switch (function()) {
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
            layout.upgrade();
            layout.validate();
            return layout;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid overlay: " + e.getMessage(), e);
        }
    }
    public void upgrade() { if (version == 1) version = VERSION; }
    public void deleteButton(String controlId) {
        for (java.util.Iterator<Item> iterator = items.iterator(); iterator.hasNext();) {
            Item item = iterator.next();
            if (item.id.equals(controlId)) {
                if (!item.editableButton()) throw new IllegalArgumentException("Keep the visibility toggle and trackpad");
                iterator.remove(); return;
            }
        }
    }
    public void validate() {
        if (version != VERSION) throw new IllegalArgumentException("Unsupported overlay version");
        if (id == null || id.length() > 80 || id.isEmpty() || name == null || name.trim().isEmpty()
                || name.length() > 80 || game == null || game.length() > 200) throw new IllegalArgumentException("Invalid layout name or game");
        if (items == null || items.size() < 2 || items.size() > 69) throw new IllegalArgumentException("Use at most 67 buttons");
        Set<String> ids = new HashSet<>(), builtins = new HashSet<>();
        for (Item item : items) {
            if (item == null || item.id == null || item.id.length() > 80 || item.id.isEmpty() || !ids.add(item.id)) throw new IllegalArgumentException("Duplicate or invalid control ID");
            if (item.type == null || !(item.type.equals("key") || item.type.equals("eye") || item.type.equals("keyboard")
                    || item.type.equals("home") || item.type.equals("mouse") || item.type.equals("trackpad"))) throw new IllegalArgumentException("Unknown control type");
            if (!"key".equals(item.type) && !builtins.add(item.type)) throw new IllegalArgumentException("Duplicate built-in control");
            String function = item.function();
            if (!function.equals("key") && !function.equals("eye") && !function.equals("keyboard")
                    && !function.equals("home") && !function.equals("mouse") && !function.equals("trackpad"))
                throw new IllegalArgumentException("Unknown button function");
            if (!item.editableButton() && !function.equals(item.type)) throw new IllegalArgumentException("Keep dedicated controls unchanged");
            if (item.editableButton() && function.equals("trackpad")) throw new IllegalArgumentException("A button cannot become a trackpad");
            if (function.equals("key")) {
                int[] codes = item.keyboardKeys();
                if (codes.length == 0 || codes.length > 8) throw new IllegalArgumentException("Use one to eight keys per shortcut");
                Set<Integer> unique = new HashSet<>();
                for (int code : codes) if (!OverlayKeys.supported(code) || !unique.add(code)) throw new IllegalArgumentException("Invalid or duplicate keyboard key");
            }
            range(item.x, 0, 1); range(item.y, 0, 1); range(item.size, .05f, .6f);
            range(item.width, .1f, 1); range(item.height, .1f, 1);
        }
        if (!builtins.contains("eye") || !builtins.contains("trackpad")) throw new IllegalArgumentException("Keep the visibility toggle and trackpad");
    }
    private static void range(float value, float min, float max) {
        if (Float.isNaN(value) || Float.isInfinite(value) || value < min || value > max) throw new IllegalArgumentException("Control position or size is out of range");
    }
}
