# Android QUIC and low-latency presentation

Status: implementation foundation / draft. The current app does not yet have an end-to-end QUIC client or direct HardwareBuffer presentation path. Do not expose either option as enabled until the complete data path is integrated and verified.

## Goals

- Keep the existing Moonlight UDP/ENet and SurfaceView paths as reliable defaults.
- Add an opt-in Syzygy QUIC transport that is wire-compatible with the host relay.
- Add an opt-in experimental low-latency renderer with capability detection and safe fallback.
- Measure input-to-photon latency and actual HWC composition; do not promise a fixed latency reduction.

## QUIC client contract

The reference host implementation is `NotADeveloper4665/Syzygy`, `transport/syzygy-quic.py`. Its current experimental SQ1 contract is:

- QUIC v1 ALPN: `syzygy-quic-1`.
- Host identity: SHA-256 certificate fingerprint pin.
- Client authorization: the paired client certificate is checked against Syzygy's saved device list and its streaming permission bits; the client signs `b"Syzygy QUIC client authentication v1\\x00" + challenge` using RSA PKCS#1 v1.5 with SHA-256.
- Authentication is a newline-delimited JSON exchange on bidirectional stream 0: `hello`, `challenge`, `certificate` + `signature`, then `authorized`.
- Client control uses one reliable bidirectional QUIC stream with a JSON `{"kind":"control"}\\n` preface and 32-bit big-endian length-prefixed UDP payloads.
- TCP channels use bidirectional streams with a JSON `{"kind":"tcp","port":N}\\n` preface. The host only accepts the configured HTTPS and RTSP forwarding ports.
- Media UDP channels use QUIC DATAGRAM frames. Each fragment is a 10-byte network-order header: uint16 port, uint32 sequence, uint16 fragment index, uint16 fragment count; payload chunks are at most 1000 bytes. Reassembly is bounded to 128 pending packets, 256 KiB, 100 ms, and 66 fragments.
- The client binds local UDP sockets for the three streaming channels and local TCP listeners for the HTTPS and RTSP channels. The Android Moonlight core must connect to these loopback endpoints without changing its higher-level protocol behavior.

The Java datagram codec in this branch implements only the framing/reassembly layer. It does not create a QUIC session, authenticate the host/client, forward TCP, or connect Moonlight-core. Those pieces require a maintained Android-compatible QUIC implementation (preferred: MsQuic via JNI), certificate/key access from the existing Syzygy pairing store, bounded backpressure, cancellation, and lifecycle ownership. Do not silently substitute Cronet: its public API does not provide the raw bidirectional QUIC streams plus DATAGRAM behavior required by SQ1.

### QUIC setting behavior

The user-facing toggle must remain hidden until the native QUIC session, certificate pinning, SQ1 authentication, local TCP/UDP forwarding, and Moonlight-core loopback routing are complete. When exposed, it must default off, be scoped per host/profile, show transport state, and only fall back to legacy transport after a clear connection failure (never switch transports mid-session without restarting the stream).

## Rendering paths

### Path A: Adreno / EGL mutable render buffer

- Probe the current EGL display's extension string at runtime; do not infer support from GPU brand alone.
- Require the mutable-render-buffer extension and successful surface creation/attribute negotiation.
- Keep the normal SurfaceView path as fallback. A successful extension query is not proof that single-buffer presentation or direct scanout is actually active.
- Keep the renderer and decoder ownership on a single GL thread. Treat EGL surface loss, context loss, and display rotation as hard fallback conditions until recovered.

### Path B: API 33+ HardwareBuffer + SurfaceControl

- Allocate buffers with `USAGE_GPU_COLOR_OUTPUT | USAGE_GPU_SAMPLED_IMAGE | USAGE_COMPOSER_OVERLAY`; do not require `USAGE_FRONT_BUFFER`. If the allocator rejects the requested usage, fail the experimental path cleanly.
- Import each HardwareBuffer into EGL using the Android EGL native-client-buffer and EGLImage extensions, then attach it to an FBO. Resolve the native entry points at runtime and validate all pointers before use.
- Keep the original EGL window surface current as required by the device-specific path, but do not call `eglSwapBuffers` as a frame submission mechanism. Keep the context alive using a documented, tested pbuffer/window-surface strategy.
- Submit buffers with `SurfaceControl.Transaction.setBuffer()` and release them only after the presentation/release fence signals. Do not reuse a buffer while HWC may still be reading it.
- HWC may select DEVICE/overlay composition, but the app cannot force that decision. Detect and log actual composition/latency where available; do not claim guaranteed overlay bypass.
- MediaTek landscape transforms and scaling can force CLIENT composition. Experiment with a portrait-sized producer surface plus a GL rotation, but gate this behind per-device capability tests. Do not force portrait app orientation globally.
- If `USAGE_FRONT_BUFFER` is rejected, retry only with the supported composer/GPU usage combination. Do not assume that this will result in front-buffer semantics.

## Input transform

`PortraitSurfaceTransform` provides pure coordinate transforms for the exact 90-degree clockwise GL transform. The caller must only apply it when that GL transform is active, and must use the actual producer-buffer dimensions. It is not wired into touch/mouse input yet.

## Lifecycle and safety requirements

- Experimental rendering must be disabled by default and must never replace the working SurfaceView renderer unless all allocations, EGL imports, FBO checks, and SurfaceControl submission checks pass.
- Handle acquire/release fences, buffer queue depth, resize, orientation change, pause/resume, decoder flush, and Surface destruction.
- Never call GL or EGL entry points from arbitrary UI threads.
- Never leak HardwareBuffer, EGLImage, EGLSurface, EGLContext, SurfaceControl, or native allocations across stream restarts.
- Provide a runtime kill switch and record the selected renderer, GPU vendor/renderer, extension list, allocation flags, composition type if observable, frame pacing, dropped frames, and presentation timestamps.

## Acceptance tests

1. SQ1 codec interoperability tests against the Python host implementation, including malformed fragments, out-of-order delivery, duplicates, expiry, and memory limits.
2. QUIC integration tests for valid pin, wrong pin, unpaired client, revoked permissions, reconnect, and cancellation.
3. Existing UDP streaming remains unchanged with QUIC disabled.
4. Renderer tests on Adreno, Mali-G57, and MediaTek devices; verify actual composition mode and measure latency rather than inferring it from API success.
5. Touch/mouse/trackpad coordinates round-trip under the portrait-surface transform.
6. Repeated start/stop, orientation change, app backgrounding, and Surface destruction do not leak native resources or crash.
