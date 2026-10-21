package com.limelight.binding.video.ahb;

import org.junit.Test;
import static org.junit.Assert.*;

public class DisplayPolicyTest {
    @Test public void tensorIsNotMistakenForMediaTek() {
        assertFalse(DisplayPolicy.isMediaTek("Google", "tensor", "Tensor G4"));
        assertTrue(DisplayPolicy.isMediaTek("MediaTek", "board", "Dimensity 9300"));
        assertTrue(DisplayPolicy.isMediaTek("Xiaomi", "mt6989", "unknown"));
        assertFalse(DisplayPolicy.isMediaTek(null, null, null));
    }
    @Test public void respectsExplicitChoiceAndDeviceCapabilities() {
        assertFalse(DisplayPolicy.useAhb(33, false, false, false, true));
        assertFalse(DisplayPolicy.useAhb(32, false, true, true, true));
        assertFalse(DisplayPolicy.useAhb(33, true, true, true, true));
        assertFalse(DisplayPolicy.useAhb(33, false, false, true, false));
        assertTrue(DisplayPolicy.useAhb(33, false, false, true, true));
        assertTrue(DisplayPolicy.useAhb(33, false, true, false, false));
    }
    @Test public void rejectsSoftwareNamesWithoutAssumingVendorHardwareNames() {
        assertTrue(DisplayPolicy.softwareCodecName("c2.google.av1.decoder"));
        assertTrue(DisplayPolicy.softwareCodecName("c2.android.av1.decoder"));
        assertTrue(DisplayPolicy.softwareCodecName("omx.google.hevc.decoder"));
        assertFalse(DisplayPolicy.softwareCodecName("c2.mtk.hevc.decoder"));
        assertFalse(DisplayPolicy.softwareCodecName("c2.exynos.av1.decoder"));
    }
    @Test public void asrCannotClaimReadyWithOnlyDecodedColor() {
        assertTrue(ArmAsrInputs.unavailableReason(true, false, false, false).contains("depth"));
        assertTrue(ArmAsrInputs.unavailableReason(true, true, false, true).contains("motion"));
        assertTrue(ArmAsrInputs.unavailableReason(false, true, true, true).contains("Vulkan"));
        assertTrue(ArmAsrInputs.unavailableReason(true, true, true, true).contains("not integrated"));
    }
}
