#!/usr/bin/env bash
# Build fasila-ffi for Apple platforms and package it for the Xcode project:
#
#   apps/apple/Generated/FasilaFFI.xcframework   static libs + C header + module map
#   apps/apple/Generated/fasila_ffi.swift        UniFFI Swift bindings
#
# Slices: iOS device (arm64), iOS simulator (arm64 + x86_64), macOS (arm64 + x86_64).
# Usage: scripts/build-apple.sh [--debug]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/apps/apple/Generated"
CRATE=fasila-ffi
LIB=libfasila_ffi.a
MODULE=fasila_ffiFFI

PROFILE=release
CARGO_PROFILE_FLAG=(--release)
if [[ "${1:-}" == "--debug" ]]; then
  PROFILE=debug
  CARGO_PROFILE_FLAG=()
fi

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "error: building Apple slices requires macOS with Xcode installed" >&2
  exit 1
fi
for tool in cargo rustup xcodebuild lipo; do
  command -v "$tool" >/dev/null || { echo "error: '$tool' not found" >&2; exit 1; }
done

export IPHONEOS_DEPLOYMENT_TARGET=17.0
export MACOSX_DEPLOYMENT_TARGET=14.0

TARGETS=(
  aarch64-apple-ios
  aarch64-apple-ios-sim
  x86_64-apple-ios
  aarch64-apple-darwin
  x86_64-apple-darwin
)

echo "==> Ensuring Rust targets are installed"
installed="$(rustup target list --installed)"
for t in "${TARGETS[@]}"; do
  grep -qx "$t" <<<"$installed" || rustup target add "$t"
done

cd "$ROOT"
for t in "${TARGETS[@]}"; do
  echo "==> cargo build -p $CRATE --target $t ($PROFILE)"
  cargo build -p "$CRATE" --lib --target "$t" ${CARGO_PROFILE_FLAG[@]+"${CARGO_PROFILE_FLAG[@]}"}
done

TARGET_DIR="$(cargo metadata --format-version 1 --no-deps | python3 -c 'import json,sys; print(json.load(sys.stdin)["target_directory"])')"
lib() { echo "$TARGET_DIR/$1/$PROFILE/$LIB"; }

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/ios" "$WORK/ios-sim" "$WORK/macos" "$WORK/bindings" "$WORK/headers"

echo "==> Creating universal libraries"
cp "$(lib aarch64-apple-ios)" "$WORK/ios/$LIB"
lipo -create "$(lib aarch64-apple-ios-sim)" "$(lib x86_64-apple-ios)" -output "$WORK/ios-sim/$LIB"
lipo -create "$(lib aarch64-apple-darwin)" "$(lib x86_64-apple-darwin)" -output "$WORK/macos/$LIB"

echo "==> Generating Swift bindings"
case "$(uname -m)" in
  arm64) HOST_TARGET=aarch64-apple-darwin ;;
  *) HOST_TARGET=x86_64-apple-darwin ;;
esac
# Library mode reads the UniFFI metadata embedded in the compiled library.
cargo run -q -p "$CRATE" --bin uniffi-bindgen -- generate \
  --library "$TARGET_DIR/$HOST_TARGET/$PROFILE/libfasila_ffi.dylib" \
  --language swift --out-dir "$WORK/bindings"

cp "$WORK/bindings/$MODULE.h" "$WORK/headers/"
# Xcode finds the Clang module via a file literally named module.modulemap.
cp "$WORK/bindings/$MODULE.modulemap" "$WORK/headers/module.modulemap"

echo "==> Creating FasilaFFI.xcframework"
mkdir -p "$OUT"
rm -rf "$OUT/FasilaFFI.xcframework"
xcodebuild -create-xcframework \
  -library "$WORK/ios/$LIB" -headers "$WORK/headers" \
  -library "$WORK/ios-sim/$LIB" -headers "$WORK/headers" \
  -library "$WORK/macos/$LIB" -headers "$WORK/headers" \
  -output "$OUT/FasilaFFI.xcframework"

cp "$WORK/bindings/fasila_ffi.swift" "$OUT/fasila_ffi.swift"

echo "==> Done"
echo "    $OUT/FasilaFFI.xcframework"
echo "    $OUT/fasila_ffi.swift"
echo "Next: cd apps/apple && xcodegen generate && open Fasila.xcodeproj"
