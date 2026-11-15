# MediaCodec decoder watchdog and recovery

## Watchdog contract

The output watchdog requests a flush only when both conditions hold:

- compressed input has been successfully queued recently; and
- no decoded output buffer has become available for at least 1.2 seconds.

An idle stream does not qualify. This avoids treating pauses, network starvation, or normal session idleness as a decoder failure. A request is emitted once and is re-armed only after output resumes.

## Recovery ownership

The watchdog never calls MediaCodec directly. It promotes the existing recovery state from NONE to FLUSH; the existing codec recovery barrier then waits for the input, renderer, and Choreographer participants to quiesce before flushing. The renderer checks that barrier at the top of every loop iteration as well as after its normal output-drain operation, so the low-latency latest-only path cannot permanently skip its recovery acknowledgement.

## Validation

DecoderOutputWatchdogTest covers idle streams, the stall threshold, duplicate request suppression, re-arming after output progress, and stale input. These are deterministic JVM tests and do not require a device.

Device validation is still required for vendor MediaCodec behavior, recovery under sustained packet delivery, network pauses, reconnects, and long-running sessions.
