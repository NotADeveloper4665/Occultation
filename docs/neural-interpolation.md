# NNAPI neural frame interpolation: implementation status and integration contract

## Status

The adaptive policy and frame-admission controller are implemented and tested. They are deliberately not wired into the MediaCodec presentation path. No interpolation model, NNAPI model executor, frame conversion, generated-frame renderer or live network feed is implemented by these classes. There is no active frame-generation setting. Device enumeration from 0.1.8-dev is only diagnostics.

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

1. Acquire and pin a trained model and audit/export its graph. Test actual NNAPI support on the phone using the copyable hardware report as the first diagnostic.
2. Implement explicit-device NNAPI compilation, model cache, timing and verified per-model delegation. Keep model initialization outside the stream hot path.
3. Add a hardware-decoder texture/buffer consumer and a color-managed presentation stage. Current MediaCodec output goes directly to a Surface and does not expose model-ready image tensors.
4. Feed **non-destructive** telemetry snapshots to the policy. Do not consume `takeWanLoss()` twice: it resets the counters used by WAN adaptation. Snapshot loss and RTT variance at the existing sampling point, record arrival variation separately, and do not relabel RTT variance as actual frame-arrival jitter. Reject stale samples.
5. Evaluate before every admission, invalidate pending results on any bypass/scene cut/resolution change/reconnect, preserve real-frame priority, and use one monotonic clock domain for all deadlines and completion timestamps.
6. Expose Off / Experimental NNAPI interpolation only after this path works. Show requested versus verified active accelerator, model, real/generated FPS, p95 total cost, added playout delay, dropped synthetic frames and bypass reason.
7. Replay loss/jitter/bufferbloat traces, including 300 ms RTT and 33% loss, then measure on the actual Pixel. Include thermal/battery soak, camera pans, text/HUD, cuts, HDR, rotation, disconnect and resume. Do not claim real-time Tensor interpolation from these policy tests.

## Validation

`tests/neural/InterpolationScenarios.java` is a standalone Java scenario suite run locally and in Android CI. It checks network degradation/recovery, stale/invalid telemetry, hardware gating, latency/compute/display budgets, quality hysteresis, bounded execution, late results, real-frame priority, frame loss, scene cuts, reconnects and resolution changes. These tests validate scheduling/admission only, not image quality, NNAPI inference or renderer performance.
