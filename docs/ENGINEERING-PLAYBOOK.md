# Occultation engineering playbook

This document defines how new streaming features are added without destabilizing the
Moonlight/Artemide foundation. It is intentionally implementation-oriented: future
work should extend existing seams before introducing new subsystems.

## Product quality model

Every feature must balance three outcomes:

1. **Capability** — it solves a real user problem and works across supported hosts/devices.
2. **Stability** — it has bounded resource use, deterministic lifecycle cleanup, safe
   defaults, tests, and a fallback when optional platform support is absent.
3. **Usability** — it is discoverable, understandable, accessible, profile-aware where
   appropriate, and does not surprise users or silently change stream behavior.

A feature is not complete when its UI exists or its code compiles. It is complete when
all three outcomes have evidence.

## Preserve the upstream architecture

Occultation is based on Artemide, which derives from Artemis and Moonlight Android.
Keep the `com.limelight` namespace and JNI/resource bindings, preserve upstream
copyright/license notices, and avoid broad rewrites of Moonlight core.

Prefer these existing extension points:

| Concern | Existing seam | Extension rule |
| --- | --- | --- |
| Stream preferences | `PreferenceConfiguration`, `StreamSettings`, `preferences.xml` | Add one canonical preference key/default/read path. Do not read raw preference keys from unrelated classes. |
| Per-game/session configuration | `ProfilesManager` overlaying SharedPreferences | Store stream choices in the existing profile system where users expect them. Avoid a second profile/session database. |
| Connection and host capability | `NvConnection`, `NvHTTP`, `StreamConfiguration` | Negotiate host-dependent behavior before streaming. Keep unsupported hosts on the normal Moonlight path. |
| Media decode/presentation | `VideoDecoderRenderer`, `MediaCodecDecoderRenderer`, `StreamView` | Preserve the standard MediaCodec/surface path as the reference fallback. Experimental renderers must be opt-in and capability-gated. |
| Input | Existing input handlers and Moonlight packet transport | Reuse native Moonlight input semantics; do not create parallel input protocols for an existing event type. |
| Session UI/lifecycle | `Game` and existing connection callbacks | Attach work to existing lifecycle callbacks and make stop/reconnect cleanup idempotent. |
| Diagnostics | Existing performance overlay and `PerformanceDataTracker` | Extend current metrics and export path; do not add a second always-on telemetry service. |

The table describes the current source structure, not permission to bypass its contracts.
Before changing a seam, inspect its callers, thread ownership, and teardown behavior.

## Required implementation pattern

For every new feature:

1. **Audit first.** Record the current behavior, relevant upstream implementation,
   call sites, preference/profile behavior, and any open PR that already contains work.
2. **Define the contract.** Document user-visible behavior, host/platform requirements,
   failure modes, privacy implications, and measurable success criteria.
3. **Extend, do not fork.** Reuse existing classes and protocols where possible. Add a
   small adapter/interface only when it isolates a real platform or protocol boundary.
4. **Default safely.** New transport/rendering/latency behavior is opt-in until it has
   passed device testing. Existing settings and sessions retain their current behavior.
5. **Bound resources.** Every queue, buffer, retry loop, cache, log, and worker needs a
   documented limit, owner, cancellation path, and teardown rule.
6. **Fail closed, then fall back.** Authentication, permission, or capability failures
   must not silently weaken security. Optional performance features may fall back to
   the standard path before a session starts; never switch transports/renderers midway
   unless the protocol explicitly supports a safe transition.
7. **Test the seams.** Unit-test pure policy/protocol logic; add Android/Robolectric
   tests for lifecycle and preference behavior; add emulator/device tests for platform
   integration. Include a regression test for every bug fixed.
8. **Measure on hardware.** Report startup time, frame pacing, decode/present latency,
   dropped frames, network RTT/loss, power/thermal behavior, and memory as relevant.
   Do not infer a Pixel 9a result from an emulator or desktop host test.
9. **Document operation.** Update user-facing help, developer notes, feature matrix,
   release notes, and troubleshooting guidance in the same change.
10. **Keep changes reviewable.** One feature or tightly coupled vertical slice per PR.
    Avoid combining cleanup, signing changes, transport changes, and UI redesigns.

## Feature roadmap and order

The active source and open PRs must be reconciled before implementation. The sequence
below is a priority order, not a claim that each item is absent.

### 0. Stabilize and reconcile existing work

- Establish one integration branch and a dependency map for PRs #7, #8, #10, #11, #12,
  and #13 before merging or rebasing any of them.
- Run the documented Gradle unit-test, lint, and APK tasks on the exact candidate SHA.
- Resolve duplicate or contradictory release documentation and ensure the build
  workflow's advertised features match the checked-out source.
- Keep production signing and public release operations separate from development work.

### 1. Make streaming diagnostics trustworthy

Extend the existing performance overlay/tracker rather than creating a new service.
Expose only measurements the client can actually observe, with clear labels for
measured, estimated, and host-reported values. Prefer rolling windows and bounded
history over unbounded per-frame storage. Diagnostics must be disableable and must not
block the render, audio, or input threads.

Initial metrics to standardize:
- negotiated resolution, refresh rate, codec, and configured bitrate;
- decoder output rate and decode timing when exposed by MediaCodec;
- frame presentation cadence, late/dropped frames where measurable;
- network RTT/loss and active connection mode where exposed by Moonlight;
- selected renderer/transport and whether a fallback occurred.

### 2. Improve decoder and presentation behavior

Keep MediaCodec and the current surface path as the baseline. First instrument the
existing path and identify the measured bottleneck. Then make narrowly scoped changes
to buffer sizing, frame pacing, surface lifecycle, and renderer selection.

Any EGL, HardwareBuffer, or vendor-specific path must:
- be optional and guarded by runtime capability checks;
- handle rotation, resize, pause/resume, surface loss, HDR/SDR, and display changes;
- release every EGL/GL/MediaCodec resource on all exit paths;
- fall back to the standard decoder surface without corrupting session state;
- demonstrate lower end-to-end latency or better pacing on physical hardware.

### 3. WAN feedback and adaptive bitrate: extend the existing v1 system

This is not a greenfield feature. Occultation already negotiates `SyzygyWanVersion=1`,
sends authenticated RTT/jitter/frame-gap feedback about once per second through
`NvHTTP.sendWanFeedback()`, and uses a retry/backoff worker owned by `NvConnection`.
Syzygy consumes that feedback for adaptive FEC and runtime bitrate changes on supported
FFmpeg libx264 and FFmpeg NVENC encoders. The host documentation states that native
Windows NVENC and VAAPI do not yet have a supported live reconfiguration path; multiple
simultaneous clients and pacing backlog also trigger conservative fallbacks.

Do not add a second client controller or a competing telemetry endpoint. The next
work is to validate and harden the existing contract: verify telemetry units and
semantics, retry/stop races, missing-feedback behavior, encoder capability reporting,
multiple-client behavior, and real cellular impairment. Preserve manual bitrate as
the configured ceiling and retain the host's conservative fallback. Any extension
must be version-negotiated, authenticated, rate-limited, bounded, and tested with
older hosts and clients.

### 4. Session/profile improvements

Use `ProfilesManager` and its existing preference overlay. Do not introduce a parallel
session database. New options should be profile-aware only when they describe a user's
repeatable stream setup; device-wide privacy, accessibility, and diagnostics choices
should remain global unless there is a clear reason otherwise.

Test import/export compatibility, missing fields, invalid values, profile switching,
and reconnect application. A profile must never change authentication identity or
weaken host permissions.

### 5. Optional capture/recording

Treat recording as a separate feature with explicit user consent and Android storage
picker integration. Prefer encoded stream capture to avoid re-encoding. Define supported
container/codec combinations, audio/video timestamp handling, bounded queues, storage
exhaustion behavior, interruption recovery, and finalization before implementing UI.
Do not silently record or upload anything.

### Deferred / excluded until independently justified

- RIFE/frame interpolation and neural upscaling remain deferred research, not active
  runtime features. Reconsider only with a standalone prototype, verified backend,
  and measured latency/thermal benefit on target hardware.
- Do not add a generic always-running analytics or telemetry uploader.
- Do not add host controls that require unsupported Syzygy protocol changes.
- Do not remove existing input, WAN, overlay, or profile features based on stale PRs.

## Validation matrix

| Layer | Minimum evidence |
| --- | --- |
| Pure logic | JUnit tests for policy, bounds, parsing, state transitions, and fallback decisions |
| Android integration | Robolectric/instrumented tests for preferences, lifecycle, permissions, and surface recreation |
| Build quality | Unit tests, lint, debug APK build, dependency/native build checks |
| Emulator | API 29 and current API level smoke tests; connect/disconnect and UI navigation |
| Physical devices | Pixel 9a (Tensor/Mali) plus at least one Adreno and one additional Mali device for renderer work |
| Host interoperability | Syzygy and a standard Moonlight-compatible host; test both supported and unsupported capability paths |
| Release readiness | Stable signer, upgrade path, checksums, changelog, explicit owner approval |

## Current baseline commands

Use the repository's documented toolchain (Java 17, Android SDK 36, NDK
27.0.12077973):

```sh
git submodule update --init --recursive
./gradlew :app:testNonRoot_gameDebugUnitTest :app:lintNonRoot_gameDebug :app:assembleNonRoot_gameDebug
```

A passing build is necessary but does not replace physical-device validation.
