# SQ1 Android authentication implementation

The Android authentication helper now contains the exact client-side SQ1 proof operation:

1. Verify the QUIC engine's peer X.509 certificate is currently valid and its SHA-256 DER fingerprint matches the saved host pin.
2. Send the one-line JSON hello on bidirectional stream 0.
3. Decode the host's base64 32-byte challenge.
4. Sign the exact byte sequence `Syzygy QUIC client authentication v1\\0` followed by the challenge using SHA256withRSA and the paired client private key.
5. Send the exact PEM client certificate and base64 signature as one JSON line.
6. Do not open forwarding channels until the exact `{"authorized":true}` response is received.

The helper is deliberately transport-agnostic. The app still needs a QUIC v1 engine exposing bidirectional streams, DATAGRAM frames, peer-certificate access, and cancellation; it also needs to load the existing paired identity without exporting private-key material unnecessarily. No UI toggle or fallback behavior is exposed until that engine and the local socket tunnel are implemented.
