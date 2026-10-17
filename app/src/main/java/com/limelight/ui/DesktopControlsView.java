package com.limelight.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import com.limelight.R;

/** Touch-only overlay for the existing remote desktop mouse mode. */
public final class DesktopControlsView extends FrameLayout {
    public interface MouseInput { void accept(MotionEvent event); }

    private final View trackpad;
    private final ImageButton eye, keyboard, home, mouse;
    private final Runnable cancelTouches;
    private final MouseInput mouseInput;
    private boolean controlsVisible = true;
    private boolean fullScreen;
    private boolean mouseGesture;

    public DesktopControlsView(Context context, Runnable showKeyboard, Runnable superKey,
                               Runnable cancelTouches, MouseInput mouseInput) {
        super(context);
        this.cancelTouches = cancelTouches;
        this.mouseInput = mouseInput;
        setMotionEventSplittingEnabled(false);
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
        eye.setOnClickListener(v -> {
            cancelTouches.run();
            controlsVisible = !controlsVisible;
            eye.setAlpha(controlsVisible ? 1f : .2f);
            keyboard.setVisibility(controlsVisible ? VISIBLE : INVISIBLE);
            home.setVisibility(controlsVisible ? VISIBLE : INVISIBLE);
            mouse.setVisibility(controlsVisible ? VISIBLE : INVISIBLE);
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
        trackpad.setVisibility(controlsVisible && !fullScreen ? VISIBLE : INVISIBLE);
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left, height = bottom - top;
        // Scale the cluster for small windows; every control remains a true circle.
        int unit = Math.min(dp(56), Math.min(width / 7, height / 4));
        int margin = Math.min(dp(24), unit / 2);
        eye.layout(margin + unit, margin, margin + unit * 2, margin + unit);
        int base = height - margin;
        home.layout(margin, base - unit * 3, margin + unit, base - unit * 2);
        int large = unit * 3 / 2;
        keyboard.layout(margin + unit / 2, base - large, margin + unit / 2 + large, base);
        mouse.layout(margin + unit * 2, base - unit * 2,
                margin + unit * 3, base - unit);
        trackpad.layout(width * 56 / 100, Math.max(margin + unit, height * 38 / 100),
                width - margin, height - margin);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        // Leave physical mouse/stylus input on the established input route.
        if (event.getToolType(0) != MotionEvent.TOOL_TYPE_FINGER) return false;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
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
        cancelTouches.run();
        super.onDetachedFromWindow();
    }
}
