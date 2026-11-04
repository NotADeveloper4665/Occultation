#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT="$ROOT/app/build/generated/jniLibs"

command -v cargo >/dev/null 2>&1 || { echo "Rust cargo is required to build the Syzygy QUIC transport" >&2; exit 1; }
command -v cargo-ndk >/dev/null 2>&1 || { echo "Install cargo-ndk (cargo install cargo-ndk) to build the Syzygy QUIC transport" >&2; exit 1; }

if [ -z "${ANDROID_NDK_HOME:-}" ] && [ -n "${ANDROID_HOME:-}" ]; then
  export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/27.0.12077973"
fi

mkdir -p "$OUT"
cargo ndk \
  -t arm64-v8a \
  -t armeabi-v7a \
  -t x86 \
  -t x86_64 \
  -p 21 \
  -o "$OUT" \
  -- build --release --manifest-path "$ROOT/quic-transport/Cargo.toml"
