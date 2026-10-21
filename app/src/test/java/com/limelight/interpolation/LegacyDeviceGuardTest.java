package com.limelight.interpolation;

import androidx.test.core.app.ApplicationProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class LegacyDeviceGuardTest {
    @Test @Config(sdk = 23)
    public void nnapiRejectsOldAndroidBeforeLoadingNativeRuntime() throws Exception {
        try {
            new NnapiRifeLite(ApplicationProvider.getApplicationContext(), 64, 64);
            fail("Must not initialize ONNX Runtime on old Android");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("Android 10"));
        }
    }
    @Test @Config(sdk = 23)
    public void vulkanRejectsOldAndroidBeforeLoadingNativeLibrary() throws Exception {
        try {
            new VulkanRife(ApplicationProvider.getApplicationContext());
            fail("Must not initialize Vulkan on old Android");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("Android 7"));
        }
    }
    @Test @Config(sdk = 28)
    public void nnapiRejectsBeforeCpuExclusionIsAvailable() throws Exception {
        try {
            new NnapiRifeLite(ApplicationProvider.getApplicationContext(), 64, 64);
            fail("NNAPI CPU exclusion requires API 29");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("Android 10"));
        }
    }
}
