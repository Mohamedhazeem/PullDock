#!/usr/bin/env bash
# Verifies PullDock's 64-bit-only ABI packaging policy (spec FR-076, task T019).
# Usage: check-apk-abis.sh <apk-file-or-directory>
# Scans every .apk given (or every .apk in the directory) and exits non-zero if any
# 32-bit ABI directory (armeabi-v7a, x86) appears in lib/ of any APK.
set -euo pipefail

TARGET="${1:-}"
if [ -z "$TARGET" ]; then
    echo "FAIL: no APK file or directory given." >&2
    exit 1
fi

if [ -f "$TARGET" ]; then
    APKS="$TARGET"
elif [ -d "$TARGET" ]; then
    APKS=$(find "$TARGET" -maxdepth 1 -name '*.apk' -print)
    if [ -z "$APKS" ]; then
        echo "FAIL: no .apk files found in '$TARGET'." >&2
        exit 1
    fi
else
    echo "FAIL: APK target not found: '$TARGET'" >&2
    exit 1
fi

FAILED=0
for APK in $APKS; do
    # Native library ABI directories (lib/<abi>/...). An APK without native libraries
    # yields an empty list, which passes (nothing 32-bit to find).
    LIBS=$(unzip -Z1 "$APK" | grep '^lib/' | cut -d/ -f2 | sort -u || true)
    if [ -z "$LIBS" ]; then
        echo "OK ($(basename "$APK")): no native libraries packaged yet; nothing violates the 64-bit policy."
        continue
    fi
    echo "Native ABI directories in $(basename "$APK"): $LIBS"
    for forbidden in armeabi-v7a x86; do
        if grep -qx "$forbidden" <<<"$LIBS"; then
            echo "FAIL ($(basename "$APK")): forbidden 32-bit ABI '$forbidden' is packaged (FR-076)." >&2
            FAILED=1
        fi
    done
done

if [ "$FAILED" -ne 0 ]; then
    exit 1
fi

echo "OK: 64-bit-only ABI packaging verified."
