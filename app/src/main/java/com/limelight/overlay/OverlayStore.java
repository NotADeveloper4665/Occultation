package com.limelight.overlay;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class OverlayStore {
    private final SharedPreferences prefs;
    public OverlayStore(Context context) { prefs = context.getSharedPreferences("desktop_overlay_layouts", Context.MODE_PRIVATE); }
    public List<OverlayLayout> layouts() {
        try {
            List<OverlayLayout> list = new Gson().fromJson(prefs.getString("layouts", "[]"), new TypeToken<List<OverlayLayout>>(){}.getType());
            if (list != null && !list.isEmpty()) {
                for (OverlayLayout layout : list) layout.validate();
                return list;
            }
        } catch (RuntimeException ignored) { /* Retain corrupt storage for recovery, use a safe default. */ }
        List<OverlayLayout> list = new ArrayList<>(); list.add(OverlayLayout.defaults()); return list;
    }
    public OverlayLayout active() {
        List<OverlayLayout> list = layouts();
        String id = prefs.getString("active", "");
        for (OverlayLayout layout : list) if (layout.id.equals(id)) return layout;
        return list.get(0);
    }
    public OverlayLayout forGame(String game) {
        OverlayLayout selected = active();
        if (matches(selected.game, game)) return selected;
        List<OverlayLayout> list = layouts();
        for (OverlayLayout layout : list) if (matches(layout.game, game)) return layout;
        if (selected.game.trim().isEmpty()) return selected;
        String general = prefs.getString("general", "builtin-desktop");
        for (OverlayLayout layout : list) if (layout.id.equals(general) && layout.game.trim().isEmpty()) return layout;
        for (OverlayLayout layout : list) if (layout.game.trim().isEmpty()) return layout;
        return OverlayLayout.defaults();
    }
    private static boolean matches(String assigned, String game) {
        return !assigned.trim().isEmpty() && game != null && assigned.trim().equalsIgnoreCase(game.trim());
    }
    public void save(OverlayLayout layout) {
        layout.validate();
        List<OverlayLayout> list = layouts();
        for (java.util.Iterator<OverlayLayout> it = list.iterator(); it.hasNext();) if (it.next().id.equals(layout.id)) it.remove();
        if (list.size() >= 50) throw new IllegalArgumentException("Keep at most 50 saved layouts");
        // One automatic assignment per exact game name; saving is explicit reassignment.
        for (OverlayLayout old : list) if (matches(old.game, layout.game)) old.game = "";
        list.add(layout.copy());
        SharedPreferences.Editor edit = prefs.edit().putString("layouts", new Gson().toJson(list)).putString("active", layout.id);
        if (layout.game.trim().isEmpty()) edit.putString("general", layout.id);
        edit.apply();
    }
    public OverlayLayout importLayout(String json) {
        OverlayLayout layout = OverlayLayout.fromJson(json);
        layout.id = UUID.randomUUID().toString(); // Imports never overwrite a user's existing layout.
        return layout;
    }
    public void delete(String id) {
        List<OverlayLayout> list = layouts(); for (java.util.Iterator<OverlayLayout> it = list.iterator(); it.hasNext();) if (it.next().id.equals(id)) it.remove();
        if (list.isEmpty()) list.add(OverlayLayout.defaults());
        prefs.edit().putString("layouts", new Gson().toJson(list)).putString("active", list.get(0).id).apply();
    }
}
