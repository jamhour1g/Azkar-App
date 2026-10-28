#!/usr/bin/env bash
# Compatibility entrypoint for the trusted dev workflow until it adopts scripts/tests/.
set -euo pipefail
exec kotlinc -script "$(dirname "${BASH_SOURCE[0]}")/tests/test_ci_authorization.main.kts" "$@"
