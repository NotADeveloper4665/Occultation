# SQ1 Android QUIC transport

The Android client now has an end-to-end SQ1 transport implementation in this branch.

## Connection and authentication

- Uses the Rust Quinn QUIC v1 engine through JNI, with ALPN `syzygy-quic-1`.
- The Rust TLS verifier checks the exact SHA-256 DER fingerprint of the host certificate before the handshake is accepted. Java also checks the saved X.509 certificate validity.
- Authentication uses the existing paired identity supplied by `LimelightCryptoProvider`; the private key signs the 32-byte host challenge with RSA PKCS#1 v1.5 / SHA-256.
- The client does not open forwarding channels until the host returns the exact SQ1 authorization object.
- QUIC uses TLS 1.3 and negotiates the DATAGRAM extension. The client does not disable TLS certificate verification globally; it uses a dedicated pinned-certificate verifier for this connection.

## Loopback forwarding

When the opt-in `checkbox_syzygy_quic` preference is enabled, the app connects to the host relay on UDP port 48020, authenticates, then starts local loopback proxies for Moonlight-core:

| Local port | Protocol | SQ1 channel |
|---|---|---|
| 47984 | TCP | Reliable QUIC stream |
| 48010 | TCP | Reliable QUIC stream |
| 47998 | UDP | QUIC DATAGRAM frames |
| 47999 | UDP | Length-prefixed packets on the reliable control stream |
| 48000 | UDP | QUIC DATAGRAM frames |

The existing Moonlight-core connection is pointed at loopback and its RTSP session URL authority is rewritten without changing the path or query. The existing HTTP discovery and pairing requests still use the real host address.

Media datagrams use the existing bounded SQ1 fragment codec. TCP streams are half-closed on EOF and their native stream handles are released after both directions finish.

## Fallback and limitations

- The setting is off by default.
- If QUIC connection, certificate validation, SQ1 authorization, or local port binding fails before Moonlight-core starts, the app reports the failure and uses the existing transport.
- There is no mid-stream transport switch. A QUIC connection lost after streaming starts ends that stream; the app does not silently mix QUIC and legacy sockets.
- The preference is currently global rather than per-host.
- The host must run the Syzygy SQ1 relay on UDP 48020 and use the same certificate identity as the paired host.
- Android ABI packaging and host interoperability must pass CI and physical-device testing before this is considered production-ready.
