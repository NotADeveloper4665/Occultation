package com.limelight.nvstream;

/** Bounded retry delay, owned by one stream's telemetry worker. */
final class WanFeedbackRetry {
    private long delayMillis = 1000;

    long getDelayMillis() { return delayMillis; }
    void failed() { delayMillis = Math.min(30000, delayMillis * 2); }
    void succeeded() { delayMillis = 1000; }
}
