package com.limelight.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Button;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import androidx.appcompat.widget.AppCompatButton;
import android.view.Gravity;
import com.limelight.overlay.OverlayLayout;
import com.limelight.overlay.OverlayKeys;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import com.limelight.R;

/** Touch-only overlay for the existing remote desktop mouse mode. */
public final class DesktopControlsView extends FrameLayout {
    public interface MouseInput { void accept(MotionEvent event); }

    private final View trackpad;
    private final ImageButton eye, keyboard, home, mouse;
    private final Runnable cancelTouches;
    private final MouseInput mouseInput;
    public interface KeyInput { void send(int key, boolean down); }
    public interface Selection { void select(OverlayLayout.Item item); }
    private OverlayLayout layout = OverlayLayout.defaults();
    private final Map<String, View> controls = new LinkedHashMap<>();
    private final Map<View, int[]> pressedButtons = new LinkedHashMap<>();
    private KeyInput keyInput = (key, down) -> {};
    private Selection selection;
    private OverlayLayout.Item dragging;
    private boolean editing;
    private float dragX, dragY;
    private boolean controlsVisible = true;
    private boolean fullScreen;
    private boolean mouseGesture;

    public DesktopControlsView(Context context, Runnable showKeyboard, Runnable superKey,
                               Runnable cancelTouches, MouseInput mouseInput) {
        super(context);
        this.cancelTouches = cancelTouches;
        this.mouseInput = mouseInput;
        setMotionEventSplittingEnabled(true);
        setFocusable(false);
        trackpad = new View(context);
        GradientDrawable pad = background(false);
        pad.setCornerRadius(dp(28));
        trackpad.setBackground(pad);
        trackpad.setContentDescription(context.getString(R.string.desktop_trackpad));
        addView(trackpad);
        eye = button(R.drawable.ic_desktop_eye, R.string.desktop_controls_toggle);
        keyboard = button(R.drawable.ic_desktop_keyboard, R.string.desktop_keyboard);
        home = button(R.drawable.ic_desktop_home, R.string.desktop_home);
        mouse = button(R.drawable.ic_desktop_mouse, R.string.desktop_mouse);
        controls.put("trackpad", trackpad); controls.put("eye", eye);
        controls.put("keyboard", keyboard); controls.put("home", home); controls.put("mouse", mouse);
        eye.setElevation(dp(2));
        eye.setOnClickListener(v -> {
            cancelTouches.run();
            controlsVisible = !controlsVisible;
            eye.setAlpha(controlsVisible ? 1f : .2f);
            releaseKeys();
            for (Map.Entry<String, View> control : controls.entrySet()) {
                if (control.getValue() != eye && control.getValue() != trackpad)
                    control.getValue().setVisibility(controlsVisible ? VISIBLE : INVISIBLE);
            }
            updateTrackpad();
        });
        keyboard.setOnClickListener(v -> showKeyboard.run());
        home.setOnClickListener(v -> superKey.run());
        mouse.setOnClickListener(v -> {
            cancelTouches.run();
            fullScreen = !fullScreen;
            mouse.setSelected(fullScreen);
            mouse.setAlpha(fullScreen ? 1f : .7f);
            updateTrackpad();
        });
        mouse.setAlpha(.7f);
        // Keep the overlay above existing floating controls without stealing keyboard focus.
        setElevation(dp(10000));
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable background(boolean circle) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(circle ? GradientDrawable.OVAL : GradientDrawable.RECTANGLE);
        drawable.setColor(Color.argb(95, 102, 55, 166));
        drawable.setStroke(dp(1), Color.argb(165, 200, 166, 245));
        return drawable;
    }

    private Button actionButton(OverlayLayout.Item item, int icon) {
        AppCompatButton button = new AppCompatButton(getContext());
        androidx.core.view.ViewCompat.setBackgroundTintList(button, null);
        button.setText(item.label());
        button.setTextColor(0xFFF4EAFF);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinWidth(0); button.setMinimumWidth(0);
        button.setMinHeight(0); button.setMinimumHeight(0);
        button.setIncludeFontPadding(false);
        button.setMaxLines(5);
        androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(button, 6, 11, 1, TypedValue.COMPLEX_UNIT_SP);
        button.setBackground(background(true));
        button.setContentDescription(item.label());
        button.setFocusable(false);
        if (icon != 0) {
            Drawable drawable = getResources().getDrawable(icon, getContext().getTheme());
            drawable.setBounds(0, 0, dp(18), dp(18));
            button.setCompoundDrawables(null, drawable, null, null);
            button.setCompoundDrawablePadding(dp(1));
        }
        return button;
    }

    private ImageButton button(int icon, int description) {
        ImageButton button = new ImageButton(getContext());
        button.setImageResource(icon);
        button.setBackground(background(true));
        button.setPadding(dp(14), dp(14), dp(14), dp(14));
        button.setScaleType(ImageButton.ScaleType.FIT_CENTER);
        button.setContentDescription(getContext().getString(description));
        button.setFocusable(false);
        addView(button);
        return button;
    }

    private void updateTrackpad() {
        for (OverlayLayout.Item item : layout.items) {
            View view = controls.get(item.id);
            if (view != null && "mouse".equals(item.function())) {
                view.setSelected(fullScreen); view.setAlpha(fullScreen ? 1f : .7f);
            }
        }
        trackpad.setVisibility(controlsVisible && !fullScreen ? VISIBLE : INVISIBLE);
    }

    public void setConfiguration(OverlayLayout value, KeyInput input) {
        // Persistence/import boundaries validate files. An editor draft may have an empty name.
        releaseKeys();
        layout = value;
        keyInput = input;
        removeAllViews();
        controls.clear();
        for (OverlayLayout.Item item : layout.items) {
            View view;
            if ("trackpad".equals(item.type)) view = trackpad;
            else if ("eye".equals(item.type)) view = eye;
            else if ("key".equals(item.function())) {
                Button key = actionButton(item, 0);
                key.setOnTouchListener((v, event) -> {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN && !pressedButtons.containsKey(v)) {
                        Set<Integer> previouslyHeld = heldKeys();
                        int[] codes = orderedKeys(item.keyboardKeys());
                        pressedButtons.put(v, codes);
                        for (int code : codes) if (!previouslyHeld.contains(code)) keyInput.send(code, true);
                        v.setAlpha(1f);
                    } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                            || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                        int[] released = pressedButtons.remove(v);
                        Set<Integer> stillHeld = heldKeys();
                        if (released != null) for (int i = released.length - 1; i >= 0; i--)
                            if (!stillHeld.contains(released[i])) keyInput.send(released[i], false);
                        v.setAlpha(.8f);
                    }
                    return true;
                });
                key.setAlpha(.8f); view = key;
            } else {
                int icon = "keyboard".equals(item.function()) ? R.drawable.ic_desktop_keyboard
                        : "home".equals(item.function()) ? R.drawable.ic_desktop_home
                        : "mouse".equals(item.function()) ? R.drawable.ic_desktop_mouse : R.drawable.ic_desktop_eye;
                Button button = actionButton(item, icon);
                button.setOnClickListener(v -> {
                    switch (item.function()) {
                        case "keyboard": keyboard.performClick(); break;
                        case "home": home.performClick(); break;
                        case "mouse": mouse.performClick(); break;
                        case "eye": eye.performClick(); break;
                    }
                });
                view = button;
            }
            addView(view);
            if (view != eye && view != trackpad) view.setVisibility(controlsVisible ? VISIBLE : INVISIBLE);
            controls.put(item.id, view);
        }
        updateTrackpad();
        requestLayout();
    }

    public void selectControl(String id) {
        for (Map.Entry<String, View> control : controls.entrySet()) {
            GradientDrawable background = (GradientDrawable) control.getValue().getBackground();
            background.setStroke(dp(control.getKey().equals(id) ? 3 : 1),
                    control.getKey().equals(id) ? 0xFFF4EAFF : 0xA5C8A6F5);
        }
    }

    public void setEditing(Selection listener) {
        editing = true; selection = listener;
    }

    private Set<Integer> heldKeys() {
        Set<Integer> held = new HashSet<>();
        for (int[] keys : pressedButtons.values()) for (int key : keys) held.add(key);
        return held;
    }

    private static int[] orderedKeys(int[] keys) {
        int[] ordered = new int[keys.length]; int index = 0;
        for (int key : keys) if (OverlayKeys.modifier(key)) ordered[index++] = key;
        for (int key : keys) if (!OverlayKeys.modifier(key)) ordered[index++] = key;
        return ordered;
    }

    public void releaseKeys() {
        Set<Integer> held = heldKeys();
        int[] codes = new int[held.size()]; int index = 0;
        for (int key : held) codes[index++] = key;
        codes = orderedKeys(codes);
        for (int i = codes.length - 1; i >= 0; i--) keyInput.send(codes[i], false);
        pressedButtons.clear();
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left, height = bottom - top;
        for (OverlayLayout.Item item : layout.items) {
            View control = controls.get(item.id);
            if (control == null) continue;
            int w, h;
            if ("trackpad".equals(item.type)) {
                w = Math.round(width * item.width); h = Math.round(height * item.height);
            } else {
                w = h = Math.min(Math.min(width, height), Math.max(1,
                        Math.round(Math.min(width, height) * item.size)));
            }
            int x = Math.max(0, Math.min(width - w, Math.round(item.x * width - w / 2f)));
            int y = Math.max(0, Math.min(height - h, Math.round(item.y * height - h / 2f)));
            if (control instanceof Button) {
                // Text must be measured at the actual circular control size, not
                // the full overlay size inherited from FrameLayout's defaults.
                int inset = Math.round(w * .15f);
                control.setPadding(inset, Math.round(h * .10f), inset, Math.round(h * .10f));
            }
            control.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
            control.layout(x, y, x + w, y + h);
        }
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        return editing || super.onInterceptTouchEvent(event);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        // Leave physical mouse/stylus input on the established input route.
        if (event.getToolType(0) != MotionEvent.TOOL_TYPE_FINGER) return false;
        if (!editing && event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            // The original in-stream menu and zoom toggle remain reachable.
            for (int id : new int[]{R.id.floatingMenuButton, R.id.overlayToggleZoomButton}) {
                View control = getRootView().findViewById(id);
                Rect bounds = new Rect();
                if (control != null && control.isShown() && control.getGlobalVisibleRect(bounds)
                        && bounds.contains((int) event.getRawX(), (int) event.getRawY())) {
                    return false;
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (editing) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                dragging = null;
                for (int i = layout.items.size() - 1; i >= 0; i--) {
                    OverlayLayout.Item item = layout.items.get(i);
                    View control = controls.get(item.id);
                    if (event.getX() >= control.getLeft() && event.getX() <= control.getRight()
                            && event.getY() >= control.getTop() && event.getY() <= control.getBottom()) {
                        dragging = item; selection.select(item); break;
                    }
                }
                dragX = event.getX(); dragY = event.getY();
            } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE && dragging != null) {
                dragging.x = Math.max(0, Math.min(1, dragging.x + (event.getX() - dragX) / Math.max(1, getWidth())));
                dragging.y = Math.max(0, Math.min(1, dragging.y + (event.getY() - dragY) / Math.max(1, getHeight())));
                dragX = event.getX(); dragY = event.getY(); requestLayout();
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) dragging = null;
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            // Buttons are dispatched to their own views first. Keep this decision
            // for the complete gesture, including moves outside the pad bounds.
            mouseGesture = fullScreen || (event.getX() >= trackpad.getLeft()
                    && event.getX() <= trackpad.getRight()
                    && event.getY() >= trackpad.getTop()
                    && event.getY() <= trackpad.getBottom());
        }
        if (mouseGesture) mouseInput.accept(event);
        if (event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL) mouseGesture = false;
        // Consume unused touchscreen space so it cannot fall through to the stream.
        return true;
    }

    @Override protected void onDetachedFromWindow() {
        releaseKeys();
        cancelTouches.run();
        super.onDetachedFromWindow();
    }
}
