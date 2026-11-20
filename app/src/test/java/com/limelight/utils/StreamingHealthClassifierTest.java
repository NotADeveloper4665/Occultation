package com.limelight.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StreamingHealthClassifierTest {
    private static StreamingHealthClassifier.Sample sample(double rtt, double loss, double decode,
            double host, double received, double rendered, boolean recovering) {
        return new StreamingHealthClassifier.Sample(rtt, loss, decode, host,
                received, rendered, 60.0, recovering);
    }

    @Test
    public void waitsForMinimumEvidence() {
        StreamingHealthClassifier classifier = new StreamingHealthClassifier();
        assertEquals(StreamingHealthClassifier.Status.INSUFFICIENT_DATA,
                classifier.observe(StreamingHealthClassifier.Sample.unavailable()).getStatus());
        assertEquals(StreamingHealthClassifier.Status.INSUFFICIENT_DATA,
                classifier.observe(sample(25, 0, 3, Double.NaN, 60, 60, false)).getStatus());
        assertEquals(StreamingHealthClassifier.Status.HEALTHY,
                classifier.observe(sample(25, 0, 3, Double.NaN, 60, 60, false)).getStatus());
    }

    @Test
    public void sustainedNetworkPressureWinsAfterThreeVotes() {
        StreamingHealthClassifier classifier = new StreamingHealthClassifier();
        classifier.observe(sample(30, 0, 3, Double.NaN, 60, 60, false));
        classifier.observe(sample(120, 2, 3, Double.NaN, 60, 60, false));
        assertEquals(StreamingHealthClassifier.Status.INSUFFICIENT_DATA,
                classifier.observe(sample(130, 2, 3, Double.NaN, 60, 60, false)).getStatus());
        assertEquals(StreamingHealthClassifier.Status.NETWORK_PRESSURE,
                classifier.observe(sample(125, 2, 3, Double.NaN, 60, 60, false)).getStatus());
    }

    @Test
    public void distinguishesDecoderHostAndPresentationPressure() {
        StreamingHealthClassifier decoder = new StreamingHealthClassifier();
        StreamingHealthClassifier host = new StreamingHealthClassifier();
        StreamingHealthClassifier presentation = new StreamingHealthClassifier();
        for (int i = 0; i < 3; i++) {
            decoder.observe(sample(25, 0, 14, Double.NaN, 60, 60, false));
            host.observe(sample(25, 0, 3, 15, 60, 60, false));
            presentation.observe(sample(25, 0, 3, Double.NaN, 60, 48, false));
        }
        assertEquals(StreamingHealthClassifier.Status.DECODER_PRESSURE,
                decoder.observe(sample(25, 0, 14, Double.NaN, 60, 60, false)).getStatus());
        assertEquals(StreamingHealthClassifier.Status.HOST_PROCESSING_DELAY,
                host.observe(sample(25, 0, 3, 15, 60, 60, false)).getStatus());
        assertEquals(StreamingHealthClassifier.Status.PRESENTATION_PACING,
                presentation.observe(sample(25, 0, 3, Double.NaN, 60, 48, false)).getStatus());
    }

    @Test
    public void recoveryIsImmediateAndResetClearsHistory() {
        StreamingHealthClassifier classifier = new StreamingHealthClassifier();
        StreamingHealthClassifier.Assessment recovery =
                classifier.observe(sample(Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                        Double.NaN, Double.NaN, true));
        assertEquals(StreamingHealthClassifier.Status.RECOVERING, recovery.getStatus());
        assertTrue(recovery.getReason().contains("recovery"));
        classifier.reset();
        assertEquals(StreamingHealthClassifier.Status.INSUFFICIENT_DATA,
                classifier.observe(StreamingHealthClassifier.Sample.unavailable()).getStatus());
    }

    @Test
    public void missingMetricsAreNotTreatedAsZeroOrHealthy() {
        StreamingHealthClassifier classifier = new StreamingHealthClassifier();
        for (int i = 0; i < 5; i++) {
            assertEquals(StreamingHealthClassifier.Status.INSUFFICIENT_DATA,
                    classifier.observe(StreamingHealthClassifier.Sample.unavailable()).getStatus());
        }
    }
}
