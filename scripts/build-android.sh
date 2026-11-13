#!/usr/bin/env bash
# Build the Rust core for Android and generate the Kotlin (UniFFI) bindings.
#
#   scripts/build-android.sh            # release .so files
#   PROFILE=debug scripts/build-android.sh
#
# Requirements: rustup, cargo-ndk (`cargo install cargo-ndk`), and an Android NDK
# (ANDROID_NDK_HOME, or ANDROID_HOME/ndk/<version> which cargo-ndk discovers).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP="$ROOT/apps/android/app"
JNI_DIR="$APP/src/main/jniLibs"
BINDINGS_DIR="$APP/src/generated/uniffi"
PROFILE="${PROFILE:-release}"
ABIS=(arm64-v8a armeabi-v7a x86_64)
TARGETS=(aarch64-linux-android armv7-linux-androideabi x86_64-linux-android)
API_LEVEL=26

cd "$ROOT"

command -v cargo-ndk >/dev/null 2>&1 || {
  echo "cargo-ndk not found: cargo install cargo-ndk" >&2
  exit 1
}

rustup target add "${TARGETS[@]}" >/dev/null

profile_flag=()
[[ "$PROFILE" == "release" ]] && profile_flag=(--release)

ndk_args=()
for abi in "${ABIS[@]}"; do ndk_args+=(-t "$abi"); done

echo "==> Building libfasila_ffi.so (${ABIS[*]}, $PROFILE)"
rm -rf "$JNI_DIR"
cargo ndk "${ndk_args[@]}" --platform "$API_LEVEL" -o "$JNI_DIR" \
  build -p fasila-ffi --lib "${profile_flag[@]}"

# Bindings are generated from a host build of the same crate (the metadata is
# identical across targets) so this works without running anything on-device.
echo "==> Generating Kotlin bindings"
cargo build -p fasila-ffi --lib
case "$(uname -s)" in
  Darwin) HOST_LIB="$ROOT/target/debug/libfasila_ffi.dylib" ;;
  *) HOST_LIB="$ROOT/target/debug/libfasila_ffi.so" ;;
esac
rm -rf "$BINDINGS_DIR"
mkdir -p "$BINDINGS_DIR"
cargo run -q -p fasila-ffi --bin uniffi-bindgen -- generate \
  --library "$HOST_LIB" --language kotlin --no-format --out-dir "$BINDINGS_DIR"

echo "==> Done"
find "$JNI_DIR" -name '*.so' -exec ls -lh {} \;
find "$BINDINGS_DIR" -name '*.kt'
