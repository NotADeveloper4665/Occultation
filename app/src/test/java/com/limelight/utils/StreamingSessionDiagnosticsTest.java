package com.limelight.utils;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StreamingSessionDiagnosticsTest {
    @Test
    public void eventHistoryIsBoundedAndOldestFirst() {
        StreamingSessionDiagnostics diagnostics = new StreamingSessionDiagnostics();
        for (int i = 0; i < StreamingSessionDiagnostics.MAX_EVENTS + 10; i++) {
            diagnostics.recordEvent(StreamingSessionDiagnostics.EventType.RECONNECT_ATTEMPT);
        }
        List<StreamingSessionDiagnostics.Event> events = diagnostics.getEvents();
        assertEquals(StreamingSessionDiagnostics.MAX_EVENTS, events.size());
        assertEquals(StreamingSessionDiagnostics.EventType.RECONNECT_ATTEMPT,
                events.get(0).getType());
        assertTrue(events.get(events.size() - 1).getElapsedMs() >= events.get(0).getElapsedMs());
    }

    @Test
    public void reportLabelsSourcesAndKeepsUnavailableMetricsExplicit() {
        StreamingSessionDiagnostics diagnostics = new StreamingSessionDiagnostics();
        diagnostics.recordEvent(StreamingSessionDiagnostics.EventType.DECODER_RECOVERY_REQUESTED);
        String report = diagnostics.buildShareableReport(new StreamingSessionDiagnostics.Summary(
                StreamingSessionDiagnostics.Codec.HEVC,
                StreamingSessionDiagnostics.Renderer.MEDIACODEC_SURFACE,
                StreamingSessionDiagnostics.Transport.QUIC,
                1920, 1080, 60, 20000,
                StreamingHealthClassifier.Status.DECODER_PRESSURE,
                1.5, 60, 58, 35, 8.5, Double.NaN, Double.NaN));

        assertTrue(report.contains("Network RTT (client-observed): 35.00 ms"));
        assertTrue(report.contains("Packet loss (client-observed): 1.50%"));
        assertTrue(report.contains("Received FPS: 60.0"));
        assertTrue(report.contains("Rendered FPS: 58.0"));
        assertTrue(report.contains("Decoder time (client-measured): 8.50 ms"));
        assertTrue(report.contains("Presentation delay (release-to-render): unavailable"));
        assertTrue(report.contains("Host processing (host-reported): unavailable"));
        assertTrue(report.contains("Decoder recovery requested"));
        assertTrue(report.contains("not uploaded automatically"));
    }

    @Test
    public void reportHasNoFieldsForConnectionSecrets() {
        StreamingSessionDiagnostics diagnostics = new StreamingSessionDiagnostics();
        String report = diagnostics.buildShareableReport(new StreamingSessionDiagnostics.Summary(
                StreamingSessionDiagnostics.Codec.H264,
                StreamingSessionDiagnostics.Renderer.UNKNOWN,
                StreamingSessionDiagnostics.Transport.UNKNOWN,
                0, 0, 0, 0,
                StreamingHealthClassifier.Status.INSUFFICIENT_DATA,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN));

        assertFalse(report.contains("hostAddress"));
        assertFalse(report.contains("pairingKey"));
        assertFalse(report.contains("certificate:"));
    }
}
