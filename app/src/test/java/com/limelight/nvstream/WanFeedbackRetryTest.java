package com.limelight.nvstream;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

// Pure Java: no Android runtime or native bridge is needed for retry policy.
public class WanFeedbackRetryTest {
    @Test public void outagesBackOffAndRecoveryResetsDelay() {
        WanFeedbackRetry retry = new WanFeedbackRetry();
        assertEquals(1000, retry.getDelayMillis());
        for (long expected : new long[]{2000, 4000, 8000, 16000, 30000, 30000, 30000}) {
            retry.failed();
            assertEquals(expected, retry.getDelayMillis());
        }
        retry.succeeded();
        assertEquals(1000, retry.getDelayMillis());
        retry.failed();
        assertEquals(2000, retry.getDelayMillis());
    }

    @Test public void retryStateIsSessionLocal() {
        WanFeedbackRetry first = new WanFeedbackRetry();
        WanFeedbackRetry second = new WanFeedbackRetry();
        first.failed();
        assertEquals(2000, first.getDelayMillis());
        assertEquals(1000, second.getDelayMillis());
    }
}
