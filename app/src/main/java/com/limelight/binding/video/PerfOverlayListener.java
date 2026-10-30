package com.limelight.binding.video;

public interface PerfOverlayListener {
    void onPerfUpdate(final String text);

    /** Called once the first decoded video frame has rendered to the stream surface. */
    void onVideoFrameRendered();
}