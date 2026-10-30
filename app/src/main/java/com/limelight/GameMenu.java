package com.limelight;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.graphics.Typeface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.widget.Toast;

import com.limelight.binding.input.GameInputDevice;
import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.utils.KeyConfigHelper;
import com.limelight.utils.KeyMapper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provide options for ongoing Game Stream.
 * <p>
 * Shown on back action in game activity.
 */
public class GameMenu implements Game.GameMenuCallbacks {

    public static final long KEY_UP_DELAY = 25;
    private static final long TEST_GAME_FOCUS_DELAY = 10;

    public static final String PREF_NAME = "specialPrefs"; // SharedPreferences的名称

    public static final String KEY_NAME = "special_key"; // 要保存的键名称

    public static class MenuOption {
        private final String label;
        private final boolean withGameFocus;
        private final Runnable runnable;

        public MenuOption(String label, boolean withGameFocus, Runnable runnable) {
            this.label = label;
            this.withGameFocus = withGameFocus;
            this.runnable = runnable;
        }

        public MenuOption(String label, Runnable runnable) {
            this(label, false, runnable);
        }
    }

    private final Game game;
    private final Context dialogScreenContext;

    private AlertDialog currentDialog;

    public GameMenu(Game game, Context dialogScreenContext) {
        this.game = game;
        this.dialogScreenContext = dialogScreenContext;
    }

    public GameMenu(Game game) {
        this.game = game;
        this.dialogScreenContext = game;
    }

    private String getString(int id) {
        return game.getResources().getString(id);
    }


    private void sendKeys(short[] keys) {
        game.sendKeys(keys);
    }

    private void runWithGameFocus(Runnable runnable) {
        // Ensure that the Game activity is still active (not finished)
        if (game.isFinishing()) {
            return;
        }
        // Check if the game window has focus again, if not try again after delay
        if (!game.hasWindowFocus() && dialogScreenContext instanceof Game) {
            new Handler().postDelayed(() -> runWithGameFocus(runnable), TEST_GAME_FOCUS_DELAY);
            return;
        }
        // Game Activity has focus, run runnable
        runnable.run();
    }

    private void run(MenuOption option) {
        if (option.runnable == null) {
            return;
        }

        if (option.withGameFocus) {
            runWithGameFocus(option.runnable);
        } else {
            option.runnable.run();
        }
    }

    private void showMenuDialog(String title, MenuOption[] options) {
        int themeResId = game.getApplicationInfo().theme;
        Context themedContext = new ContextThemeWrapper(dialogScreenContext, themeResId);
        AlertDialog.Builder builder = new AlertDialog.Builder(themedContext);
        builder.setTitle(title);

        final ArrayAdapter<String> actions = new ArrayAdapter<>(themedContext, android.R.layout.simple_list_item_1);

        builder.setAdapter(actions, (dialog, which) -> {
            String label = actions.getItem(which);
            for (MenuOption option : options) {
                if (label != null && label.equals(option.label)) {
                    run(option);
                    break;
                }
            }
        });

        builder.setOnCancelListener(dialog -> hideMenu());

        if (currentDialog != null) {
            currentDialog.dismiss();
        }
        currentDialog = builder.show();

        Window window = currentDialog.getWindow();

        if (window != null) {
            View decorView = window.getDecorView();
            decorView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {

                    decorView.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                    new Handler(Looper.getMainLooper()).post(() -> {
                        for (MenuOption option : options) {
                            actions.add(option.label);
                        }
                        actions.notifyDataSetChanged();
                    });
                }
            });
        }
    }

    private void showSpecialKeysMenu() {
        List<MenuOption> options = new ArrayList<>();

        if(!PreferenceConfiguration.readPreferences(game).disableDefaultExtraKeys){
            options.add(new MenuOption(getString(R.string.game_menu_send_keys_esc),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_ESCAPE})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_f11),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_F11})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_alt_f4),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_F4})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_alt_enter),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_RETURN})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_ctrl_v),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL, KeyboardTranslator.VK_V})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_win),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_win_d),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_D})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_win_g),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_G})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_ctrl_alt_tab),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL, KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_TAB})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_shift_tab),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_TAB})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_win_shift_left),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_LEFT})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_ctrl_alt_shift_f1),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL,KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_F1})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_ctrl_alt_shift_f12),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL,KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_F12})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_alt_b),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_B})));
        }

        // Import custom shortcuts
        SharedPreferences preferences = game.getSharedPreferences(PREF_NAME, Activity.MODE_PRIVATE);
        String value = preferences.getString(KEY_NAME,"");

        if(!TextUtils.isEmpty(value)){
            try {
                KeyConfigHelper.ShortcutFile shortcutFile = KeyConfigHelper.parseShortcutFile(value);
                if (shortcutFile != null && shortcutFile.data != null && !shortcutFile.data.isEmpty()) {
                    List<KeyConfigHelper.Shortcut> data = shortcutFile.data;
                    for (KeyConfigHelper.Shortcut sc : data) {
                        List<String> keys = sc.keys;
                        short[] keyCodes = new short[keys.size()];

                        for (int i = 0; i < keys.size(); i++) {
                            String code = keys.get(i);
                            int keycode;

                            if (code.startsWith("0x")) {               // literal hex value
                                keycode = Integer.parseInt(code.substring(2), 16);
                            } else if (code.startsWith("VK_")) {       // symbolic constant in KeyMapper
                                Field field = KeyMapper.class.getDeclaredField(code);
                                keycode = field.getInt(null);
                            } else {                                   // unsupported
                                throw new IllegalArgumentException("Unknown key code: " + code);
                            }
                            keyCodes[i] = (short) keycode;
                        }

                        // Whatever MenuOption looks like in your project
                        MenuOption option = new MenuOption(sc.name, () -> sendKeys(keyCodes));
                        options.add(option);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(game,getString(R.string.wrong_import_format),Toast.LENGTH_SHORT).show();
            }
        }
        options.add(new MenuOption(getString(R.string.game_menu_cancel), null));

        showMenuDialog(getString(R.string.game_menu_send_keys), options.toArray(new MenuOption[options.size()]));
    }

    private void showAdvancedMenu(GameInputDevice device) {
        List<MenuOption> options = new ArrayList<>();

        if (game.allowChangeMouseMode) {
            options.add(new MenuOption(getString(R.string.game_menu_select_mouse_mode), true, () -> game.selectMouseMode(dialogScreenContext)));
        }
        
        options.add(new MenuOption(getString(R.string.game_menu_toggle_hud), true, game::toggleHUD));
        options.add(new MenuOption(getString(R.string.game_menu_toggle_floating_button), true, game::toggleFloatingButtonVisibility));
        options.add(new MenuOption(getString(R.string.game_menu_toggle_keyboard_model), true, game::toggleKeyboardController));
        if (!game.isOnExternalDisplay()) {
            options.add(new MenuOption(getString(R.string.game_menu_toggle_virtual_model), true, game::toggleVirtualController));
        }
        options.add(new MenuOption(getString(R.string.game_menu_toggle_virtual_keyboard_model), true, game::toggleFullKeyboard));
        options.add(new MenuOption(getString(R.string.game_menu_task_manager), true, () -> sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_ESCAPE})));

        // **FIXED:** This is a UI navigation action, so it should not use withGameFocus.
        options.add(new MenuOption(getString(R.string.game_menu_send_keys), () -> {
            hideMenu();
            showSpecialKeysMenu();
        }));

        options.add(new MenuOption(getString(R.string.game_menu_switch_touch_sensitivity_model), true, game::switchTouchSensitivity));
        if (device != null) {
            options.addAll(device.getGameMenuOptions());
        }
        options.add(new MenuOption(getString(R.string.game_menu_cancel), null));
        showMenuDialog(getString(R.string.game_menu_advanced), options.toArray(new MenuOption[options.size()]));
    }

    private void showServerCmd(ArrayList<String> serverCmds) {
        List<MenuOption> options = new ArrayList<>();

        AtomicInteger index = new AtomicInteger(0);
        for (String str : serverCmds) {
            final int finalI = index.getAndIncrement();
            options.add(new MenuOption("> " + str, true, () -> game.sendExecServerCmd(finalI)));
        };

        options.add(new MenuOption(getString(R.string.game_menu_cancel), null));

        showMenuDialog(getString(R.string.game_menu_server_cmd), options.toArray(new MenuOption[options.size()]));
    }


    private int dp(float value) { return Math.round(value * game.getResources().getDisplayMetrics().density); }

    private TextView panelText(Context context, String value, float size, boolean bold) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        text.setTextColor(Color.WHITE);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }

    private LinearLayout panelCard(Context context, LinearLayout parent, String title) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(13), dp(16), dp(13));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(35, 34, 49));
        bg.setCornerRadius(dp(16));
        card.setBackground(bg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(10);
        parent.addView(card, params);
        TextView heading = panelText(context, title.toUpperCase(java.util.Locale.ROOT), 11, true);
        heading.setTextColor(Color.rgb(177, 157, 255));
        card.addView(heading);
        return card;
    }

    private void addPanelLine(LinearLayout card, String label, String value) {
        LinearLayout line = new LinearLayout(card.getContext());
        line.setGravity(android.view.Gravity.CENTER_VERTICAL);
        line.setPadding(0, dp(8), 0, dp(2));
        TextView left = panelText(card.getContext(), label, 14, false);
        left.setTextColor(Color.rgb(205, 204, 216));
        TextView right = panelText(card.getContext(), value, 14, true);
        right.setGravity(android.view.Gravity.RIGHT);
        line.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
        line.addView(right, new LinearLayout.LayoutParams(-2, -2));
        card.addView(line);
    }

    private TextView panelButton(Context context, String label, Runnable action, int backgroundColor) {
        TextView button = panelText(context, label, 14, true);
        button.setGravity(android.view.Gravity.CENTER);
        button.setTextColor(Color.WHITE);
        button.setMinHeight(dp(44));
        button.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable background = new GradientDrawable();
        background.setColor(backgroundColor);
        background.setCornerRadius(dp(12));
        button.setBackground(background);
        button.setClickable(true);
        button.setFocusable(true);
        button.setOnClickListener(v -> { hideMenu(); action.run(); });
        return button;
    }

    private void addPanelButton(LinearLayout parent, String label, Runnable action, int color) {
        TextView button = panelButton(parent.getContext(), label, action, color);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(44));
        params.bottomMargin = dp(7);
        parent.addView(button, params);
    }

    private void showMoreMenu(GameInputDevice device) {
        List<MenuOption> options = new ArrayList<>();
        options.add(new MenuOption(getString(R.string.game_menu_upload_clipboard), true, () -> game.sendClipboard(true)));
        options.add(new MenuOption(getString(R.string.game_menu_fetch_clipboard), true, () -> game.getClipboard(0)));
        options.add(new MenuOption(getString(R.string.game_menu_server_cmd), true, () -> {
            ArrayList<String> cmds = game.getServerCmds();
            hideMenu();
            if (cmds.isEmpty()) {
                new AlertDialog.Builder(new ContextThemeWrapper(dialogScreenContext, game.getApplicationInfo().theme))
                        .setTitle(R.string.game_dialog_title_server_cmd_empty)
                        .setMessage(R.string.game_dialog_message_server_cmd_empty).show();
            } else showServerCmd(cmds);
        }));
        options.add(new MenuOption(getString(R.string.game_menu_toggle_keyboard), true, game::toggleKeyboard));
        options.add(new MenuOption(getString(game.isZoomModeEnabled() ? R.string.game_menu_disable_zoom_mode :
                R.string.game_menu_enable_zoom_mode), true, game::toggleZoomMode));
        if (dialogScreenContext == game) options.add(new MenuOption(getString(R.string.game_menu_rotate_screen), true, game::rotateScreen));
        options.add(new MenuOption(getString(R.string.game_menu_advanced), true, () -> showAdvancedMenu(device)));
        options.add(new MenuOption(getString(R.string.game_menu_send_keys), () -> { hideMenu(); showSpecialKeysMenu(); }));
        options.add(new MenuOption(getString(R.string.game_menu_cancel), null));
        showMenuDialog(getString(R.string.quick_menu_title), options.toArray(new MenuOption[0]));
    }

    private void showStreamControlPanel(GameInputDevice device) {
        Context context = new ContextThemeWrapper(dialogScreenContext, game.getApplicationInfo().theme);
        int currentBitrate = Math.max(500, game.getConfiguredStreamBitrate());
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(12), dp(18), dp(12));
        content.setBackgroundColor(Color.rgb(20, 19, 29));

        TextView eyebrow = panelText(context, "OCCULTATION", 11, true);
        eyebrow.setTextColor(Color.rgb(177, 157, 255));
        content.addView(eyebrow);
        TextView title = panelText(context, "Stream controls", 24, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.bottomMargin = dp(14);
        content.addView(title, titleParams);

        LinearLayout video = panelCard(context, content, "Video");
        addPanelLine(video, "Resolution", game.getConfiguredStreamWidth() + " × " + game.getConfiguredStreamHeight());
        addPanelLine(video, "Target frame rate", game.getConfiguredStreamFps() + " FPS");
        TextView bitrateValue = panelText(context, "Bitrate · " + String.format(java.util.Locale.ROOT, "%.1f Mbps", currentBitrate / 1000.0), 15, true);
        bitrateValue.setPadding(0, dp(12), 0, dp(2));
        video.addView(bitrateValue);
        SeekBar bitrate = new SeekBar(context);
        bitrate.setMax(299500);
        bitrate.setProgress(currentBitrate - 500);
        video.addView(bitrate, new LinearLayout.LayoutParams(-1, -2));
        TextView hint = panelText(context, "Saved for your next stream. The current native streaming API does not support live bitrate changes.", 11, false);
        hint.setTextColor(Color.LTGRAY);
        video.addView(hint);
        bitrate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                bitrateValue.setText("Bitrate · " + String.format(java.util.Locale.ROOT, "%.1f Mbps", (progress + 500) / 1000.0));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {
                game.saveStreamBitrateForNextSession(bar.getProgress() + 500);
                Toast.makeText(game, "Bitrate saved for next stream", Toast.LENGTH_SHORT).show();
            }
        });

        LinearLayout network = panelCard(context, content, "Network");
        addPanelLine(network, "Connection", game.getStreamConnectionStatus());
        addPanelLine(network, "Estimated RTT", game.getEstimatedNetworkRtt());
        addPanelLine(network, "WAN optimization", game.isWanOptimizationEnabled() ? "Enabled" : "Disabled");

        LinearLayout performance = panelCard(context, content, "Performance");
        addPanelLine(performance, "Performance HUD", PreferenceConfiguration.readPreferences(game).enablePerfOverlay ? "Enabled" : "Disabled");
        TextView perfHint = panelText(context, "Decoder, rendering and frame statistics are available through the performance HUD.", 12, false);
        perfHint.setTextColor(Color.LTGRAY);
        perfHint.setPadding(0, dp(7), 0, 0);
        performance.addView(perfHint);

        LinearLayout controls = panelCard(context, content, "Controls");
        addPanelLine(controls, "Native touch", game.getNativeTouchStatus());
        addPanelButton(controls, "Input mode", () -> game.selectMouseMode(dialogScreenContext), Color.rgb(76, 62, 116));
        addPanelButton(controls, "Toggle keyboard", game::toggleKeyboard, Color.rgb(48, 47, 64));
        addPanelButton(controls, "Toggle performance HUD", game::toggleHUD, Color.rgb(48, 47, 64));
        addPanelButton(controls, "More controls", () -> showMoreMenu(device), Color.rgb(48, 47, 64));

        LinearLayout sessionActions = new LinearLayout(context);
        sessionActions.setOrientation(LinearLayout.HORIZONTAL);
        TextView disconnect = panelButton(context, "Disconnect", game::disconnect, Color.rgb(91, 54, 58));
        TextView quit = panelButton(context, "Quit host app", game::quit, Color.rgb(119, 43, 58));
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(0, dp(46), 1);
        actionParams.rightMargin = dp(6);
        sessionActions.addView(disconnect, actionParams);
        LinearLayout.LayoutParams quitParams = new LinearLayout.LayoutParams(0, dp(46), 1);
        quitParams.leftMargin = dp(6);
        sessionActions.addView(quit, quitParams);
        controls.addView(sessionActions);

        TextView resume = panelButton(context, "Return to stream", this::hideMenu, Color.rgb(105, 82, 166));
        LinearLayout.LayoutParams resumeParams = new LinearLayout.LayoutParams(-1, dp(48));
        resumeParams.topMargin = dp(4);
        content.addView(resume, resumeParams);

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(context).create();
        dialog.setView(scroll, 0, 0, 0, 0);
        dialog.setOnCancelListener(d -> hideMenu());
        if (currentDialog != null) currentDialog.dismiss();
        currentDialog = dialog;
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(Math.min(game.getResources().getDisplayMetrics().widthPixels - dp(24), dp(520)),
                    Math.min(game.getResources().getDisplayMetrics().heightPixels - dp(48), dp(720)));
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

    public void showMenu(GameInputDevice device) { showStreamControlPanel(device); }

    @Override
    public void hideMenu() {
        if (currentDialog != null && currentDialog.isShowing()) {
            currentDialog.dismiss();
        }
        currentDialog = null;
    }

    @Override
    public boolean isMenuOpen() {
        return currentDialog != null && currentDialog.isShowing();
    }
}
