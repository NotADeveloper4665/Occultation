# Neural upscaling investigation

## Implemented in 0.1.8-dev

Settings → Video → Neural upscaling hardware report queries the actual installed NNAPI runtime for exposed device names, types, driver versions and feature levels. Copy the report for device testing. This read-only operation runs outside the UI thread, dynamically loads the native NNAPI library only for the query, and leaves MediaCodec's normal Surface rendering intact. Older Android versions and missing native libraries produce an explicit unavailable message.

This build does not contain a trained upscaling model or perform neural inference. Enumeration is not evidence of a compatible super-resolution model, TPU execution or suitable frame latency. CPU and GPU devices are labeled as such. No neural-upscaling switch is enabled by this report.

## Verified API limitation

The requested target is Pixel 9a / Google Tensor G4. Google's current supported Tensor SDK path lists G5 and G6, not G4. The SDK's Tensor backend requires ahead-of-time compiled models; arbitrary runtime compilation is not supported. NNAPI is deprecated from Android 15 and may use CPU execution when a vendor accelerator is absent.

- https://developers.google.com/edge/litert/next/tensor-sdk
- https://developers.google.com/edge/litert/next/npu
- https://developer.android.com/ndk/guides/neuralnetworks

AMD FSR and Tensor neural super-resolution are separate algorithms/backends. This work follows the user's choice of neural upscaling, not a relabeled GPU FSR shader.

## Required implementation sequence

1. Inspect the Pixel 9a report. A legacy NNAPI accelerator may warrant investigation, but a name alone does not prove that its operations can execute the selected model. Do not infer TPU availability from the Pixel brand or SoC string.
2. Select a genuinely trained, appropriately licensed low-latency 2× super-resolution model. Measure reconstruction quality on remote desktop text and moving game content. Fixed resize/sharpen operations and random weights must not be described as neural super-resolution.
3. For supported G5/G6 devices, obtain the Tensor SDK/compiler access, compile the model ahead of time, bundle compatible runtime libraries and pin model/runtime versions. For G4, only investigate a legacy NNAPI delegate after the device report; otherwise neural GPU inference must be disclosed as GPU execution.
4. Replace direct decoder-to-display rendering only for an explicitly selected experimental mode: decoded frame → color-managed model input → inference → output texture → display. Preserve normal hardware decode and correct aspect, orientation, HDR/SDR handling and timestamps. A MediaCodec Surface cannot simply be passed to a neural model.
5. Use at most one pending frame, never a growing inference queue. Include conversion/upload/inference/display in timing. Drop stale work and return to the normal stream if the end-to-end budget is repeatedly exceeded. Preserve controls and lifecycle cleanup across rotation, disconnect and resume.
6. Require per-model delegation evidence and physical-device measurements before reporting Tensor active. No silent CPU fallback in the streaming path. Show requested and verified active backends separately, with model name, timing and fallback reason in statistics.
7. Validate quality and 30/60 FPS latency, battery/thermal behavior and recovery on actual devices. For 60 FPS the whole frame budget is 16.67 ms; inference is only part of that budget. Do not claim performance from desktop tests or NNAPI enumeration.

## Completion status

Button labels and diagnostics are implemented. Neural stream upscaling, model packaging, Tensor delegation and device performance validation remain unimplemented. The current G4 SDK limitation cannot be fixed by adding an Android preference or linking an NNAPI library.
