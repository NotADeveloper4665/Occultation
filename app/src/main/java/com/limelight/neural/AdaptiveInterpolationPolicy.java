package com.limelight.neural;

/** Admission policy only. No model is loaded and no synthetic frames are rendered here. */
public final class AdaptiveInterpolationPolicy {
    public enum Mode { BYPASS, LITE, FULL }
    public enum Reason {
        READY, DISABLED, HARDWARE_UNVERIFIED, MODEL_UNAVAILABLE, INVALID_METRICS,
        STALE_TELEMETRY, NETWORK_UNSTABLE, BACKLOG, THERMAL, DISPLAY_RATE,
        LATENCY_BUDGET, COMPUTE_BUDGET, RECOVERING
    }
    public static final class Metrics {
        public final long sampledMs;
        public final double sourceFps, displayFps, lossFraction, jitterMs, rttMs, baselineRttMs;
        public final double liteP95Ms, fullP95Ms, existingPlayoutMs, allowedExtraDelayMs;
        public final int decodedBacklog;
        public final boolean nnapiGraphVerified, liteModelReady, fullModelReady, thermalLimited;
        public Metrics(long sampledMs, double sourceFps, double displayFps, double lossFraction,
                       double jitterMs, double rttMs, double baselineRttMs, int decodedBacklog,
                       double liteP95Ms, double fullP95Ms, double existingPlayoutMs,
                       double allowedExtraDelayMs, boolean nnapiGraphVerified,
                       boolean liteModelReady, boolean fullModelReady, boolean thermalLimited) {
            this.sampledMs = sampledMs; this.sourceFps = sourceFps; this.displayFps = displayFps;
            this.lossFraction = lossFraction; this.jitterMs = jitterMs; this.rttMs = rttMs;
            this.baselineRttMs = baselineRttMs; this.decodedBacklog = decodedBacklog;
            this.liteP95Ms = liteP95Ms; this.fullP95Ms = fullP95Ms;
            this.existingPlayoutMs = existingPlayoutMs; this.allowedExtraDelayMs = allowedExtraDelayMs;
            this.nnapiGraphVerified = nnapiGraphVerified; this.liteModelReady = liteModelReady;
            this.fullModelReady = fullModelReady; this.thermalLimited = thermalLimited;
        }
    }
    public static final class Decision {
        public final Mode mode;
        public final Reason reason;
        public final double processingP95Ms, requiredPlayoutMs, addedDelayMs;
        private Decision(Mode mode, Reason reason, double processingP95Ms,
                         double requiredPlayoutMs, double addedDelayMs) {
            this.mode = mode; this.reason = reason; this.processingP95Ms = processingP95Ms;
            this.requiredPlayoutMs = requiredPlayoutMs; this.addedDelayMs = addedDelayMs;
        }
        public boolean enabled() { return mode != Mode.BYPASS; }
    }
    private static final long MAX_TELEMETRY_AGE_MS = 1500;
    private static final long RECOVERY_MS = 3000;
    private static final double GUARD_MS = 2;
    private long healthySince = -1;
    private long cleanSince = -1;
    private long lastSample = -1;
    private long lastNow = -1;

    private Decision bypass(Reason reason) {
        healthySince = cleanSince = -1;
        return new Decision(Mode.BYPASS, reason, 0, 0, 0);
    }
    public synchronized Decision evaluate(boolean requested, long nowMs, Metrics m) {
        if (nowMs < 0 || nowMs < lastNow) {
            lastNow = nowMs; return bypass(Reason.INVALID_METRICS);
        }
        lastNow = nowMs;
        if (!requested) return bypass(Reason.DISABLED);
        if (m == null || !valid(m)) return bypass(Reason.INVALID_METRICS);
        if (!m.nnapiGraphVerified) return bypass(Reason.HARDWARE_UNVERIFIED);
        if (!m.liteModelReady && !m.fullModelReady) return bypass(Reason.MODEL_UNAVAILABLE);
        if (m.sampledMs > nowMs || nowMs - m.sampledMs > MAX_TELEMETRY_AGE_MS || m.sampledMs < lastSample)
            return bypass(Reason.STALE_TELEMETRY);
        lastSample = m.sampledMs;
        if (m.thermalLimited) return bypass(Reason.THERMAL);
        if (m.decodedBacklog > 1) return bypass(Reason.BACKLOG);
        double inflation = Math.max(0, m.rttMs - m.baselineRttMs);
        if (m.lossFraction >= .03 || m.jitterMs >= 12 || inflation >= 50)
            return bypass(Reason.NETWORK_UNSTABLE);
        if (m.displayFps + .1 < 2 * m.sourceFps) return bypass(Reason.DISPLAY_RATE);
        double halfPeriod = 500 / m.sourceFps;
        boolean clean = m.lossFraction <= .005 && m.jitterMs <= 4 && inflation <= 15;
        if (!clean) cleanSince = -1;
        else if (cleanSince < 0) cleanSince = nowMs;
        Mode mode = clean && m.fullModelReady && (!m.liteModelReady || nowMs - cleanSince >= 1500) ? Mode.FULL : Mode.LITE;
        double cost = mode == Mode.FULL ? m.fullP95Ms : m.liteP95Ms;
        if (mode == Mode.LITE && !m.liteModelReady) return bypass(Reason.MODEL_UNAVAILABLE);
        // All timings include color conversion, inference, upload and rendering.
        if (cost + GUARD_MS >= halfPeriod && mode == Mode.FULL && m.liteModelReady) {
            mode = Mode.LITE; cost = m.liteP95Ms;
        }
        if (cost + GUARD_MS >= halfPeriod) return bypass(Reason.COMPUTE_BUDGET);
        double needed = halfPeriod + cost + GUARD_MS;
        double added = Math.max(0, needed - m.existingPlayoutMs);
        if (added > m.allowedExtraDelayMs && mode == Mode.FULL && m.liteModelReady) {
            mode = Mode.LITE; cost = m.liteP95Ms;
            needed = halfPeriod + cost + GUARD_MS;
            added = Math.max(0, needed - m.existingPlayoutMs);
        }
        if (added > m.allowedExtraDelayMs) return bypass(Reason.LATENCY_BUDGET);
        if (healthySince < 0) healthySince = nowMs;
        if (nowMs - healthySince < RECOVERY_MS)
            return new Decision(Mode.BYPASS, Reason.RECOVERING, cost, needed, added);
        return new Decision(mode, Reason.READY, cost, needed, added);
    }
    private static boolean valid(Metrics m) {
        if (m.sampledMs < 0 || m.decodedBacklog < 0) return false;
        double[] values = {m.sourceFps, m.displayFps, m.lossFraction, m.jitterMs, m.rttMs,
                m.baselineRttMs, m.liteP95Ms, m.fullP95Ms, m.existingPlayoutMs, m.allowedExtraDelayMs};
        for (double v : values) if (!(!Double.isNaN(v) && !Double.isInfinite(v)) || v < 0) return false;
        return m.sourceFps >= 1 && m.sourceFps <= 240 && m.displayFps >= 1 && m.displayFps <= 480
                && m.lossFraction <= 1 && (!m.liteModelReady || m.liteP95Ms > 0)
                && (!m.fullModelReady || m.fullP95Ms > 0);
    }
    public synchronized void reset() { healthySince = cleanSince = lastSample = lastNow = -1; }
}
