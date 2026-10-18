package com.limelight.ui;

import android.app.Activity;
import org.robolectric.Robolectric;
import android.os.Looper;
import static org.robolectric.Shadows.shadowOf;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class DesktopControlsTest {
    private DesktopControlsView overlay(AtomicInteger events) {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        DesktopControlsView view = new DesktopControlsView(activity,
                () -> {}, () -> {}, () -> {}, e -> events.incrementAndGet());
        activity.setContentView(view);
        view.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 1000, 600);
        shadowOf(Looper.getMainLooper()).idle();
        return view;
    }

    private void tap(DesktopControlsView view, float x, float y) {
        MotionEvent.PointerProperties pointer = new MotionEvent.PointerProperties();
        pointer.id = 0;
        pointer.toolType = MotionEvent.TOOL_TYPE_FINGER;
        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        coords.x = x; coords.y = y;
        for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
            MotionEvent e = MotionEvent.obtain(0, 50, action, 1,
                    new MotionEvent.PointerProperties[]{pointer}, new MotionEvent.PointerCoords[]{coords},
                    0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
            view.dispatchTouchEvent(e);
            e.recycle();
        }
        shadowOf(Looper.getMainLooper()).idle();
    }

    @Test public void confinesMouseAndFullScreenToggleExpandsArea() {
        AtomicInteger events = new AtomicInteger();
        DesktopControlsView view = overlay(events);
        tap(view, view.getWidth() * .45f, view.getHeight() * .3f);
        assertEquals(0, events.get());
        View pad = view.getChildAt(0);
        tap(view, pad.getLeft() + pad.getWidth()/2f, pad.getTop() + pad.getHeight()/2f);
        assertEquals(2, events.get());
        View mouse = view.getChildAt(4);
        tap(view, mouse.getLeft() + mouse.getWidth()/2f, mouse.getTop() + mouse.getHeight()/2f);
        assertEquals(2, events.get()); // Button touches never reach the remote input route.
        tap(view, view.getWidth() * .45f, view.getHeight() * .3f);
        assertEquals(4, events.get());
    }

    @Test public void eyeHidesClusterAndReturnsFaintly() {
        DesktopControlsView view = overlay(new AtomicInteger());
        view.getChildAt(1).performClick();
        assertEquals(.2f, view.getChildAt(1).getAlpha(), .001f);
        for (int i : new int[]{0, 2, 3, 4}) assertEquals(View.INVISIBLE, view.getChildAt(i).getVisibility());
        view.getChildAt(1).performClick();
        for (int i = 0; i < 5; i++) assertEquals(View.VISIBLE, view.getChildAt(i).getVisibility());
        for (int i : new int[]{2, 3, 4}) {
            assertEquals(view.getChildAt(i).getWidth(), view.getChildAt(i).getHeight());
        }
    }
    @Test public void customShiftButtonLabelsItselfAndReleasesWhenHidden() {
        DesktopControlsView view = overlay(new AtomicInteger());
        com.limelight.overlay.OverlayLayout layout = com.limelight.overlay.OverlayLayout.defaults();
        com.limelight.overlay.OverlayLayout.Item shift = new com.limelight.overlay.OverlayLayout.Item("shift", "key", .5f, .5f, .12f);
        shift.key = 0xA0; layout.items.add(shift);
        java.util.List<String> events = new java.util.ArrayList<>();
        view.setConfiguration(layout, (key, down) -> events.add(key + ":" + down));
        android.widget.Button button = (android.widget.Button) view.getChildAt(5);
        assertEquals("Shift", button.getText().toString());
        MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 1, 1, 0);
        button.dispatchTouchEvent(down); down.recycle();
        assertEquals(java.util.Arrays.asList("160:true"), events);
        view.getChildAt(1).performClick();
        assertEquals(java.util.Arrays.asList("160:true", "160:false"), events);
    }

    @Test public void editingPreviewDoesNotSendMouseOrKeys() {
        AtomicInteger mouse = new AtomicInteger(), keys = new AtomicInteger();
        DesktopControlsView view = overlay(mouse);
        com.limelight.overlay.OverlayLayout layout = com.limelight.overlay.OverlayLayout.defaults();
        view.setConfiguration(layout, (key, down) -> keys.incrementAndGet());
        view.setEditing(item -> {});
        tap(view, view.getWidth() * .77f, view.getHeight() * .68f);
        assertEquals(0, mouse.get()); assertEquals(0, keys.get());
    }

    @Test public void clusterShortcutReleasesInReverseOrderAndCanBecomeBuiltin() {
        DesktopControlsView view = overlay(new AtomicInteger());
        com.limelight.overlay.OverlayLayout layout = com.limelight.overlay.OverlayLayout.defaults();
        layout.items.get(2).assign("key", new int[]{0x43, 0xA2});
        java.util.List<String> events = new java.util.ArrayList<>();
        view.setConfiguration(layout, (key, down) -> events.add(key + ":" + down));
        android.widget.Button key = (android.widget.Button) view.getChildAt(2);
        assertEquals("Ctrl + C", key.getText().toString());
        MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 1, 1, 0);
        key.dispatchTouchEvent(down); down.recycle(); view.releaseKeys();
        assertEquals(java.util.Arrays.asList("162:true", "67:true", "67:false", "162:false"), events);
        layout.items.get(2).assign("mouse", null);
        view.setConfiguration(layout, (code, pressed) -> {});
        assertTrue(view.getChildAt(2) instanceof android.widget.ImageButton);
        assertEquals("Full-screen mouse", view.getChildAt(2).getContentDescription().toString());
    }
    @Test public void deletedClusterButtonDisappearsFromRendering() {
        DesktopControlsView view = overlay(new AtomicInteger());
        com.limelight.overlay.OverlayLayout layout = com.limelight.overlay.OverlayLayout.defaults();
        layout.deleteButton("keyboard");
        view.setConfiguration(layout, (key, down) -> {});
        assertEquals(4, view.getChildCount());
    }

    @Test public void configuredActionsHaveVisibleLabelsAndMeasureAtTheirCircleSize() {
        DesktopControlsView view = overlay(new AtomicInteger());
        com.limelight.overlay.OverlayLayout layout = com.limelight.overlay.OverlayLayout.defaults();
        com.limelight.overlay.OverlayLayout.Item added = new com.limelight.overlay.OverlayLayout.Item("added", "key", .5f, .5f, .14f);
        added.assign("home", null); layout.items.add(added);
        view.setConfiguration(layout, (key, down) -> {});
        view.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 1000, 600);
        for (int index = 2; index < view.getChildCount(); index++) {
            android.widget.Button button = (android.widget.Button)view.getChildAt(index);
            assertEquals(layout.items.get(index).label(), button.getText().toString());
            assertEquals(button.getWidth(), button.getMeasuredWidth());
            assertEquals(button.getHeight(), button.getMeasuredHeight());
            assertEquals(button.getWidth(), button.getHeight());
        }
        assertEquals("Windows / Super", ((android.widget.Button)view.getChildAt(5)).getText().toString());
        added.assign("key", new int[]{0xA0});
        view.setConfiguration(layout.copy(), (key, down) -> {});
        assertEquals("Shift", ((android.widget.Button)view.getChildAt(5)).getText().toString());
    }
}
