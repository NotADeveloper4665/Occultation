import com.limelight.neural.AdaptiveInterpolationPolicy;
import com.limelight.neural.InterpolationAdmission;

public final class InterpolationScenarios {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    private static AdaptiveInterpolationPolicy.Metrics sample(long t, double loss, double jitter,
            double rtt, int backlog, boolean verified, boolean hot, double buffer, double extra,
            double lite, double full, double fps, double display) {
        return new AdaptiveInterpolationPolicy.Metrics(t, fps, display, loss, jitter, rtt, 20,
                backlog, lite, full, buffer, extra, verified, true, true, hot);
    }
    private static AdaptiveInterpolationPolicy.Metrics clean(long t) {
        return sample(t,0,1,20,0,true,false,30,0,2,4,30,60);
    }
    private static AdaptiveInterpolationPolicy.Decision ready(AdaptiveInterpolationPolicy p) {
        for (long t = 0; t <= 3000; t += 1000) p.evaluate(true,t,clean(t));
        return p.evaluate(true,3000,clean(3000));
    }
    public static void main(String[] args) {
        AdaptiveInterpolationPolicy p = new AdaptiveInterpolationPolicy();
        check(p.evaluate(true,0,clean(0)).reason == AdaptiveInterpolationPolicy.Reason.RECOVERING, "warmup");
        check(ready(p).mode == AdaptiveInterpolationPolicy.Mode.FULL, "stable recovery enables full model");
        check(p.evaluate(true,3100,sample(3100,.01,6,30,0,true,false,30,0,2,4,30,60)).mode
                == AdaptiveInterpolationPolicy.Mode.LITE, "moderate network selects lite");
        check(p.evaluate(true,3200,sample(3200,.33,1,300,0,true,false,30,0,2,4,30,60)).reason
                == AdaptiveInterpolationPolicy.Reason.NETWORK_UNSTABLE, "33 percent loss / 300 ms disables");
        check(p.evaluate(true,3300,clean(3300)).reason == AdaptiveInterpolationPolicy.Reason.RECOVERING, "no immediate reenable");
        check(p.evaluate(true,5300,clean(3300)).reason == AdaptiveInterpolationPolicy.Reason.STALE_TELEMETRY, "stale telemetry disables");
        p.reset();
        check(p.evaluate(true,0,sample(0,0,1,20,0,false,false,30,0,2,4,30,60)).reason
                == AdaptiveInterpolationPolicy.Reason.HARDWARE_UNVERIFIED, "no CPU or unknown backend admitted");
        check(p.evaluate(true,0,sample(0,0,1,20,2,true,false,30,0,2,4,30,60)).reason
                == AdaptiveInterpolationPolicy.Reason.BACKLOG, "decoded backlog bypass");
        check(p.evaluate(true,0,sample(0,0,1,20,0,true,true,30,0,2,4,30,60)).reason
                == AdaptiveInterpolationPolicy.Reason.THERMAL, "thermal bypass");
        check(p.evaluate(true,0,sample(0,0,1,20,0,true,false,0,0,2,4,30,60)).reason
                == AdaptiveInterpolationPolicy.Reason.LATENCY_BUDGET, "no free lookahead / no silent buffering");
        check(p.evaluate(true,0,sample(0,0,1,20,0,true,false,30,0,20,30,30,60)).reason
                == AdaptiveInterpolationPolicy.Reason.COMPUTE_BUDGET, "slow model bypass");
        check(p.evaluate(true,0,sample(0,0,1,20,0,true,false,30,0,2,4,60,60)).reason
                == AdaptiveInterpolationPolicy.Reason.DISPLAY_RATE, "display already matches source");
        check(p.evaluate(true,0,sample(0,Double.NaN,1,20,0,true,false,30,0,2,4,30,60)).reason
                == AdaptiveInterpolationPolicy.Reason.INVALID_METRICS, "invalid metrics bypass");
        p.reset(); ready(p);
        p.evaluate(true,3100,sample(3100,.01,6,30,0,true,false,30,0,2,4,30,60));
        check(p.evaluate(true,3200,clean(3200)).mode == AdaptiveInterpolationPolicy.Mode.LITE, "quality upgrade waits for clean window");
        check(p.evaluate(true,4300,clean(4300)).mode == AdaptiveInterpolationPolicy.Mode.LITE, "quality hysteresis persists");
        check(p.evaluate(true,5000,clean(5000)).mode == AdaptiveInterpolationPolicy.Mode.FULL, "quality recovers after stable window");
        AdaptiveInterpolationPolicy onlyFull = new AdaptiveInterpolationPolicy();
        AdaptiveInterpolationPolicy.Decision onlyFullDecision = null;
        for (long t = 0; t <= 3000; t += 1000) onlyFullDecision = onlyFull.evaluate(true,t,
                new AdaptiveInterpolationPolicy.Metrics(t,30,60,0,1,20,20,0,0,4,30,0,true,false,true,false));
        check(onlyFullDecision.mode == AdaptiveInterpolationPolicy.Mode.FULL, "full-only model can recover without lite model");
        p.reset(); AdaptiveInterpolationPolicy.Decision d = ready(p);
        InterpolationAdmission gate = new InterpolationAdmission();
        InterpolationAdmission.Frame a = new InterpolationAdmission.Frame(1,0,1,1280,720);
        InterpolationAdmission.Frame b = new InterpolationAdmission.Frame(2,33333,1,1280,720);
        InterpolationAdmission.Ticket ticket = gate.tryStart(d,a,b,30,false,0,10_000_000);
        check(ticket != null && ticket.midpointPtsUs == 16666, "midpoint between real consecutive frames");
        check(gate.tryStart(d,a,b,30,false,0,10_000_000) == null, "one hardware execution in flight");
        gate.invalidate();
        check(gate.busy(), "invalidating does not free in-use hardware buffers");
        check(!gate.finish(ticket,5_000_000,1,0), "invalidated result discarded");
        check(!gate.busy(), "completed old work releases slot");
        ticket = gate.tryStart(d,a,b,30,false,0,10_000_000);
        check(!gate.finish(ticket,10_000_001,1,0), "late result discarded");
        ticket = gate.tryStart(d,a,b,30,false,0,10_000_000);
        check(!gate.finish(ticket,5_000_000,1,33333), "newer real frame wins");
        ticket = gate.tryStart(d,a,b,30,false,0,10_000_000);
        check(gate.finish(ticket,5_000_000,1,0), "on-time result can be admitted");
        check(gate.tryStart(d,a,b,30,true,0,10_000_000) == null, "scene cut bypass");
        check(gate.tryStart(d,a,b,30,false,8_000_000,10_000_000) == null, "deadline cannot be met");
        check(gate.tryStart(d,a,new InterpolationAdmission.Frame(3,66666,1,1280,720),30,false,0,10_000_000) == null, "lost frame is not invented");
        check(gate.tryStart(d,a,new InterpolationAdmission.Frame(2,33333,2,1280,720),30,false,0,10_000_000) == null, "reconnect epoch bypass");
        check(gate.tryStart(d,a,new InterpolationAdmission.Frame(2,33333,1,1920,1080),30,false,0,10_000_000) == null, "resolution change bypass");
        System.out.println("PASS: " + checks + " neural interpolation policy and admission checks");
    }
}
