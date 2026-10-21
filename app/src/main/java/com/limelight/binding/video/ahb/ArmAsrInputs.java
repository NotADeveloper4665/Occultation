package com.limelight.binding.video.ahb;

/** Arm ASR is temporal: decoded color alone is insufficient for a valid dispatch. */
public final class ArmAsrInputs {
    private ArmAsrInputs() { }
    public static String unavailableReason(boolean vulkan, boolean depth, boolean motion, boolean jitter) {
        if (!vulkan) return "Arm ASR requires the Vulkan video renderer.";
        if (!depth || !motion || !jitter) return "Arm ASR requires host depth, motion vectors and render jitter; this video stream supplies color only.";
        return "Arm ASR dispatch backend is not integrated yet.";
    }
}
