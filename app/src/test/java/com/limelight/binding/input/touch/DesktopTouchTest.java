package com.limelight.binding.input.touch;

import android.os.Looper;
import android.view.View;
import androidx.test.core.app.ApplicationProvider;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.MouseButtonPacket;
import com.limelight.preferences.PreferenceConfiguration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.time.Duration;
import static org.mockito.Mockito.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class DesktopTouchTest {
    private RelativeTouchContext context(NvConnection conn, boolean desktop) {
        View view = new View(ApplicationProvider.getApplicationContext());
        view.layout(0, 0, 1280, 720);
        PreferenceConfiguration prefs = new PreferenceConfiguration();
        prefs.touchPadSensitivity = prefs.touchPadYSensitity = 100;
        RelativeTouchContext touch = new RelativeTouchContext(conn, 0, 1280, 720, view, prefs, desktop);
        touch.setPointerCount(1);
        touch.touchDownEvent(100, 100, 0, true);
        return touch;
    }

    @Test public void desktopTapLeftClicksAndReleases() {
        NvConnection conn = mock(NvConnection.class);
        RelativeTouchContext touch = context(conn, true);
        touch.touchUpEvent(100, 100, 100);
        verify(conn).sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(110));
        verify(conn).sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);
        verify(conn, never()).sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT);
    }

    @Test public void holdRightDragsAndCancellationReleases() {
        NvConnection conn = mock(NvConnection.class);
        RelativeTouchContext touch = context(conn, true);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(660));
        verify(conn).sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT);
        touch.touchMoveEvent(130, 120, 700);
        verify(conn).sendMouseMove((short)30, (short)20);
        touch.cancelTouch();
        verify(conn).sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT);
        verify(conn, never()).sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT);
    }

    @Test public void movingDoesNotClickOrStartDelayedDrag() {
        NvConnection conn = mock(NvConnection.class);
        RelativeTouchContext touch = context(conn, true);
        touch.touchMoveEvent(150, 100, 50);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(700));
        touch.touchUpEvent(150, 100, 750);
        verify(conn, never()).sendMouseButtonDown(anyByte());
    }

    @Test public void existingGamingModeKeepsLeftTap() {
        NvConnection conn = mock(NvConnection.class);
        RelativeTouchContext touch = context(conn, false);
        touch.touchUpEvent(100, 100, 100);
        verify(conn).sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(110));
        verify(conn).sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);
    }
    @Test public void twoFingerTapRightClicksWithoutAnExtraLeftClick() {
        NvConnection conn = mock(NvConnection.class);
        RelativeTouchContext primary = context(conn, true);
        View view = new View(ApplicationProvider.getApplicationContext()); view.layout(0, 0, 1280, 720);
        RelativeTouchContext second = new RelativeTouchContext(conn, 1, 1280, 720, view, new PreferenceConfiguration(), true);
        primary.setPointerCount(2); second.setPointerCount(2);
        second.touchDownEvent(200, 100, 10, true);
        second.touchUpEvent(200, 100, 100);
        primary.touchUpEvent(100, 100, 120);
        verify(conn).sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT);
        verify(conn, never()).sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(110));
        verify(conn).sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT);
    }

}
