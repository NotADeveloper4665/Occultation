# Android QUIC and low-latency presentation

Status: implementation integrated behind opt-in settings; automated build/test and physical-device validation are still required. Neither experimental option is enabled by default.

## QUIC transport

The Android client now uses a Rust Quinn QUIC v1 engine through JNI. The transport is wire-compatible with Syzygy's `transport/syzygy-quic.py` SQ1 relay.

- ALPN: `syzygy-quic-1`.
- TLS host identity: exact SHA-256 DER-certificate pin, with X.509 validity checked against the paired host certificate.
- SQ1 client proof: paired client certificate plus RSA PKCS#1 v1.5 / SHA-256 signature over the protocol domain separator and 32-byte challenge.
- Authentication uses bidirectional stream 0. The control stream and TCP streams are reliable bidirectional streams.
- Media UDP uses QUIC DATAGRAM frames with the bounded 10-byte SQ1 fragmentation header and Java reassembly codec.
- Local TCP proxies: 47984 and 48010. Local UDP proxies: 47998, 47999, and 48000. The host relay listens on UDP 48020.
- Moonlight-core is routed to loopback only after QUIC authentication and all local proxy sockets succeed. The regular transport remains the default.
- `checkbox_syzygy_quic` is opt-in. Setup failure before Moonlight-core starts reports the failure and uses legacy transport. There is no mid-session transport switching.

The QUIC implementation still needs an interoperability run against a live Syzygy host, permission-revocation testing, reconnect/cancellation tests, and packet-loss/backpressure validation on Android networks.

## EGL single-buffer presentation

The `checkbox_front_buffer_rendering` setting enables a separate experimental decoder-output path:

1. MediaCodec decodes into a `SurfaceTexture`.
2. An EGL/GLES thread samples the external texture and draws to the existing display Surface.
3. The renderer requests `EGL_SINGLE_BUFFER` through `EGL_KHR_mutable_render_buffer` only when the extension is advertised and the surface accepts the attribute.
4. `EGL_ANDROID_front_buffer_auto_refresh` is enabled when available.
5. If initialization is unsupported, the app keeps the normal MediaCodec-to-Surface path. If single-buffer presentation fails during operation, the renderer attempts to switch the mutable surface to back-buffer mode.

The experimental EGL path is limited to landscape SDR streams. HDR, portrait, unsupported GPUs/surfaces, and initialization failures use the standard MediaCodec output Surface.

**Important limitation:** single-buffer EGL presentation may reduce queued-buffer latency, but it does not remove SurfaceFlinger or guarantee HWC overlay/direct scanout. The compositor may still choose CLIENT composition. No fixed latency reduction is claimed; measure input-to-photon latency and actual composition on each device.

## Validation still required

1. Build all four Android ABIs and run the Java/native CI suite.
2. Interoperate with the current Syzygy relay: valid pin, wrong pin, unpaired client, revoked permission, TCP forwarding, control stream, UDP DATAGRAM fragmentation, reconnect, and cancellation.
3. Verify the legacy transport remains unchanged with QUIC disabled and after setup failure.
4. Validate EGL single-buffer support and stable rendering on Adreno, Mali, and MediaTek devices; measure latency and composition rather than inferring success from extension presence.
5. Test pause/resume, rotation, decoder flush, Surface destruction, repeated stream start/stop, and native resource cleanup.
