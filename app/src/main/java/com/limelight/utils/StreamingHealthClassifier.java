package com.limelight.utils;

/**
 * Conservative, client-only streaming health classifier.
 *
 * This model consumes one-second performance snapshots. It has no Android dependencies,
 * performs no I/O, and is intended to run from the existing stats callback.
 */
public final class StreamingHealthClassifier {
    private static final int WINDOW_SIZE = 5;
    private static final int MIN_SAMPLES = 3;
    private static final int REQUIRED_VOTES = 3;

    public enum Status {
        INSUFFICIENT_DATA("Insufficient data"),
        HEALTHY("Healthy"),
        NETWORK_PRESSURE("Network pressure"),
        DECODER_PRESSURE("Decoder pressure"),
        HOST_PROCESSING_DELAY("Host processing delay"),
        PRESENTATION_PACING("Presentation pacing"),
        RECOVERING("Recovering");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public static final class Sample {
        public final double rttMs;
        public final double packetLossPercent;
        public final double decoderTimeMs;
        public final double hostProcessingMs;
        public final double receivedFps;
        public final double renderedFps;
        public final double targetFps;
        public final boolean recovering;

        public Sample(double rttMs, double packetLossPercent, double decoderTimeMs,
                      double hostProcessingMs, double receivedFps, double renderedFps,
                      double targetFps, boolean recovering) {
            this.rttMs = rttMs;
            this.packetLossPercent = packetLossPercent;
            this.decoderTimeMs = decoderTimeMs;
            this.hostProcessingMs = hostProcessingMs;
            this.receivedFps = receivedFps;
            this.renderedFps = renderedFps;
            this.targetFps = targetFps;
            this.recovering = recovering;
        }

        public static Sample unavailable() {
            return new Sample(Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                    Double.NaN, Double.NaN, Double.NaN, false);
        }
    }

    public static final class Assessment {
        private final Status status;
        private final String reason;
        private final int samples;

        private Assessment(Status status, String reason, int samples) {
            this.status = status;
            this.reason = reason;
            this.samples = samples;
        }

        public Status getStatus() {
            return status;
        }

        public String getLabel() {
            return status.getLabel();
        }

        public String getReason() {
            return reason;
        }

        public int getSampleCount() {
            return samples;
        }

        public String formatForOverlay() {
            return reason.isEmpty() ? status.getLabel() : status.getLabel() + " — " + reason;
        }
    }

    private final Status[] window = new Status[WINDOW_SIZE];
    private int nextIndex;
    private int sampleCount;
    private Status lastStableStatus = Status.INSUFFICIENT_DATA;

    public synchronized Assessment observe(Sample sample) {
        if (sample == null) {
            sample = Sample.unavailable();
        }

        if (sample.recovering) {
            add(Status.RECOVERING);
            return new Assessment(Status.RECOVERING, "Decoder recovery is in progress", sampleCount);
        }

        Status current = classify(sample);
        add(current);

        int validSamples = 0;
        int network = 0;
        int decoder = 0;
        int host = 0;
        int presentation = 0;
        int recovering = 0;
        for (int i = 0; i < sampleCount; i++) {
            Status status = window[i];
            if (status != Status.INSUFFICIENT_DATA && status != Status.RECOVERING) {
                validSamples++;
            }
            if (status == Status.NETWORK_PRESSURE) network++;
            if (status == Status.DECODER_PRESSURE) decoder++;
            if (status == Status.HOST_PROCESSING_DELAY) host++;
            if (status == Status.PRESENTATION_PACING) presentation++;
            if (status == Status.RECOVERING) recovering++;
        }

        if (recovering >= REQUIRED_VOTES) {
            lastStableStatus = Status.RECOVERING;
            return new Assessment(Status.RECOVERING, "Recent decoder recovery activity", sampleCount);
        }

        Status candidate = Status.INSUFFICIENT_DATA;
        if (network >= REQUIRED_VOTES) candidate = Status.NETWORK_PRESSURE;
        else if (decoder >= REQUIRED_VOTES) candidate = Status.DECODER_PRESSURE;
        else if (host >= REQUIRED_VOTES) candidate = Status.HOST_PROCESSING_DELAY;
        else if (presentation >= REQUIRED_VOTES) candidate = Status.PRESENTATION_PACING;

        if (candidate != Status.INSUFFICIENT_DATA) {
            lastStableStatus = candidate;
            return assessment(candidate, sampleCount);
        }

        if (validSamples < MIN_SAMPLES) {
            return new Assessment(Status.INSUFFICIENT_DATA,
                    "Waiting for more performance samples", sampleCount);
        }

        // Keep an established pressure state until the rolling window has enough
        // neutral evidence to clear it. A few clean samples must not hide a sustained issue.
        if (lastStableStatus != Status.HEALTHY &&
                lastStableStatus != Status.INSUFFICIENT_DATA &&
                count(window, lastStableStatus) > 0) {
            return assessment(lastStableStatus, sampleCount);
        }

        if (network + decoder + host + presentation > 0) {
            return new Assessment(Status.INSUFFICIENT_DATA,
                    "Signals are mixed; collecting more samples", sampleCount);
        }

        lastStableStatus = Status.HEALTHY;
        return new Assessment(Status.HEALTHY, "No sustained client-visible pressure detected", sampleCount);
    }

    public synchronized void reset() {
        for (int i = 0; i < window.length; i++) {
            window[i] = null;
        }
        nextIndex = 0;
        sampleCount = 0;
        lastStableStatus = Status.INSUFFICIENT_DATA;
    }

    private void add(Status status) {
        window[nextIndex] = status;
        nextIndex = (nextIndex + 1) % WINDOW_SIZE;
        if (sampleCount < WINDOW_SIZE) sampleCount++;
    }

    private static int count(Status[] values, Status target) {
        int count = 0;
        for (Status value : values) {
            if (value == target) count++;
        }
        return count;
    }

    private static Status classify(Sample sample) {
        boolean hasEvidence = false;
        double frameBudgetMs = isPositive(sample.targetFps) ? 1000.0 / sample.targetFps : 16.667;

        boolean network = false;
        if (isFinite(sample.rttMs)) {
            hasEvidence = true;
            network = sample.rttMs >= 100.0;
        }
        if (isFinite(sample.packetLossPercent)) {
            hasEvidence = true;
            network |= sample.packetLossPercent >= 1.5;
        }

        boolean decoder = false;
        if (isFinite(sample.decoderTimeMs)) {
            hasEvidence = true;
            decoder = sample.decoderTimeMs >= Math.max(10.0, frameBudgetMs * 0.70);
        }

        boolean host = false;
        if (isFinite(sample.hostProcessingMs)) {
            hasEvidence = true;
            host = sample.hostProcessingMs >= Math.max(12.0, frameBudgetMs * 0.70);
        }

        boolean presentation = false;
        if (isFinite(sample.receivedFps) && isFinite(sample.renderedFps) &&
                sample.receivedFps >= 15.0) {
            hasEvidence = true;
            presentation = sample.renderedFps < sample.receivedFps * 0.90;
        }

        // Prefer the earliest evidenced stage. Multiple causes can coexist; this
        // primary label is a diagnostic hint, not a definitive root-cause claim.
        if (network) return Status.NETWORK_PRESSURE;
        if (decoder) return Status.DECODER_PRESSURE;
        if (host) return Status.HOST_PROCESSING_DELAY;
        if (presentation) return Status.PRESENTATION_PACING;
        return hasEvidence ? Status.HEALTHY : Status.INSUFFICIENT_DATA;
    }

    private static Assessment assessment(Status status, int samples) {
        switch (status) {
            case NETWORK_PRESSURE:
                return new Assessment(status, "Elevated RTT or packet loss observed", samples);
            case DECODER_PRESSURE:
                return new Assessment(status, "Decode time is consuming the frame budget", samples);
            case HOST_PROCESSING_DELAY:
                return new Assessment(status, "Host-reported processing time is elevated", samples);
            case PRESENTATION_PACING:
                return new Assessment(status, "Rendered frame cadence is trailing received cadence", samples);
            case RECOVERING:
                return new Assessment(status, "Decoder recovery is in progress", samples);
            default:
                return new Assessment(status, "No sustained client-visible pressure detected", samples);
        }
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value) && value >= 0.0;
    }

    private static boolean isPositive(double value) {
        return isFinite(value) && value > 0.0;
    }
}
