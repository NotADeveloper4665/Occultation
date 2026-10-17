package com.limelight.ui;

import android.os.Looper;
import static org.robolectric.Shadows.shadowOf;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import androidx.test.core.app.ApplicationProvider;
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
        DesktopControlsView view = new DesktopControlsView(ApplicationProvider.getApplicationContext(),
                () -> {}, () -> {}, () -> {}, e -> events.incrementAndGet());
        view.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 1000, 600);
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
        tap(view, 450, 300);
        assertEquals(0, events.get());
        tap(view, 800, 450);
        assertEquals(2, events.get());
        View mouse = view.getChildAt(4);
        tap(view, mouse.getLeft() + mouse.getWidth()/2f, mouse.getTop() + mouse.getHeight()/2f);
        assertEquals(2, events.get()); // Button touches never reach the remote input route.
        tap(view, 450, 300);
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
}
