# Experimental Android hardware-buffer video display

## Audit and scope

The previous branch built and tested real RIFE frame-pair APIs, but still displayed
streams through MediaCodec directly to SurfaceView. ncnn Vulkan compute did not
create a display swapchain. A vkQueuePresentKHR change could not affect that path.
The new, opt-in display backend connects decoder output to a PRIVATE ImageReader
with GPU_SAMPLED_IMAGE usage and imports its HardwareBuffers into Vulkan. It still
uses MediaCodec and an ImageReader Surface; it does not replace decoding with an
AHardwareBuffer API or claim SurfaceView normally requires CPU copies.

An untracked OpenSSL temporary archive was moved out of the repository into a
scratch backup. Audit fixes include MIME checks on preferred decoders, official
hardware/software decoder classification, c2.google/c2.android/omx.google software
name exclusion, and null-safe decoder cleanup/renderer-thread shutdown after failed
setup. Emulator/ChromeOS software exceptions remain as before.

## User controls

Settings → Video:
- **Vulkan video output (experimental)** selects the new path on Android 13+.
- **Prefer AHardwareBuffer on MediaTek** selects it only on detected MediaTek SoCs.
  A Mali GPU alone is not MediaTek detection; Google Tensor is not classified as MTK.
- **Arm ASR availability** explains the missing input resources. It is a status
  report, not an enabled upscaler.

Both display choices default off. SDR fit/fill/stretch are implemented. HDR uses the
standard path; dynamic HLG/PQ frames also trigger standard-output recovery. Vulkan
initialization/import/execution errors disable this backend for the connection and
request codec restart on its existing recovery path. A driver that cannot release
GPU work cannot be safely cleaned up through an arbitrary host timeout.

## Ownership and synchronization

One HandlerThread owns every Vulkan call, including acquire, command recording,
queue submit and vkQueuePresentKHR. The video decoding threads release codec output
to ImageReader; they never call vkQueuePresentKHR. The queue is not concurrently
accessed from another host thread. There is one GPU submission in flight; present
wait semaphores are per swapchain image, not recycled solely because a submit fence
signaled. Swapchain images use FIFO. FIFO follows the current display mode and does
not itself select 120 Hz. Existing Android display-mode selection remains responsible
for choosing a refresh rate.

The reader keeps at most three Images and acquires the latest. API 33 is required
so its public SyncFence is available. The producer fence is checked before reading
an image. A frame whose producer is not ready within 5 ms is dropped. Each imported
image has FOREIGN→graphics and graphics→FOREIGN ownership/layout barriers. The Image
stays open until GPU sampling and release finish. The submit fence is waited on the
presentation thread before Image.close; GPU resources are never freed merely because
a deadline elapsed. Device-idle waits occur during swapchain recreation/teardown,
not in the normal per-frame render loop. This initial bounded implementation favors
correct ownership over overlapping multiple submissions; no measured speedup is claimed.

The renderer validates device/extension/YCbCr support, imports dedicated AHB memory,
caches at most eight input resource bundles, and uses immutable sampler conversions.
It retains buffers while cached but Image lifetime controls when the producer may
reuse them. External-format descriptor pools reserve spare slots and allocation
failure returns to normal output. Crop, negotiated color matrix/range and per-frame
DataSpace are honored. Unsupported pre-rotation or display formats fall back.

The fragment shader performs one sampler lookup. It contains no custom YUV→RGB
matrix math. VK_KHR_sampler_ycbcr_conversion is a Vulkan API mechanism; the driver's
implementation determines whether conversion uses fixed hardware. This is not a
promise that all Mali execution slots become free.

## Decoder latency and Arm ASR

Decoder selection uses supported MIME/profile/capabilities and Android hardware flags,
not a fixed component name. c2.google.av1.decoder is not a hardware-decoder target.
KEY_LOW_LATENCY and PARAMETER_KEY_LOW_LATENCY are used on API 30+ through the existing
ordered configure/retry path. Android has no portable decoder slice-thread count key;
no invented MediaFormat key is added.

Arm Accuracy Super Resolution is a temporal upscaler. Its documented inputs include
rendered color, depth, motion vectors, jitter and frame/camera parameters. A decoded
Moonlight desktop video supplies color but not the original game's depth, motion or
jitter. Input precondition checks and the availability UI are added. The ASR library
and dispatch are not integrated, and no fake constant depth or motion data is
supplied. An actual ASR path needs a host/game metadata protocol and a compatible
capture source; optical-flow vectors alone would not supply all required data.

RIFE stream interpolation remains separate from this renderer: no generated RIFE
frames are inserted by this change. Public release publication is not part of this
experimental branch update.

Sources:
- https://developer.android.com/reference/android/media/ImageReader
- https://developer.android.com/reference/android/media/Image#getFence()
- https://developer.android.com/reference/android/media/MediaCodecInfo
- https://developer.android.com/reference/android/media/MediaFormat#KEY_LOW_LATENCY
- https://docs.vulkan.org/refpages/latest/refpages/source/vkQueuePresentKHR.html
- https://docs.vulkan.org/guide/latest/extensions/VK_KHR_sampler_ycbcr_conversion.html
- https://docs.vulkan.org/refpages/latest/refpages/source/VK_ANDROID_external_memory_android_hardware_buffer.html
- https://github.com/arm/accuracy-super-resolution-generic-library
