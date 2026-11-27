#!/usr/bin/env bash
set -euo pipefail

log="$(mktemp)"
trap 'rm -f "$log"' EXIT

for delay in 60 120 240; do
  if "$@" 2>&1 | tee "$log"; then
    exit 0
  else
    status=$?
  fi

  if ! grep -Ei -B 1 'status code 429' "$log" | grep -Ei 'https://repo\.maven\.apache\.org/maven2/|https://repo1\.maven\.org/maven2/' >/dev/null; then
    exit "$status"
  fi

  echo "Maven Central returned HTTP 429. Retrying in $delay seconds."
  sleep "$delay"
done
"$@"
