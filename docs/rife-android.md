# Android RIFE backends (development)

Both paths reuse existing trained models. No model is trained by Occultation.

| Path | Bundled model | Runtime | Implemented status |
| --- | --- | --- | --- |
| NNAPI / Tensor candidate | Practical-RIFE Lite 4.25, vs-mlrt v2 ONNX export | ONNX Runtime Android 1.22.0 NNAPI provider | Model installation, fixed shape specialization, RGB/NCHW conversion and execution API. Strict CPU fallback rejection. Full graph support is not established. |
| Vulkan GPU | RIFE v4 from nihui/rife-ncnn-vulkan | ncnn Android Vulkan 20240410 | Native model loading, explicit Vulkan GPU selection, RGB frame-pair inference and GPU readback. CPU model fallback is rejected. Physical-device performance is unverified. |

These are **different model versions**. The Vulkan backend is not labeled RIFE Lite.
The ONNX model contains GridSample, ConvTranspose and dynamic shape operations;
GridSample is absent from the documented NNAPI EP operator list. Expect the strict
NNAPI session to reject graphs it cannot fully delegate. This does not mean the
phone lacks an AI chip. It is a model/operator compatibility problem. NNAPI may
select either GPU or NPU; session success does not establish Google Tensor/TPU identity.
No policy flag is marked verified just because a session or the small ADD test passes.

## Try the real models

In a build from this branch, open **Settings → Video → Test RIFE frame interpolation**.
Choose Vulkan GPU or NNAPI. A worker thread generates a middle frame between two
64 × 64 test inputs. The result includes a first/generated/second image and timings,
or an explicit unsupported/error report. Tests serialize across activity recreation.
There is no hidden CPU retry. The Vulkan timing includes RGB upload, execution and
readback; NNAPI timing includes RGB/NCHW conversion and output conversion. Loading
and compilation are excluded. Three warm samples are reported as a maximum, not p95.
This tiny test does not demonstrate 720p/1080p throughput or image quality.

## Build

Install Python `py7zr==1.1.4` (and `onnx==1.17.0` for the graph validation script).
`prepareRife` runs before native builds and asset merging, and can be run directly:

```
python3 scripts/interpolation/prepare.py
python3 scripts/interpolation/validate_models.py
```

The lock file pins upstream source commits, SDK/archive/model SHA-256 values,
source licenses and conversion provenance. Downloads are verified before use;
model files are bundled into the APK, then verified again before installation in
private app storage. No model is downloaded by the phone and no Python checkpoint
code is executed on Android. Generated files and large binaries stay in app/build.
The upstream author lists the Practical-RIFE linked models under MIT; the ONNX
conversion is from AmusementClub/vs-mlrt's external-models release.

The existing NDK build compiles the upstream RIFE kernels into a JNI library and
links the pinned ncnn static SDK. Android requires API 24+ to open the Vulkan path;
other app features retain minSdk 21. The original RIFE shader generation is reproduced
by the preparation script. Local changes to pinned upstream source add checked model
loading, reject layers without Vulkan support (apart from structural Input/Split),
remove the selected v4 GPU path's CPU retry, check extractor/submission results and
retain/reclaim allocators on error. No TTA/UHD mode is enabled.

## API and lifecycle

`VulkanRife` owns one native model session and accepts distinct direct RGB24 buffers
and a timestep strictly between zero and one. Calls and close are serialized. JNI
bounds-checks frame sizes/capacities and reports model/GPU/execution failures. GPU
commands finish before returning or releasing input/output memory. Vulkan instance
lifetime is process-wide; sessions release their nets/pipelines on close.

`NnapiRifeLite` owns an ONNX Runtime session specialized to dimensions divisible by
32. It uses the seven-channel export: first RGB, second RGB, timestep, normalized to
[0,1]. NNAPI CPU devices are disabled and ORT's CPU execution-provider fallback is
also disabled; setting only the former would allow unsupported layers to use ORT
CPU kernels. Returned shape and finite float output are checked. The native explicit-
accelerator ADD diagnostic remains separate from this higher-level ONNX workflow.

## Stream status and remaining work

The MediaCodec decoder still renders directly to its Surface. Neither backend is
connected to active stream presentation yet. The existing adaptive policy and frame
admission tests remain applicable, but they do not establish image inference support.
Next integration requires a decoder frame consumer, color-managed image conversion,
real/generated presentation timestamps, measured p95 end-to-end cost, non-destructive
network snapshots, and late-result/thermal/resolution-change cleanup. Preserve real
frame priority and default to zero additional playout budget. The RGB CPU staging
APIs here are functional integration paths, not a zero-copy claim.

Sources:
- https://github.com/nihui/rife-ncnn-vulkan
- https://github.com/Tencent/ncnn/wiki/FAQ-ncnn-vulkan
- https://github.com/hzwer/Practical-RIFE
- https://github.com/AmusementClub/vs-mlrt/releases/tag/external-models
- https://onnxruntime.ai/docs/execution-providers/NNAPI-ExecutionProvider.html
