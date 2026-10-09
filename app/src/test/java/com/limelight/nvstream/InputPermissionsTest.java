package com.limelight.nvstream;
import androidx.test.core.app.ApplicationProvider;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.jni.MoonBridge;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, shadows = InputPermissionsTest.PacketShadow.class)
public class InputPermissionsTest {
    @Implements(value = MoonBridge.class, isInAndroidSdk = false)
    public static class PacketShadow {
        static int packets;
        @Implementation protected static void __staticInitializer__() {}
        @Implementation protected static void sendExecServerCmd(int cmdId) { packets++; }
        @Implementation protected static void sendEmptyPayload() { packets++; }
        @Implementation protected static void sendMouseMove(short deltaX, short deltaY) { packets++; }
        @Implementation protected static void sendMousePosition(short x, short y, short referenceWidth, short referenceHeight) { packets++; }
        @Implementation protected static void sendMouseMoveAsMousePosition(short deltaX, short deltaY, short referenceWidth, short referenceHeight) { packets++; }
        @Implementation protected static void sendMouseButton(byte buttonEvent, byte mouseButton) { packets++; }
        @Implementation protected static void sendMultiControllerInput(short controllerNumber, short activeGamepadMask, int buttonFlags, byte leftTrigger, byte rightTrigger, short leftStickX, short leftStickY, short rightStickX, short rightStickY) { packets++; }
        @Implementation protected static int sendTouchEvent(byte eventType, int pointerId, float x, float y, float pressure, float contactAreaMajor, float contactAreaMinor, short rotation) { packets++; return 0; }
        @Implementation protected static int sendPenEvent(byte eventType, byte toolType, byte penButtons, float x, float y, float pressure, float contactAreaMajor, float contactAreaMinor, short rotation, byte tilt) { packets++; return 0; }
        @Implementation protected static int sendControllerArrivalEvent(byte controllerNumber, short activeGamepadMask, byte type, int supportedButtonFlags, short capabilities) { packets++; return 0; }
        @Implementation protected static int sendControllerTouchEvent(byte controllerNumber, byte eventType, int pointerId, float x, float y, float pressure) { packets++; return 0; }
        @Implementation protected static int sendControllerMotionEvent(byte controllerNumber, byte motionType, float x, float y, float z) { packets++; return 0; }
        @Implementation protected static int sendControllerBatteryEvent(byte controllerNumber, byte batteryState, byte batteryPercentage) { packets++; return 0; }
        @Implementation protected static void sendKeyboardInput(short keyMap, byte keyDirection, byte modifier, byte flags) { packets++; }
        @Implementation protected static void sendMouseHighResScroll(short scrollAmount) { packets++; }
        @Implementation protected static void sendMouseHighResHScroll(short scrollAmount) { packets++; }
        @Implementation protected static void sendUtf8Text(String text) { packets++; }
    }

    private NvConnection connection() {
        return new NvConnection(ApplicationProvider.getApplicationContext(),
                new ComputerDetails.AddressTuple("localhost", 47989), 47984, "test", null, null, null);
    }
    private void input(NvConnection c) {
        c.sendKeyboardInput((short)1, (byte)0, (byte)0, (byte)0);
        c.sendUtf8Text("paste"); c.sendMouseMove((short)1,(short)2);
        c.sendMousePosition((short)1,(short)2,(short)100,(short)100);
        c.sendMouseMoveAsMousePosition((short)1,(short)2,(short)100,(short)100);
        c.sendMouseButtonDown((byte)1); c.sendMouseButtonUp((byte)1);
        c.sendMouseScroll((byte)1); c.sendMouseHScroll((byte)1);
        c.sendMouseHighResScroll((short)1); c.sendMouseHighResHScroll((short)1);
        c.sendTouchEvent((byte)0,0,0,0,0,0,0,(short)0);
        c.sendPenEvent((byte)0,(byte)0,(byte)0,0,0,0,0,0,(short)0,(byte)0);
        c.sendControllerInput((short)0,(short)1,0,(byte)0,(byte)0,(short)0,(short)0,(short)0,(short)0);
        c.sendControllerArrivalEvent((byte)0,(short)1,(byte)0,0,(short)0);
        c.sendControllerTouchEvent((byte)0,(byte)0,0,0,0,0);
        c.sendControllerMotionEvent((byte)0,(byte)0,0,0,0);
        c.sendControllerBatteryEvent((byte)0,(byte)0,(byte)0);
    }
    @Test public void viewOnlyBlocksEveryInputPacketAndCommands() {
        NvConnection c = connection(); c.configureInputPermissions(true,true,true,true);
        PacketShadow.packets = 0;
        {
            input(c); c.sendExecServerCmd(1); assertEquals(0, PacketShadow.packets);
        }
    }
    @Test public void disablingAllCategoriesBlocksEveryInputPacket() {
        NvConnection c = connection(); c.configureInputPermissions(false,false,false,false);
        PacketShadow.packets = 0;
        {
            input(c); assertEquals(0, PacketShadow.packets);
        }
    }
    @Test public void keyboardRestrictionKeepsMouseAvailable() {
        NvConnection c = connection(); c.configureInputPermissions(false,false,true,true);
        PacketShadow.packets = 0;
        {
            c.sendKeyboardInput((short)1,(byte)0,(byte)0,(byte)0); c.sendUtf8Text("paste");
            c.sendMouseMove((short)1,(short)2);
            assertEquals(1, PacketShadow.packets);
        }
    }
}
