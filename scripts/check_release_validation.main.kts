#!/usr/bin/env bash
# Compatibility entrypoint for the trusted dev workflow until it adopts scripts/release/.
set -euo pipefail
exec kotlinc -script "$(dirname "${BASH_SOURCE[0]}")/release/check_release_validation.main.kts" "$@"
