# Streaming Intelligence — Client Diagnostics Spec

Status: implementation in progress
Target: Occultation Android client
Branch base: `fix/bounded-performance-history`
Host counterpart: Syzygy Core telemetry and bounded per-session pipeline work

## Problem

The host and client are gaining better queue ownership, scheduling, backpressure handling,
and recovery behavior. The client should make the effects understandable to the person
playing or using the remote desktop. A raw FPS/RTT overlay is useful, but it does not
answer the important question: **what is currently limiting the stream, and what is the
client doing about it?**

Occultation already has several pieces that must be reused:
- Performance overlay metrics including FPS, bandwidth, packet loss, RTT, decoder timing,
  and host processing latency.
- Frame pacing and decoder output/recovery coordination.
- Negotiated Syzygy WAN feedback v1 (RTT, jitter, and frame-gap feedback).
- Existing `PerformanceDataTracker` history and profile/preferences architecture.

This feature must extend those systems, not create a parallel telemetry service, session
database, WAN controller, or profile store.

## User-facing outcomes

1. **Streaming health summary**
   - A compact state: Healthy, Network pressure, Decoder pressure, Host processing delay,
     Presentation pacing, Recovering, or Insufficient data.
   - Show the reason and the evidence behind it; do not present a guessed root cause as fact.
   - Use neutral language and avoid alarm states for normal transient variation.

2. **Latency and frame pipeline breakdown**
   - Separate client-observed decode time, client presentation/frame pacing, network RTT/loss,
     and host-reported processing latency.
   - Clearly label the source of each value: measured locally, reported by host, or estimated.
   - Do not imply that host processing latency is the same as network transit or total
     glass-to-glass latency.
   - Host queue age, packetization, FEC, encryption, and socket submission are not currently
     exposed to the client. Show them only after a versioned Syzygy telemetry capability
     reports them; otherwise say "Host stage details unavailable".

3. **Recovery and transport event timeline**
   - Surface meaningful events such as decoder recovery requested/completed, transport
     fallback before session start, reconnect attempt/success, and WAN feedback unavailable.
   - Events must be bounded, timestamped from a monotonic clock for ordering, and safe to
     collect without blocking render, audio, or input threads.
   - Avoid logging every frame or every packet.

4. **Session report**
   - Extend the existing performance history/export path with a compact session summary:
     selected codec/resolution/FPS/bitrate, renderer/transport, health intervals, recovery
     events, and rolling metric summaries.
   - Preserve the current bounded history behavior (100 entries) and existing stored fields.
   - No automatic upload. Any share/export action is explicit and user initiated.
   - Avoid including host addresses, pairing secrets, certificates, or other identifiers.

5. **Profiles and controls (later phase)**
   - Reuse `ProfilesManager` for explicit user-selected streaming presets only if they
     represent repeatable stream configuration.
   - Possible presets: Low Latency, Balanced, Quality. Show the settings each preset changes.
   - No hidden adaptive bitrate/FEC/resolution/FPS/decoder/renderer changes in this feature.
     Existing Syzygy WAN v1 behavior remains the only adaptive host controller.

## Diagnostic classification contract

The first implementation should be a conservative, deterministic classifier over existing
client-visible metrics. It must:
- Use rolling windows and hysteresis so one bad sample does not flip the status.
- Require enough samples before classifying; otherwise return Insufficient data.
- Prefer specific evidence: rising RTT/loss/jitter => Network pressure; sustained high
  decoder time or decoder output stall => Decoder pressure; host-reported processing delay
  => Host processing delay; late presentation cadence without corresponding network/decode
  pressure => Presentation pacing.
- Allow more than one contributing signal; identify a primary likely bottleneck only when
  evidence is sufficient.
- Distinguish "observed" from "likely". A status is a diagnostic hint, not a promise of cause.
- Never infer host queueing, FEC cost, or socket pressure from RTT alone.
- Never call a missing metric zero.

Thresholds must be documented, tested with synthetic traces, and calibrated against actual
Pixel 9a and other Android device sessions before being advertised as universal.

## Architecture

- Extend existing performance overlay and `PerformanceDataTracker`.
- Add a small pure-Java diagnostics policy/model for rolling windows, health state, and
  event classification; keep Android UI and MediaCodec calls outside the model.
- Feed it snapshots from existing stats callbacks. No new always-on worker.
- Use existing decoder recovery callbacks as event sources; do not create a second watchdog.
- Add a negotiated, optional host-telemetry extension only after Syzygy defines a stable
  versioned schema. Older hosts and standard Moonlight hosts must continue to work unchanged.
- Keep diagnostics disableable and bounded in CPU, memory, and storage.

## Implementation slices

### Slice 1 — foundation
- [x] Record this feature contract and source-of-truth boundaries.
- [x] Audit the exact overlay callback and stats lifecycle on the selected base.
- [x] Add pure-Java health classifier and rolling-window tests.
- [x] Wire existing client metrics into the classifier.
- [x] Add a compact health row and evidence summary to the existing performance overlay.
- [ ] Add a dedicated health details view.

### Slice 2 — recovery and session history
- [x] Publish decoder recovery requested/completed and session-disconnect events from existing lifecycle owners.
- [x] Record missing Syzygy WAN feedback when the existing retry worker first enters its unavailable state.
- [x] Add a bounded event ring (maximum 64 events per session).
- [ ] Wire reconnect attempts/successes when an actual reconnect lifecycle exists; do not infer them from unrelated connection stages.
- [ ] Wire transport fallback events from the negotiated transport owner; this branch currently has no confirmed fallback callback.
- [ ] Add session summary to existing performance history without changing old JSON compatibility.
- [x] Add explicit long-press report sharing with an allowlisted summary and no connection secrets.

### Slice 3 — Syzygy host telemetry
- [ ] Define a versioned, authenticated capability/schema with Syzygy.
- [ ] Expose per-session host queue age, encoder time, packetization/FEC time, encryption,
      pacing delay, socket submission/backpressure, and host recovery state where measurable.
- [ ] Display host-reported values separately from client measurements.
- [ ] Preserve standard Moonlight and older Syzygy compatibility.

### Slice 4 — optional user controls
- [ ] Evaluate profile-aware presets using existing `ProfilesManager`.
- [ ] Show exact setting changes before applying a preset.
- [ ] Do not add a competing adaptation controller.

## Non-goals

- No new transport protocol or QUIC implementation in this feature.
- No renderer rewrite, front-buffer changes, frame interpolation, or upscaling.
- No automatic stream-quality changes.
- No cloud analytics, background telemetry upload, or always-on diagnostic service.
- No duplicate performance overlay, preference store, profile system, or WAN controller.

## Acceptance criteria

- Existing Moonlight hosts and older Syzygy hosts behave exactly as before.
- Classifier is deterministic, unit-tested, bounded, and does not run on render/audio/input
  critical paths.
- Missing/unsupported metrics remain explicitly unavailable.
- No health classification is emitted until the minimum sample window is satisfied.
- Health transitions use hysteresis and have tests for noisy, missing, and contradictory data.
- Overlay remains readable and can be disabled.
- Existing performance history remains readable and bounded.
- Android unit tests, lint, and development APK build pass.
- Physical validation includes Pixel 9a (Tensor/Mali), at least one Adreno device, a standard
  Moonlight host, and Syzygy with/without the optional host telemetry capability.
- No release, merge, or public claim of measured latency improvements until device evidence
  exists.


## Implementation progress

### Slice 1 — health indicator
- Added a pure-Java rolling classifier with a five-sample window and three-vote transition threshold.
- Requires at least three evidence-bearing samples before showing Healthy or a pressure category.
- Uses client-observed RTT/loss, decoder time, host-reported processing time, and received-versus-rendered FPS.
- Decoder recovery is surfaced immediately as Recovering.
- Missing metrics remain unavailable; they are never converted to zero.
- The existing performance overlay displays the current health state and its evidence summary.
- Thresholds are provisional diagnostic heuristics and must be calibrated on real devices before being described as universal.


## Runtime implementation status

### Implemented on this branch
- Conservative five-sample health classifier with three-vote transitions and a minimum evidence gate.
- Health row and reason in the existing performance overlay.
- Source-labelled network RTT, decoder time, host-reported processing latency, and local MediaCodec release-to-render presentation delay.
- Presentation timing uses the MediaCodec frame-rendered callback and a bounded 64-entry PTS correlation map.
- Bounded 64-event local timeline for decoder recovery requested/completed, session disconnect, and missing WAN feedback.
- Long-press either performance overlay to explicitly open Android's share chooser for a report.
- Report metadata is allowlisted (codec, dimensions, FPS, bitrate, enum-only renderer/transport); no host address, hostname, pairing key, certificate, or session identifier is accepted by the report model.
- No automatic uploads or new background analytics worker.

### Still open
- Reconnect attempts/successes: the current connection lifecycle does not expose a distinct reconnect manager callback.
- Transport fallback: wire the event at the actual negotiated transport fallback owner when that code is present on the target branch.
- Report metadata still reports renderer/transport as Unknown until those values are exposed safely by the active runtime.
- Session report persistence/history integration and a dedicated health details view.
- Physical-device validation for callback timing, thermal/load impact, and threshold calibration.
- Low Latency, Balanced, and Quality presets through ProfilesManager after diagnostics behavior is validated.
