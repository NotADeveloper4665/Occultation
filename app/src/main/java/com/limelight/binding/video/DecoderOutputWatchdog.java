package com.limelight.binding.video;

/**
 * Tracks whether a decoder has stopped producing output while compressed input is
 * still being submitted. This deliberately does not infer a stall from an idle
 * stream, since paused or network-starved sessions must not trigger codec flushes.
 */
final class DecoderOutputWatchdog {
    static final long STALL_TIMEOUT_NS = 1_200_000_000L;

    private long lastInputNs = Long.MIN_VALUE;
    private long lastOutputNs = Long.MIN_VALUE;
    private boolean recoveryRequested;

    synchronized void onInputQueued(long nowNs) {
        lastInputNs = nowNs;
        // Start the output clock with the first input frame. Without this, the
        // watchdog would mistake decoder startup for a long-running stall.
        if (lastOutputNs == Long.MIN_VALUE) {
            lastOutputNs = nowNs;
        }
    }

    synchronized void onOutputAvailable(long nowNs) {
        lastOutputNs = nowNs;
        // Re-arm only after the decoder demonstrates that output has resumed.
        recoveryRequested = false;
    }

    synchronized boolean shouldRequestRecovery(long nowNs) {
        if (recoveryRequested || lastInputNs == Long.MIN_VALUE ||
                lastOutputNs == Long.MIN_VALUE || nowNs < lastInputNs || nowNs < lastOutputNs) {
            return false;
        }

        boolean inputIsActive = nowNs - lastInputNs <= STALL_TIMEOUT_NS;
        boolean outputHasStalled = nowNs - lastOutputNs >= STALL_TIMEOUT_NS;
        if (inputIsActive && outputHasStalled) {
            recoveryRequested = true;
            return true;
        }
        return false;
    }
}
