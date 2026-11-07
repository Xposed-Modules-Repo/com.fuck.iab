#!/usr/bin/env bash

set -euo pipefail

die() { echo "error: $*" >&2; exit 1; }

[ $# -eq 2 ] || die "usage: $0 <frida-gum-source-dir> <output-dir>"

SRC_IN="$1"
OUT_IN="$2"
CALLER_PWD="$PWD"

[ -d "$SRC_IN" ] || die "source dir not found: $SRC_IN"
[ -f "$SRC_IN/meson.build" ] && [ -d "$SRC_IN/releng" ] || \
  die "$SRC_IN does not look like a frida-gum checkout"

SRC="$(cd "$SRC_IN" && pwd)"
mkdir -p "$OUT_IN"
OUT="$(cd "$OUT_IN" && pwd)"

# --- Android NDK ------------------------------------------------------------
# GUM_NDK_REQUIRED picks the revision, r29 by default. Upstream releng only accepts
# the revision it was pinned to, so the checkout is patched to the one we build with.
GUM_NDK_REQUIRED="${GUM_NDK_REQUIRED:-29}"
if [ -z "${ANDROID_NDK_ROOT:-}" ]; then
  for cand in "$HOME/android/android-ndk-r$GUM_NDK_REQUIRED" \
              "$HOME/Android/Sdk/ndk/$GUM_NDK_REQUIRED.0.0"; do
    if [ -f "$cand/source.properties" ]; then
      export ANDROID_NDK_ROOT="$cand"
      break
    fi
  done
fi
[ -n "${ANDROID_NDK_ROOT:-}" ] || \
  die "ANDROID_NDK_ROOT is not set and no r$GUM_NDK_REQUIRED NDK found in ~/android"
[ -f "$ANDROID_NDK_ROOT/source.properties" ] || \
  die "ANDROID_NDK_ROOT=$ANDROID_NDK_ROOT has no source.properties"
grep -q "Pkg.Revision = $GUM_NDK_REQUIRED" "$ANDROID_NDK_ROOT/source.properties" || \
  die "NDK r$GUM_NDK_REQUIRED required (found: $(grep Pkg.Revision "$ANDROID_NDK_ROOT/source.properties"))"
export ANDROID_NDK_ROOT
echo "NDK: $ANDROID_NDK_ROOT"

RELENG_ENV="$SRC/releng/env_android.py"
if [ -f "$RELENG_ENV" ]; then
  sed -i -E "s/^NDK_REQUIRED = [0-9]+$/NDK_REQUIRED = $GUM_NDK_REQUIRED/" "$RELENG_ENV"
  echo "releng NDK pin: $(grep -m1 '^NDK_REQUIRED' "$RELENG_ENV")"
fi

# --- Host tools ----------------------------------------------------------
missing=()
for t in python3 make git curl tar; do
  command -v "$t" >/dev/null 2>&1 || missing+=("$t")
done
if [ ${#missing[@]} -gt 0 ]; then
  echo "installing missing tools: ${missing[*]}"
  pkgs=()
  for t in "${missing[@]}"; do
    case "$t" in
      python3) pkgs+=(python3) ;;
      make)    pkgs+=(make) ;;
      git)     pkgs+=(git) ;;
      curl)    pkgs+=(curl) ;;
      tar)     pkgs+=(tar) ;;
    esac
  done
  if command -v apt-get >/dev/null 2>&1; then
    sudo apt-get update
    sudo apt-get install -y "${pkgs[@]}"
  else
    die "missing tools: ${missing[*]} (no apt-get available)"
  fi
fi

# --- Minimal feature set: GumJS with QuickJS, nothing else ---------------
CONFIGURE_FLAGS=(
  --enable-gumjs           # JavaScript bindings (default: disabled)
  --with-devkits=gumjs     # emit frida-gumjs.h + libfrida-gumjs.a
  --disable-v8             # no V8 backend (QuickJS only)
  --disable-inspector      # no JS inspector server
  --disable-database       # no Database API (sqlite)
  --disable-tests          # no test programs
  --disable-graft-tool     # no gum-graft utility
  --enable-diet            # minimal-footprint build
)

# frida machine spec -> Android ABI directory name
abi_for() {
  case "$1" in
    android-arm64) echo arm64-v8a ;;
    android-arm)   echo armeabi-v7a ;;
    android-x86_64) echo x86_64 ;;
    android-x86)   echo x86 ;;
    *) die "unknown machine: $1" ;;
  esac
}

# Only the ABIs the module ships: arm64-v8a and armeabi-v7a.
MACHINES=(android-arm64 android-arm)

LIBS_DIR="$OUT/app/src/main/libs"
mkdir -p "$LIBS_DIR"

for machine in "${MACHINES[@]}"; do
  abi="$(abi_for "$machine")"
  bd="$OUT/.build/$machine"

  echo ""
  echo "=== [$machine -> $abi] configure ==="
  if [ ! -f "$bd/build.ninja" ]; then
    rm -rf "$bd"
    mkdir -p "$bd"
    (
      cd "$bd"
      python3 -c "
import sys
sys.path.insert(0, sys.argv[1])
from releng.meson_configure import main
main()
" "$SRC" --host="$machine" "${CONFIGURE_FLAGS[@]}"
    )
  else
    echo "already configured, reusing $bd"
  fi

  echo "=== [$machine -> $abi] build ==="
  make -C "$bd"

  devkit="$bd/bindings/gumjs/devkit"
  [ -f "$devkit/libfrida-gumjs.a" ] || die "devkit lib missing for $machine"
  [ -f "$devkit/frida-gumjs.h" ]    || die "devkit header missing for $machine"

  mkdir -p "$LIBS_DIR/$abi/gum"
  cp "$devkit/libfrida-gumjs.a" "$LIBS_DIR/$abi/libfrida-gumjs.a"
  cp "$devkit/frida-gumjs.h"    "$LIBS_DIR/$abi/frida-gumjs.h"
  # frida-gumjs.h contains arch-dependent typedefs (LP64 vs ILP32), so each
  # ABI keeps its own copy next to its library rather than a shared one.

  glib_object_h="$(find "$SRC" -path '*/glib/glib-object.h' -print -quit)"
  [ -n "$glib_object_h" ] || glib_object_h="$(find "$SRC" -name 'glib-object.h' -print -quit)"
  [ -n "$glib_object_h" ] || die "glib-object.h not found under $SRC (is the glib subproject fetched?)"
  cp "$glib_object_h" "$LIBS_DIR/$abi/glib-object.h"

  gumenumtypes_h="$(find "$bd" -name 'gumenumtypes.h' -print -quit)"
  [ -n "$gumenumtypes_h" ] || die "gumenumtypes.h not found in $bd"
  cp "$gumenumtypes_h" "$LIBS_DIR/$abi/gum/gumenumtypes.h"

  echo "[$abi] $(du -h "$LIBS_DIR/$abi/libfrida-gumjs.a" | cut -f1) libfrida-gumjs.a"
done

echo ""
echo "=== done ==="
find "$LIBS_DIR" -name '*.a' -o -name '*.h' | sort
