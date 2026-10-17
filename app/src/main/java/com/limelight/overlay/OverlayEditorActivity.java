package com.limelight.overlay;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import com.limelight.R;
import com.limelight.ui.DesktopControlsView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Offline editor. Preview events never connect to a host or send keyboard/mouse input. */
public class OverlayEditorActivity extends Activity {
    private static final int IMPORT = 710, EXPORT = 711;
    private OverlayStore store;
    private OverlayLayout layout;
    private DesktopControlsView preview;
    private FrameLayout previewFrame;
    private EditText name, game;
    private Spinner controls;
    private SeekBar scale;
    private TextView selectionLabel;
    private OverlayLayout.Item selected;
    private boolean updating, dirty;
    private String pendingExport;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new OverlayStore(this);
        layout = state != null && state.containsKey("layout") ? new com.google.gson.Gson().fromJson(state.getString("layout"), OverlayLayout.class) : store.active().copy();
        dirty = state != null && state.getBoolean("dirty");
        pendingExport = state == null ? null : state.getString("export");
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(8), dp(10), dp(8)); root.setBackgroundColor(0xFF171320);
        LinearLayout actions = row(root);
        action(actions, "Layouts", () -> discardThen(this::chooseLayout));
        action(actions, "New", () -> discardThen(() -> { layout = OverlayLayout.defaults(); layout.id = UUID.randomUUID().toString(); layout.name = "New layout"; loadPreview(); dirty = true; }));
        action(actions, "Save", this::save);
        action(actions, "Import", () -> discardThen(() -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, IMPORT);
        }));
        action(actions, "Export", () -> {
            try {
                syncFields(); layout.validate(); pendingExport = layout.toJson();
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json")
                        .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "occultation-overlay.json");
                startActivityForResult(intent, EXPORT);
            } catch (RuntimeException e) { error(e); }
        });
        action(actions, "Delete layout", () -> new AlertDialog.Builder(this).setTitle("Delete saved layout?")
                .setMessage("This removes the saved layout, including its game assignment.")
                .setNegativeButton("Cancel", null).setPositiveButton("Delete", (d,w) -> { store.delete(layout.id); layout = store.active().copy(); loadPreview(); dirty = false; }).show());
        LinearLayout fields = new LinearLayout(this);
        name = field("Layout name", fields); game = field("Game name (optional)", fields); root.addView(fields);
        FrameLayout previewArea = new FrameLayout(this) {
            @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
                int w = Math.min(r-l, (b-t)*16/9), h = w*9/16;
                getChildAt(0).layout((r-l-w)/2, (b-t-h)/2, (r-l+w)/2, (b-t+h)/2);
            }
        };
        previewFrame = new FrameLayout(this); previewFrame.setBackgroundResource(R.drawable.overlay_preview_desktop);
        previewArea.addView(previewFrame, new FrameLayout.LayoutParams(-1, -1));
        root.addView(previewArea, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView hint = new TextView(this); hint.setText("Drag a control to move it. Select a control below to change its size. Game name must match the launched app.");
        hint.setTextColor(0xFFCEC2DD); hint.setTextSize(12); root.addView(hint);
        LinearLayout tools = row(root);
        controls = new Spinner(this); tools.addView(controls, new LinearLayout.LayoutParams(dp(185), dp(48)));
        action(tools, "Add key", () -> pickKey(null));
        action(tools, "Change key", () -> { if (selected != null && "key".equals(selected.type)) pickKey(selected); else toast("Select a custom key button first"); });
        action(tools, "Remove key", () -> {
            if (selected != null && "key".equals(selected.type)) { syncFields(); layout.items.remove(selected); selected = null; loadPreview(); dirty = true; }
            else toast("Built-in controls stay available. Select a custom key button to remove it.");
        });
        action(tools, "Reset positions", () -> new AlertDialog.Builder(this).setMessage("Reset the five built-in controls to their default positions and sizes?")
                .setNegativeButton("Cancel", null).setPositiveButton("Reset", (d,w) -> {
                    syncFields();
                    for (OverlayLayout.Item item : OverlayLayout.defaults().items) {
                        for (int i = 0; i < layout.items.size(); i++) if (layout.items.get(i).type.equals(item.type)) { layout.items.set(i, item); break; }
                    }
                    loadPreview(); dirty = true;
                }).show());
        selectionLabel = new TextView(this); selectionLabel.setTextColor(0xFFF4EAFF); root.addView(selectionLabel);
        scale = new SeekBar(this); scale.setMax(55); root.addView(scale);
        scale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) {}
            public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                if (!user || selected == null) return;
                float size = (progress + 5) / 100f;
                if ("trackpad".equals(selected.type)) {
                    float factor = size / selected.size;
                    selected.width = Math.max(.1f, Math.min(1, selected.width * factor));
                    selected.height = Math.max(.1f, Math.min(1, selected.height * factor));
                }
                selected.size = size; preview.requestLayout(); dirty = true; updateLabel();
            }
        });
        controls.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> parent) {}
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position < layout.items.size()) select(layout.items.get(position));
            }
        });
        TextWatcher watcher = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { if (!updating) dirty = true; }
            public void afterTextChanged(Editable s) {}
        };
        name.addTextChangedListener(watcher); game.addTextChangedListener(watcher);
        setContentView(root); com.limelight.utils.UiHelper.applyStatusBarPadding(root); loadPreview();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout row(LinearLayout root) {
        HorizontalScrollView scroll = new HorizontalScrollView(this); scroll.setFillViewport(false);
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); scroll.addView(row);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, dp(48))); return row;
    }
    private void action(LinearLayout row, String text, Runnable action) {
        Button button = new Button(this); button.setText(text); button.setAllCaps(false); button.setTextSize(12);
        button.setOnClickListener(v -> action.run()); row.addView(button, new LinearLayout.LayoutParams(-2, dp(48)));
    }
    private EditText field(String hint, LinearLayout row) {
        EditText field = new EditText(this); field.setHint(hint); field.setTextSize(13); field.setSingleLine(true);
        field.setTextColor(0xFFF4EAFF); field.setHintTextColor(0xFFB2A3C4);
        field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(hint.startsWith("Layout") ? 80 : 200)});
        row.addView(field, new LinearLayout.LayoutParams(0, dp(48), 1)); return field;
    }
    private void syncFields() { layout.name = name.getText().toString().trim(); layout.game = game.getText().toString().trim(); }
    private void loadPreview() {
        updating = true; name.setText(layout.name); game.setText(layout.game); updating = false;
        previewFrame.removeAllViews();
        preview = new DesktopControlsView(this, () -> {}, () -> {}, () -> {}, e -> {});
        preview.setConfiguration(layout, (key, down) -> {});
        preview.setEditing(item -> { controls.setSelection(layout.items.indexOf(item)); select(item); dirty = true; });
        previewFrame.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        List<String> labels = new ArrayList<>(); for (OverlayLayout.Item item : layout.items) labels.add(item.label());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); controls.setAdapter(adapter);
        select(layout.items.get(0));
    }
    private void select(OverlayLayout.Item item) { selected = item; preview.selectControl(item.id); scale.setProgress(Math.round(item.size * 100) - 5); updateLabel(); }
    private void updateLabel() { selectionLabel.setText(selected.label() + " · Size " + Math.round(selected.size * 100) + "%"); }
    private void chooseLayout() {
        List<OverlayLayout> list = store.layouts(); String[] names = new String[list.size()];
        for (int i = 0; i < names.length; i++) names[i] = list.get(i).name + (list.get(i).game.isEmpty() ? "" : " — " + list.get(i).game);
        new AlertDialog.Builder(this).setTitle("Saved overlay layouts").setItems(names, (d,w) -> { layout = list.get(w).copy(); loadPreview(); dirty = false; }).show();
    }
    private void pickKey(OverlayLayout.Item existing) {
        syncFields();
        Map<Integer, String> keys = OverlayKeys.all(); List<Integer> codes = new ArrayList<>(keys.keySet());
        new AlertDialog.Builder(this).setTitle("Choose a keyboard key")
                .setItems(keys.values().toArray(new String[0]), (d,w) -> {
                    if (existing == null && layout.items.size() >= 69) { toast("Use at most 64 custom key buttons"); return; }
                    OverlayLayout.Item item = existing == null ? new OverlayLayout.Item(UUID.randomUUID().toString(), "key", .5f, .5f, .12f) : existing;
                    item.key = codes.get(w); if (existing == null) layout.items.add(item);
                    loadPreview(); controls.setSelection(layout.items.indexOf(item)); select(item); dirty = true;
                }).show();
    }
    private boolean save() {
        try { syncFields(); store.save(layout); dirty = false; toast("Layout saved and selected"); return true; }
        catch (RuntimeException e) { error(e); return false; }
    }
    private void discardThen(Runnable action) {
        if (!dirty) { action.run(); return; }
        new AlertDialog.Builder(this).setTitle("Unsaved overlay changes")
                .setPositiveButton("Save", (d,w) -> { if (save()) action.run(); })
                .setNegativeButton("Discard", (d,w) -> action.run()).setNeutralButton("Cancel", null).show();
    }
    @Override public void onBackPressed() { discardThen(this::finish); }
    @Override public void onSaveInstanceState(Bundle out) {
        syncFields(); out.putString("layout", layout.toJson()); out.putBoolean("dirty", dirty); out.putString("export", pendingExport);
        super.onSaveInstanceState(out);
    }
    @Override public void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        try {
            if (request == IMPORT) {
                try (InputStream input = getContentResolver().openInputStream(data.getData()); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                    if (input == null) throw new IllegalArgumentException("Cannot read this file");
                    byte[] buffer = new byte[4096]; int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (bytes.size() + count > 262144) throw new IllegalArgumentException("Overlay files must be smaller than 256 KB");
                        bytes.write(buffer, 0, count);
                    }
                    layout = store.importLayout(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
                    loadPreview(); dirty = true; toast("Imported for preview. Save to keep this layout.");
                }
            } else if (request == EXPORT && pendingExport != null) {
                try (OutputStream output = getContentResolver().openOutputStream(data.getData(), "wt")) {
                    if (output == null) throw new IllegalArgumentException("Cannot write this file");
                    output.write(pendingExport.getBytes(StandardCharsets.UTF_8));
                }
                pendingExport = null; toast("Overlay exported");
            }
        } catch (Exception e) { error(e); }
    }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    private void error(Exception e) { new AlertDialog.Builder(this).setTitle("Overlay could not be saved or loaded").setMessage(e.getMessage()).setPositiveButton("OK", null).show(); }
}
