#!/usr/bin/env bash
set -euo pipefail
# NDK header directory; tests execute against a host fake driver, never phone hardware.
headers="${1:?Pass NDK sysroot/usr/include}"
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT
mkdir -p "$out/include/android"
cp "$headers/android/NeuralNetworks.h" "$headers/android/NeuralNetworksTypes.h" "$out/include/android/"
flags=(-std=c++17 -Wall -Wextra -Werror -DNN_COMPATIBILITY_LIBRARY_BUILD '-D__INTRODUCED_IN(x)=' '-D__DEPRECATED_IN(...)=' '-D__BIONIC_AVAILABILITY_GUARD(x)=1' -Wno-deprecated-declarations -I"$out/include" -Iapp/src/main/jni)
g++ "${flags[@]}" -shared -fPIC tests/neural/native/fake_nnapi.cpp -o "$out/libneuralnetworks.so"
g++ "${flags[@]}" app/src/main/jni/nnapi_runtime.cpp tests/neural/native/runtime_test.cpp -ldl -o "$out/runtime-test"
LD_LIBRARY_PATH="$out" "$out/runtime-test"
LD_LIBRARY_PATH= "$out/runtime-test" unavailable
