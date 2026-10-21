# NNAPI neural frame interpolation: implementation status and integration contract

## Status

The adaptive policy and frame-admission controller are implemented and tested. They are deliberately not wired into the MediaCodec presentation path. A reusable native NNAPI compiler/executor and explicit ADD-graph execution test are now implemented (see below). Pinned RIFE Lite ONNX and ncnn RIFE v4 models now have explicit frame-pair inference APIs and a settings test (see rife-android.md). No decoder frame consumer, generated-frame stream renderer or live network feed is connected. There is no active frame-generation setting. Device enumeration from 0.1.8-dev is only diagnostics.

## Established methods to evaluate

- **Practical-RIFE lite** is the first model family to evaluate for reduced compute. Its project explicitly identifies lite checkpoints and states that linked checkpoints have the same MIT license as the project. Pin the exact model/code hashes when acquiring a checkpoint, and keep attribution. This is not evidence that a PyTorch checkpoint can run on NNAPI without conversion.
  - https://github.com/hzwer/Practical-RIFE
- **FILM** is a comparison/reference implementation from Google Research with published inference code and pretrained models. It is an established two-frame interpolation method, not an Android real-time or TPU performance guarantee.
  - https://github.com/google-research/frame-interpolation

Audit the actual exported graph, especially flow warping/resampling, dynamic indexing, supported tensor layouts and quantization. Convert and validate each operation on the selected device. Do not silently replace unsupported operations with CPU execution, simple frame blending or repeated frames while calling the result Tensor interpolation.

## NNAPI device and graph admission

On API 29+, select a specific accelerator reported by NNAPI, query support for the **entire chosen model graph**, and compile with `ANeuralNetworksCompilation_createForDevices` excluding CPU/reference devices. Reject unsupported graphs. A delegate that partitions only a few convolutions onto an accelerator does not satisfy the requirement to verify the interpolation model's intended hardware execution. Use supported compilation and execution deadline APIs, check all return values and model-driver profiling. Device names alone are not proof of Tensor/TPU execution.

NNAPI is deprecated on Android 15+. The current Google Tensor SDK path supports G5/G6, not Pixel 9a/G4, and Tensor model compilation is ahead-of-time. The legacy NNAPI route on G4 must therefore be measured on the actual phone before claiming feasibility.

References:
- https://developer.android.com/ndk/guides/neuralnetworks
- https://developers.google.com/edge/litert/next/tensor-sdk
- https://developers.google.com/edge/litert/next/npu

## Latency accounting

True midpoint interpolation needs both real frames. A missing future real frame cannot be reconstructed by interpolation. Uniform 30→60 presentation requires lookahead; it cannot promise free responsiveness. The policy conservatively budgets half a source-frame period **plus measured p95 conversion/inference/upload/render cost plus a 2 ms guard**. It first uses already configured playout buffering; any additional delay must fit an explicit user-approved latency allowance. Default extra allowance should be zero. This estimate must be replaced/confirmed by measured end-to-end scheduling on hardware.

The generated midpoint has its own presentation deadline. Real frames are never held by the admission controller. A completed generated frame is discarded if its deadline passed, the epoch changed, its work was invalidated, or a real frame at/past its midpoint has already been presented. Do not count generated FPS as received/decoded host FPS or imply lower network latency.

## Dynamic network policy implemented

`AdaptiveInterpolationPolicy` consumes an immutable, validated sample of source/display FPS, loss fraction, jitter, RTT/baseline RTT, decode backlog, thermal status, per-model p95 processing cost and latency budget. Thresholds below are initial conservative engineering values, not Pixel performance measurements.

| Condition | Decision |
| --- | --- |
| NNAPI graph not verified, no usable model, invalid values or telemetry older than 1.5 s | Bypass |
| Loss ≥3%, jitter ≥12 ms, RTT inflation ≥50 ms, decode backlog >1 frame, or thermal limit | Immediate bypass |
| Loss ≤0.5%, jitter ≤4 ms and RTT inflation ≤15 ms, with model/deadline budget available | Full model after stable recovery |
| Moderate conditions below bypass thresholds | Lite model, if independently ready and fast enough |
| Compute or added-delay budget cannot be met | Try verified lite profile, otherwise bypass |
| Display cannot present twice the source frame rate | Bypass |

After bypass, require 3 seconds of continuously valid, fresh conditions before re-enabling. Downgrade promptly; require a 1.5-second clean interval to upgrade model quality. Never lower host bitrate, change FEC or modify transport from this controller. A later integration can coordinate source FPS/bitrate changes through explicit host negotiation, not hidden client-side assumptions.

## Frame pairing implemented

`InterpolationAdmission` only issues a ticket for adjacent sequence numbers with increasing timestamps, matching epoch/resolution, no scene cut and a plausible source-frame interval. It permits one hardware execution in flight and no pending inference queue. New real frames continue through the normal path. It admits a result only before the midpoint deadline and only while the result remains relevant.

Invalidation does not release the active execution slot or in-use buffers. A backend must retain input/output memory until NNAPI completes or confirms cancellation, call `finish` exactly once in its completion/failure cleanup, and then release its buffers. A hung delegate must disable neural work while normal streaming continues; do not reuse memory merely because a deadline expired.

## Remaining renderer integration

1. Validate the bundled, pinned RIFE models on the phone. Resolve RIFE Lite GridSample/shape-operation compatibility before claiming NNAPI/Tensor support; use the real frame-pair test, not only enumeration.
2. Establish verified per-model delegation and full-frame timing for each backend. The generic native compiler/executor and ONNX NNAPI workflow exist, but neither proves Tensor silicon identity or full RIFE Lite delegation. Keep initialization outside the stream hot path.
3. Add a hardware-decoder texture/buffer consumer and a color-managed presentation stage. Current MediaCodec output goes directly to a Surface and does not expose model-ready image tensors.
4. Feed **non-destructive** telemetry snapshots to the policy. Do not consume `takeWanLoss()` twice: it resets the counters used by WAN adaptation. Snapshot loss and RTT variance at the existing sampling point, record arrival variation separately, and do not relabel RTT variance as actual frame-arrival jitter. Reject stale samples.
5. Evaluate before every admission, invalidate pending results on any bypass/scene cut/resolution change/reconnect, preserve real-frame priority, and use one monotonic clock domain for all deadlines and completion timestamps.
6. Expose Off / Experimental NNAPI interpolation only after this path works. Show requested versus verified active accelerator, model, real/generated FPS, p95 total cost, added playout delay, dropped synthetic frames and bypass reason.
7. Replay loss/jitter/bufferbloat traces, including 300 ms RTT and 33% loss, then measure on the actual Pixel. Include thermal/battery soak, camera pans, text/HUD, cuts, HDR, rotation, disconnect and resume. Do not claim real-time Tensor interpolation from these policy tests.

## Validation

`tests/neural/InterpolationScenarios.java` is a standalone Java scenario suite run locally and in Android CI. It checks network degradation/recovery, stale/invalid telemetry, hardware gating, latency/compute/display budgets, quality hysteresis, bounded execution, late results, real-frame priority, frame loss, scene cuts, reconnects and resolution changes. These tests validate scheduling/admission only, not image quality, NNAPI inference or renderer performance.


## Native Android accelerator backend (development branch)

The native `NnapiRuntime` now loads public NNAPI functions dynamically and implements
full operation-support checks, explicit single-ACCELERATOR compilation, low-latency
compilation preference, synchronous tensor-buffer execution, and cleanup on every
reported error. CPU, GPU, unknown and reference devices are excluded from this NPU
path. This restriction avoids presenting general GPU or CPU execution as AI-chip
execution; an ACCELERATOR driver still does not establish the identity of its silicon.
There is no default-device compilation or software retry.

Settings → Video → **Test AI accelerator (NNAPI)** runs a four-element float32 ADD
graph on each exposed ACCELERATOR driver, checks the result, and reports one cold
execution wall time including the execution setup and buffer-binding calls. Compilation
is not included in that number. This small graph is an integration smoke test, not a
neural image model, a useful performance benchmark, or proof of interpolation support.
The existing hardware report remains enumeration-only. Diagnostics are explicit,
run on a worker thread, and do not modify stream rendering. A process-wide guard
prevents concurrent execution tests across activity recreation. Driver compilation
and execution timeout requests are advisory; buffers and runtime objects remain alive
until the driver returns. There is no unsafe timeout-driven buffer reuse.

The reusable backend accepts a finished native NNAPI graph and its exact operation
count; the trusted graph builder must supply that count. It does not parse arbitrary
model files or import TFLite graphs. Integration of a trained/exported model, image
buffer conversion, measured p95 costs, and decoder/render scheduling remains pending.
Nothing in this change marks `nnapiGraphVerified` true for the interpolation policy.

Validation: eight host fake-driver scenarios exercise real dynamic symbol loading,
CPU exclusion, full graph support rejection, compilation/binding/execution failures,
wrong-output rejection, and object cleanup. A separate unavailable-runtime case
checks graceful failure. These tests do not claim physical NPU execution. Android CI
also compiles the actual native module for packaged ABIs and runs existing app checks.

Documented interfaces:
- https://developer.android.com/ndk/guides/neuralnetworks
- https://developer.android.com/ndk/reference/group/neural-networks
- https://developer.android.com/ndk/guides/neuralnetworks/migration-guide

NNAPI is the public Android compatibility backend, not a universal guarantee of NPU
access. It is deprecated on Android 15 and later. A future LiteRT/vendor backend must
meet the same graph verification and measured latency contract; Google Tensor brand
names, SoC strings, and an available library alone must never enable frame generation.
