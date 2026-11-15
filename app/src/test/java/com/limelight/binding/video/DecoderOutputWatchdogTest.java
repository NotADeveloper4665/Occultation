package com.limelight.binding.video;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DecoderOutputWatchdogTest {
    @Test
    public void idleStreamDoesNotRequestRecovery() {
        DecoderOutputWatchdog watchdog = new DecoderOutputWatchdog();
        assertFalse(watchdog.shouldRequestRecovery(DecoderOutputWatchdog.STALL_TIMEOUT_NS * 10));
    }

    @Test
    public void requestsRecoveryOnlyAfterOutputStallsDuringActiveInput() {
        DecoderOutputWatchdog watchdog = new DecoderOutputWatchdog();
        long startNs = 10_000L;
        watchdog.onInputQueued(startNs);

        assertFalse(watchdog.shouldRequestRecovery(startNs + DecoderOutputWatchdog.STALL_TIMEOUT_NS - 1));
        assertTrue(watchdog.shouldRequestRecovery(startNs + DecoderOutputWatchdog.STALL_TIMEOUT_NS));
    }

    @Test
    public void doesNotRepeatRecoveryRequestUntilOutputResumes() {
        DecoderOutputWatchdog watchdog = new DecoderOutputWatchdog();
        long startNs = 10_000L;
        watchdog.onInputQueued(startNs);
        long stalledAtNs = startNs + DecoderOutputWatchdog.STALL_TIMEOUT_NS;

        assertTrue(watchdog.shouldRequestRecovery(stalledAtNs));
        assertFalse(watchdog.shouldRequestRecovery(stalledAtNs + 1));
    }

    @Test
    public void outputProgressRearmsWatchdog() {
        DecoderOutputWatchdog watchdog = new DecoderOutputWatchdog();
        long startNs = 10_000L;
        watchdog.onInputQueued(startNs);
        long stalledAtNs = startNs + DecoderOutputWatchdog.STALL_TIMEOUT_NS;

        assertTrue(watchdog.shouldRequestRecovery(stalledAtNs));
        watchdog.onOutputAvailable(stalledAtNs + 1);
        watchdog.onInputQueued(stalledAtNs + 2);

        assertFalse(watchdog.shouldRequestRecovery(stalledAtNs + 2 + DecoderOutputWatchdog.STALL_TIMEOUT_NS - 1));
        assertTrue(watchdog.shouldRequestRecovery(stalledAtNs + 2 + DecoderOutputWatchdog.STALL_TIMEOUT_NS));
    }

    @Test
    public void staleInputDoesNotTriggerRecovery() {
        DecoderOutputWatchdog watchdog = new DecoderOutputWatchdog();
        long startNs = 10_000L;
        watchdog.onInputQueued(startNs);

        assertFalse(watchdog.shouldRequestRecovery(startNs + DecoderOutputWatchdog.STALL_TIMEOUT_NS + 1));
    }
}
