#!/usr/bin/env bash
set -euo pipefail

log="$(mktemp)"
trap 'rm -f "$log"' EXIT

if "$@" 2>&1 | tee "$log"; then
  exit 0
else
  status=$?
fi

if ! grep -Ei -B 1 'status code 429' "$log" | grep -Ei 'https://repo\.maven\.apache\.org/maven2/|https://repo1\.maven\.org/maven2/' >/dev/null; then
  exit "$status"
fi

echo 'Maven Central returned HTTP 429. Retrying in 60 seconds.'
sleep 60
"$@"
