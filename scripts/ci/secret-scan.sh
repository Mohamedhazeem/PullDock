#!/usr/bin/env bash
# Lightweight secret-pattern scan over tracked files (task T017). Deliberately
# conservative: it fails only on high-confidence secret shapes to avoid a noisy gate.
# Replace/supplement with a dedicated scanner when adopted in Slice 7 (T176).
set -euo pipefail

cd "$(dirname "$0")/../.."

PATTERN='BEGIN (RSA |EC |DSA |OPENSSH )?PRIVATE KEY|AKIA[0-9A-Z]{16}|ASIA[0-9A-Z]{16}|ghp_[A-Za-z0-9]{36}|github_pat_[A-Za-z0-9_]{22,}|glpat-[A-Za-z0-9_-]{20}|xox[baprs]-[A-Za-z0-9-]{10,}|sk-ant-[A-Za-z0-9_-]{20,}|sk-proj-[A-Za-z0-9_-]{20,}'

MATCHES=$(git ls-files -z -- . ':(exclude)scripts/ci/secret-scan.sh' \
    | xargs -0 -r grep -nIlE "$PATTERN" -- 2>/dev/null || true)

if [ -n "$MATCHES" ]; then
    echo "FAIL: potential secrets matched the pattern scan:" >&2
    echo "$MATCHES" >&2
    exit 1
fi

echo "OK: no secret patterns detected in tracked files."
