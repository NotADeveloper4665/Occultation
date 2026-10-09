# Eclipse feature port

## Prepared foundation

- Independent Occultation application IDs and labels.
- Artemide decoder and frame release implementation retained.
- Universal and per-ABI development APK build workflow.

## Implemented in 0.1.1-dev

- Syzygy challenge/response pairing with authenticated server certificate pinning.
- Optional connection-key field in Add Computer; normal PIN pairing retained.
- View-only mode and keyboard, pointer and controller permissions, including
  blocking clipboard uploads when keyboard input is disabled.
- Profile-aware permission settings; session configuration applied at connection.

## Remaining implementation order

1. **Syzygy passkey pairing:** IP address plus optional connection-key field in
   Add Computer. Authenticate the server confirmation before pinning its
   certificate. Never persist the shared passkey or log pairing query strings.
   Keep traditional PIN pairing available for other hosts. Test invalid keys,
   expired/replayed challenges, certificate substitution and reconnects.
2. **Controls:** view-only mode, separate keyboard/mouse/controller permissions,
   and connect directly to Desktop. Enforce permissions at every input send path.
3. **Profiles and stats:** extend existing Android profiles rather than duplicate
   them. Compact stats must report the active decoder/render path accurately.
4. **Steady frame delay:** monotonic release schedule with a millisecond target,
   measured queue residence and gradual adjustment. Preserve lower-delay mode as
   a separate choice. Test variable refresh rates and network jitter.
5. **Recording:** capture encoded video/audio without re-encoding; choose a
   container supporting negotiated codecs. Use Android storage selection,
   bounded queues, clean finalization and recovery from disconnect/storage errors.
6. **Upscaling:** inspect Artemide experimental rendering branches before choosing
   a portable shader implementation. Support Adreno, Mali and other GPUs through
   runtime capability checks. Disable recording while FSR is active, matching
   Eclipse's current policy; do not require a Qualcomm-specific dependency.
7. **Adaptive bitrate:** port only after checking the actual Syzygy control
   endpoint and congestion behavior; retain a manual bitrate override.

Microphone/webcam forwarding needs Android capture permissions, lifecycle handling
and a corresponding host protocol. Desktop USB/IP forwarding is not generally
available on ordinary unrooted Android; keep it out of the standard build until a
supported design exists. Tailscale compatibility can use VPN-reachable addresses;
its desktop CLI discovery cannot simply be copied into Android.

Kyber is not implemented in Eclipse or this foundation. Gamescope/mailbox desktop
integration is Linux-specific and cannot be exposed as an Android setting.

## Build and validation

Run `.github/workflows/android.yml` manually after pushing to the Occultation
fork. This produces development APKs and runs unit tests plus Android lint.
These are not signed production releases. A stable private release signing key
and physical-device tests are required before publishing installable releases.

Device coverage should include Qualcomm/Adreno, MediaTek/Mali, Tensor/Mali, older
API levels and an Android TV target. Validate audio/video sync, reconnects,
background/foreground lifecycle, HDR, high refresh rates and thermal throttling.
