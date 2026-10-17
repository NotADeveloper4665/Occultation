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

    @Test public void desktopTapRightClicksAndReleases() {
        NvConnection conn = mock(NvConnection.class);
        RelativeTouchContext touch = context(conn, true);
        touch.touchUpEvent(100, 100, 100);
        verify(conn).sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(110));
        verify(conn).sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT);
        verify(conn, never()).sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT);
    }

    @Test public void holdLeftDragsAndCancellationReleases() {
        NvConnection conn = mock(NvConnection.class);
        RelativeTouchContext touch = context(conn, true);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(660));
        verify(conn).sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT);
        touch.touchMoveEvent(130, 120, 700);
        verify(conn).sendMouseMove((short)30, (short)20);
        touch.cancelTouch();
        verify(conn).sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);
        verify(conn, never()).sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT);
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
}
