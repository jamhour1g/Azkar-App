#!/usr/bin/env bash
set -euo pipefail

if (( $# == 0 )); then
  echo "Usage: $0 SCRIPT.main.kts [arguments...]" >&2
  exit 2
fi

compiler="$(command -v kotlinc)" || { echo 'Install the Kotlin compiler first.' >&2; exit 1; }
compiler="$(realpath "$compiler")"
plugin="$(dirname "$compiler")/../lib/kotlinx-serialization-compiler-plugin.jar"
[[ -r "$plugin" ]] || { echo 'The Kotlin distribution must include its serialization compiler plugin.' >&2; exit 1; }

exec "$compiler" "-Xplugin=$plugin" -script "$@"
