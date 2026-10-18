package com.limelight.neural;

/** One-in-flight, deadline-based pairing. Frame buffers remain owned by the renderer/backend. */
public final class InterpolationAdmission {
    public static final class Frame {
        public final long sequence, ptsUs, epoch;
        public final int width, height;
        public Frame(long sequence, long ptsUs, long epoch, int width, int height) {
            this.sequence = sequence; this.ptsUs = ptsUs; this.epoch = epoch;
            this.width = width; this.height = height;
        }
    }
    public static final class Ticket {
        public final long id, epoch, firstSequence, secondSequence, midpointPtsUs, presentDeadlineNs, submittedNs;
        public final AdaptiveInterpolationPolicy.Mode mode;
        private Ticket(long id, Frame first, Frame second, long deadline, long submitted, AdaptiveInterpolationPolicy.Mode mode) {
            this.id = id; this.epoch = second.epoch; firstSequence = first.sequence;
            secondSequence = second.sequence;
            midpointPtsUs = first.ptsUs + (second.ptsUs - first.ptsUs) / 2;
            presentDeadlineNs = deadline; submittedNs = submitted; this.mode = mode;
        }
    }
    private Ticket active;
    private long nextId;
    private long generation;
    private long activeGeneration;
    private static final long GUARD_NS = 2_000_000;

    public synchronized Ticket tryStart(AdaptiveInterpolationPolicy.Decision decision, Frame first,
                                        Frame second, double expectedSourceFps, boolean sceneCut,
                                        long nowNs, long midpointDeadlineNs) {
        if (active != null || decision == null || !decision.enabled() || sceneCut || first == null || second == null)
            return null;
        if (first.epoch < 0 || first.epoch != second.epoch || first.sequence < 0 || first.sequence == Long.MAX_VALUE
                || second.sequence != first.sequence + 1 || first.ptsUs < 0 || second.ptsUs <= first.ptsUs
                || first.width <= 0 || first.height <= 0 || first.width != second.width || first.height != second.height)
            return null;
        if ((Double.isNaN(expectedSourceFps) || Double.isInfinite(expectedSourceFps)) || expectedSourceFps < 1 || expectedSourceFps > 240 || nowNs < 0)
            return null;
        double gapUs = second.ptsUs - first.ptsUs;
        double periodUs = 1_000_000 / expectedSourceFps;
        if (gapUs < periodUs * .5 || gapUs > periodUs * 1.5) return null;
        double costNs = decision.processingP95Ms * 1_000_000;
        if (midpointDeadlineNs <= nowNs || costNs + GUARD_NS >= midpointDeadlineNs - nowNs) return null;
        active = new Ticket(++nextId, first, second, midpointDeadlineNs, nowNs, decision.mode);
        activeGeneration = generation;
        return active;
    }
    /** A real frame already presented at or beyond the midpoint always supersedes generated work. */
    public synchronized boolean finish(Ticket ticket, long nowNs, long currentEpoch, long lastRealPtsUs) {
        if (ticket == null || active != ticket) return false;
        boolean usable = activeGeneration == generation && ticket.epoch == currentEpoch
                && nowNs >= ticket.submittedNs && nowNs < ticket.presentDeadlineNs && lastRealPtsUs < ticket.midpointPtsUs;
        active = null;
        return usable;
    }
    /** Invalidate results without permitting another NNAPI execution before the old one completes. */
    public synchronized void invalidate() { generation++; }
    public synchronized boolean busy() { return active != null; }
}
