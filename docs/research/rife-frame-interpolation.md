# RIFE frame interpolation — research archive

Status: **deferred; not part of the active Occultation feature set**.

This document preserves the investigation so frame interpolation can be reconsidered as a separate project. It is not a claim that RIFE currently works in Occultation.

## Why it was deferred

The earlier experiment did not establish a complete, shippable Android frame-interpolation path. The historical work referenced:

- ONNX Runtime for Android (the experimental branch added `com.microsoft.onnxruntime:onnxruntime-android:1.22.0`).
- A pinned model preparation/validation step using `py7zr` and `onnx`.
- NNAPI device enumeration/native probing.
- Adaptive interpolation admission and native-backend tests.

The experimental workflow referenced `scripts/interpolation/prepare.py`, `scripts/interpolation/validate_models.py`, and neural test paths. The PR history does not establish a validated end-to-end runtime, a compatible packaged model, successful inference on the target phone, or a working presentation path. Device enumeration is not proof that a model is delegated to a useful accelerator.

The old RIFE work was mixed into a stale PR that also carried unrelated AHardwareBuffer/Vulkan renderer changes. That PR must not be merged wholesale as a way to restore or remove interpolation.

## Platform findings and constraints

- Frame interpolation is fundamentally a **client-side** feature. It can be implemented without changing the Syzygy host protocol: the Android client would infer intermediate frames from decoded frames and present them locally.
- It is not free. The client must access decoded frames before display, convert them into the model's input format, run inference, and schedule the generated frame. A normal MediaCodec-to-Surface path does not expose frames for arbitrary model processing.
- Pixel 9a uses Google Tensor G4 and a Mali GPU. Do not infer a supported TPU/NPU path from the presence of Tensor branding or a Mali GPU.
- NNAPI device enumeration alone cannot establish model compatibility, delegation, accelerator execution, frame latency, or power consumption. NNAPI is deprecated on modern Android; a future implementation should not make it a required foundation.
- At 60 FPS, the display interval is 16.67 ms. Decode, frame transfer, color conversion, inference, synchronization, and presentation all consume that budget. A feature that adds latency or queues stale frames is a regression, even if it increases a nominal frame counter.
- Remote desktop and game streaming content includes text, UI edges, cursor movement, and abrupt scene changes. These need explicit quality testing; interpolation artifacts can be more damaging than a lower frame rate.

## Requirements before reopening the feature

1. Treat interpolation as an optional, independently tracked feature with no effect on the default stream path.
2. Select a maintained runtime and a genuinely trained, appropriately licensed low-latency model. Record model provenance, license, input/output layout, precision, and supported operators.
3. Prove the chosen runtime/backend on the actual Pixel 9a. Report the backend that really executed the model; never label CPU fallback or device enumeration as TPU/GPU acceleration.
4. Prototype the decoded-frame capture and presentation path separately from QUIC. Preserve the existing MediaCodec Surface path as the reliable fallback.
5. Keep at most one pending inference job. Drop stale frames rather than building a queue. Do not delay original frames while waiting for interpolation.
6. Measure end-to-end added latency, frame pacing, dropped frames, visual artifacts, memory, battery, and thermal throttling over sustained sessions.
7. Test remote desktop text, mouse cursors, fast camera motion, scene cuts, variable frame rates, reconnects, rotation, background/resume, and low-bandwidth sessions.
8. Ship only if it improves perceived motion on supported devices without degrading input-to-photon latency or stream stability. Otherwise leave it disabled or abandon the experiment.

## Revisit strategy

Start with a standalone proof of concept that consumes a short recorded decoded-frame sequence and produces timestamped output frames. Benchmark candidate runtimes and models on the Pixel 9a before integrating with live streaming. Only after that succeeds should a separate feature proposal define the Android renderer integration, settings, telemetry, fallback behavior, and device support matrix.

No Syzygy server-side changes are inherently required for this design. Server-side frame-rate changes or protocol work should be considered only if a measured limitation cannot be solved in the client.

## Scope boundary

This research is archived separately from active streaming work. It does not authorize changes to Syzygy QUIC, EGL single-buffer presentation, touch/controller input, overlays, or unrelated settings.
