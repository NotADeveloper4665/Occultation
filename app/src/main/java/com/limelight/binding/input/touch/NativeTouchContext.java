package com.limelight.binding.input.touch;

import android.view.View;

import com.limelight.nvstream.jni.MoonBridge;

/**
 * Sends real pointer contacts instead of translating finger gestures into mouse buttons.
 * Coordinates are normalized to the streamed view, as expected by Moonlight's touch API.
 */
public final class NativeTouchContext implements TouchContext {
    private final int pointerId;
    private final View targetView;
    private boolean active;
    private boolean cancelled;

    public NativeTouchContext(int pointerId, View targetView) {
        this.pointerId = pointerId;
        this.targetView = targetView;
    }

    private float normalized(int value, int extent) {
        if (extent <= 0) return 0f;
        return Math.max(0f, Math.min(1f, (float) value / (float) extent));
    }

    private void send(byte eventType, int x, int y) {
        int width = targetView.getWidth();
        int height = targetView.getHeight();
        if (width <= 0 || height <= 0) return;
        MoonBridge.sendTouchEvent(eventType, pointerId,
                normalized(x, width), normalized(y, height),
                eventType == MoonBridge.LI_TOUCH_EVENT_UP ? 0f : 1f,
                0f, 0f, (short) 0);
    }

    @Override public int getActionIndex() { return pointerId; }

    @Override
    public boolean touchDownEvent(int eventX, int eventY, long eventTime, boolean isNewFinger) {
        if (cancelled || !isNewFinger || active) return true;
        active = true;
        send(MoonBridge.LI_TOUCH_EVENT_DOWN, eventX, eventY);
        return true;
    }

    @Override
    public boolean touchMoveEvent(int eventX, int eventY, long eventTime) {
        if (active && !cancelled) send(MoonBridge.LI_TOUCH_EVENT_MOVE, eventX, eventY);
        return true;
    }

    @Override
    public void touchUpEvent(int eventX, int eventY, long eventTime) {
        if (!active) return;
        send(MoonBridge.LI_TOUCH_EVENT_UP, eventX, eventY);
        active = false;
    }

    @Override
    public void cancelTouch() {
        if (active) {
            send(MoonBridge.LI_TOUCH_EVENT_CANCEL, 0, 0);
            active = false;
        }
        cancelled = true;
    }

    @Override public boolean isCancelled() { return cancelled; }

    @Override public void setPointerCount(int pointerCount) {
        // Native touch supports concurrent pointers; do not collapse gestures to a mouse.
    }
}
