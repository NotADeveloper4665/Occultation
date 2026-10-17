package com.limelight.preferences;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;
import java.util.*;

/** Filters the existing preference hierarchy; stored keys and dependencies remain unchanged. */
public final class SettingsNavigation {
    private static final String[] SECTIONS = {"Common settings", "All settings", "Video & display", "Audio",
            "Touch & keyboard", "Overlays & controls", "Controllers", "Connection & permissions",
            "Appearance", "Advanced & diagnostics", "Language & help"};
    private static final Set<String> COMMON = new HashSet<>(Arrays.asList("list_resolution", "list_fps", "seekbar_bitrate_kbps",
            "wan_optimization", "list_audio_config", "mouse_mode_list", "overlay_configuration", "checkbox_remember_mouse_mode",
            "checkbox_enable_floating_button", "eclipse_direct_desktop", "eclipse_view_only", "list_languages"));
    private final PreferenceScreen screen;
    private final Map<Preference, Boolean> originalVisibility = new LinkedHashMap<>();
    private final Map<Preference, Integer> groups = new LinkedHashMap<>();
    private int section;
    private String query = "";

    public SettingsNavigation(PreferenceScreen screen) {
        this.screen = screen;
        List<Preference> categories = new ArrayList<>();
        for (int i = 0; i < screen.getPreferenceCount(); i++) categories.add(screen.getPreference(i));
        for (int i = 0; i < categories.size(); i++) {
            Preference category = categories.get(i);
            String key = category.getKey();
            String title; int group;
            if (key == null) key = "settings_section_" + i;
            switch (key) {
                case "category_video_settings": title = "Video & display"; group = 2; break;
                case "category_audio_settings": case "settings_section_1": title = "Sound"; group = 3; break;
                case "category_gamepad_settings": title = "Game controllers"; group = 6; break;
                case "category_input_settings": title = "Touch, mouse & keyboard"; group = 4; break;
                case "category_general_settings": case "settings_section_5": title = "Clipboard sharing"; group = 4; break;
                case "category_host_settings": case "settings_section_4": title = "Host behavior"; group = 7; break;
                case "category_ui_settings": title = "App & stream interface"; group = 8; break;
                case "category_onscreen_controls": title = "On-screen gamepad"; group = 5; break;
                case "category_special_key_layout": title = "On-screen keyboard & shortcuts"; group = 5; break;
                case "desktop_overlay_settings": title = "Desktop & game overlays"; group = 5; break;
                case "category_virtual_trackpad_settings": title = "Touchpad sensitivity"; group = 4; break;
                case "category_perf_monitor_settings": title = "Performance & logs"; group = 9; break;
                case "category_advanced_settings": title = "Advanced streaming"; group = 9; break;
                case "category_settings_misc": title = "Language, help & updates"; group = 10; break;
                case "eclipse_connection_category": title = "Connection & launch"; group = 7; break;
                case "eclipse_input_category": title = "Input permissions"; group = 7; break;
                default: title = String.valueOf(category.getTitle()); group = 9;
            }
            category.setTitle(title); groups.put(category, group);
            category.setOrder(group * 100 + i);
            snapshot(category);
        }
    }
    private void snapshot(Preference pref) {
        originalVisibility.put(pref, pref.isVisible());
        if (pref instanceof PreferenceGroup) {
            PreferenceGroup group = (PreferenceGroup) pref;
            for (int i = 0; i < group.getPreferenceCount(); i++) snapshot(group.getPreference(i));
        }
    }
    public View wrap(Context context, View content) {
        LinearLayout root = new LinearLayout(context); root.setOrientation(LinearLayout.VERTICAL);
        EditText search = new EditText(context); search.setSingleLine(true); search.setHint("Search settings");
        search.setContentDescription("Search all streaming settings"); root.addView(search);
        Spinner sections = new Spinner(context);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, SECTIONS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); sections.setAdapter(adapter); root.addView(sections);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        sections.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> parent) {}
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { section = position; apply(); }
        });
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { query = s.toString().trim().toLowerCase(Locale.ROOT); apply(); }
            public void afterTextChanged(Editable s) {}
        });
        apply(); return root;
    }
    private void apply() {
        for (Map.Entry<Preference, Integer> entry : groups.entrySet()) {
            Preference category = entry.getKey();
            boolean inSection = section <= 1 || section == entry.getValue();
            boolean any = false;
            PreferenceGroup group = (PreferenceGroup) category;
            group.setInitialExpandedChildrenCount(section == 0 || !query.isEmpty() ? Integer.MAX_VALUE : 5);
            for (int i = 0; i < group.getPreferenceCount(); i++) {
                Preference pref = group.getPreference(i);
                String text = (String.valueOf(pref.getTitle()) + " " + String.valueOf(pref.getSummary()) + " " + category.getTitle()).toLowerCase(Locale.ROOT);
                if (pref instanceof androidx.preference.ListPreference) text += " " + Arrays.toString(((androidx.preference.ListPreference) pref).getEntries()).toLowerCase(Locale.ROOT);
                boolean show = Boolean.TRUE.equals(originalVisibility.get(pref)) && inSection
                        && (query.isEmpty() ? section != 0 || COMMON.contains(pref.getKey()) : text.contains(query));
                pref.setVisible(show); any |= show;
            }
            category.setVisible(inSection && any && Boolean.TRUE.equals(originalVisibility.get(category)));
        }
    }
}
