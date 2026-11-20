package com.limelight.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Bounded, local-only session event history and explicit report formatter.
 * Event payloads are enums only: connection addresses, hostnames, pairing material,
 * certificates, and arbitrary transport strings cannot enter this report.
 */
public final class StreamingSessionDiagnostics {
    public static final int MAX_EVENTS = 64;

    public enum EventType {
        DECODER_RECOVERY_REQUESTED("Decoder recovery requested"),
        DECODER_RECOVERY_COMPLETED("Decoder recovery completed"),
        RECONNECT_ATTEMPT("Reconnect attempt"),
        RECONNECT_SUCCEEDED("Reconnect succeeded"),
        SESSION_DISCONNECTED("Session disconnected"),
        TRANSPORT_FALLBACK("Transport fallback"),
        FEEDBACK_UNAVAILABLE("Host feedback unavailable"),
        FEEDBACK_RECOVERED("Host feedback recovered");

        private final String label;

        EventType(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum Codec { H264, HEVC, AV1, UNKNOWN }
    public enum Renderer { MEDIACODEC_SURFACE, EGL, VULKAN, UNKNOWN }
    public enum Transport { UDP, QUIC, UNKNOWN }

    public static final class Event {
        private final long elapsedMs;
        private final EventType type;

        private Event(long elapsedMs, EventType type) {
            this.elapsedMs = elapsedMs;
            this.type = type;
        }

        public long getElapsedMs() {
            return elapsedMs;
        }

        public EventType getType() {
            return type;
        }
    }

    public static final class Summary {
        public final Codec codec;
        public final Renderer renderer;
        public final Transport transport;
        public final int width;
        public final int height;
        public final int targetFps;
        public final int bitrateKbps;
        public final StreamingHealthClassifier.Status health;
        public final double networkRttMs;
        public final double decoderTimeMs;
        public final double presentationDelayMs;
        public final double hostProcessingMs;

        public Summary(Codec codec, Renderer renderer, Transport transport,
                       int width, int height, int targetFps, int bitrateKbps,
                       StreamingHealthClassifier.Status health, double networkRttMs,
                       double decoderTimeMs, double presentationDelayMs,
                       double hostProcessingMs) {
            this.codec = codec == null ? Codec.UNKNOWN : codec;
            this.renderer = renderer == null ? Renderer.UNKNOWN : renderer;
            this.transport = transport == null ? Transport.UNKNOWN : transport;
            this.width = width;
            this.height = height;
            this.targetFps = targetFps;
            this.bitrateKbps = bitrateKbps;
            this.health = health == null ? StreamingHealthClassifier.Status.INSUFFICIENT_DATA : health;
            this.networkRttMs = networkRttMs;
            this.decoderTimeMs = decoderTimeMs;
            this.presentationDelayMs = presentationDelayMs;
            this.hostProcessingMs = hostProcessingMs;
        }
    }

    private final long startedAtNs = System.nanoTime();
    private final Event[] events = new Event[MAX_EVENTS];
    private int nextIndex;
    private int size;

    public synchronized void recordEvent(EventType type) {
        if (type == null) return;
        long elapsedMs = Math.max(0L, (System.nanoTime() - startedAtNs) / 1_000_000L);
        events[nextIndex] = new Event(elapsedMs, type);
        nextIndex = (nextIndex + 1) % MAX_EVENTS;
        if (size < MAX_EVENTS) size++;
    }

    /** Returns events oldest-first as an immutable snapshot. */
    public synchronized List<Event> getEvents() {
        List<Event> snapshot = new ArrayList<>(size);
        int first = (nextIndex - size + MAX_EVENTS) % MAX_EVENTS;
        for (int i = 0; i < size; i++) {
            Event event = events[(first + i) % MAX_EVENTS];
            if (event != null) snapshot.add(event);
        }
        return Collections.unmodifiableList(snapshot);
    }

    /** Builds a report only when explicitly called by the UI/share action. */
    public String buildShareableReport(Summary summary) {
        if (summary == null) throw new IllegalArgumentException("summary is required");
        StringBuilder report = new StringBuilder();
        report.append("Occultation session diagnostics\n");
        report.append("Health: ").append(summary.health.getLabel()).append('\n');
        report.append("Codec: ").append(summary.codec).append('\n');
        report.append("Renderer: ").append(summary.renderer).append('\n');
        report.append("Transport: ").append(summary.transport).append('\n');
        report.append("Stream: ").append(safeDimension(summary.width)).append('x')
                .append(safeDimension(summary.height)).append(" @ ")
                .append(safeFps(summary.targetFps)).append(" FPS\n");
        report.append("Bitrate: ").append(safeBitrate(summary.bitrateKbps)).append('\n');
        report.append("Latency breakdown (source-labelled)\n");
        report.append("Network RTT (client-observed): ").append(metric(summary.networkRttMs)).append('\n');
        report.append("Decoder time (client-measured): ").append(metric(summary.decoderTimeMs)).append('\n');
        report.append("Presentation delay (release-to-render): ").append(metric(summary.presentationDelayMs)).append('\n');
        report.append("Host processing (host-reported): ").append(metric(summary.hostProcessingMs)).append('\n');
        report.append("Event timeline\n");
        List<Event> snapshot = getEvents();
        if (snapshot.isEmpty()) {
            report.append("No recorded recovery or transport events\n");
        } else {
            for (Event event : snapshot) {
                report.append('+').append(event.elapsedMs).append(" ms: ")
                        .append(event.type.getLabel()).append('\n');
            }
        }
        report.append("\nNo host address, hostname, pairing key, certificate, or session identifier is included.\n");
        report.append("Generated locally by explicit user action; not uploaded automatically.\n");
        return report.toString();
    }

    private static String metric(double value) {
        return Double.isNaN(value) || Double.isInfinite(value) || value < 0.0 ?
                "unavailable" : String.format(java.util.Locale.US, "%.2f ms", value);
    }

    private static int safeDimension(int value) {
        return value >= 1 && value <= 16384 ? value : 0;
    }

    private static int safeFps(int value) {
        return value >= 1 && value <= 360 ? value : 0;
    }

    private static String safeBitrate(int value) {
        return value >= 100 && value <= 500000 ? value + " Kbps" : "unavailable";
    }
}
