#!/usr/bin/env bash
set -euo pipefail

command -v shellcheck >/dev/null 2>&1 || {
  echo 'Install ShellCheck so embedded workflow shell scripts are checked.' >&2
  exit 1
}

# CI runs this on Linux x64; use an installed actionlint on other hosts.
lint_dir="$(mktemp -d)"
trap 'rm -rf "$lint_dir"' EXIT
if command -v actionlint >/dev/null 2>&1; then
  lint=actionlint
else
  [[ "$(uname -sm)" == 'Linux x86_64' ]] || {
    echo 'Install actionlint 1.7.12 on this platform first.' >&2
    exit 1
  }
  archive=actionlint_1.7.12_linux_amd64.tar.gz
  curl --fail --location --silent --show-error --retry 3 \
    "https://github.com/rhysd/actionlint/releases/download/v1.7.12/$archive" -o "$lint_dir/$archive"
  echo "8aca8db96f1b94770f1b0d72b6dddcb1ebb8123cb3712530b08cc387b349a3d8  $lint_dir/$archive" | sha256sum --check
  tar -xzf "$lint_dir/$archive" -C "$lint_dir" actionlint
  lint="$lint_dir/actionlint"
fi

for workflow in .github/workflows/*.yml; do
  echo "Checking $workflow"
  # actionlint 1.7.12 does not recognize GitHub's queue: max yet. Preserve line numbers
  # and check every other field; other queue values still fail validation.
  sed 's/^  queue: max$/  # queue: max/' "$workflow" | "$lint" -color -
done

shellcheck scripts/ci/*.sh scripts/release/*.sh
