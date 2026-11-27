#!/usr/bin/env bash
set -euo pipefail
scripts="$(cd "$(dirname "$0")/../release" && pwd)"
root="$(mktemp -d)"
trap 'rm -rf "$root"' EXIT
cd "$root"
export RUNNER_TEMP="$root" GITHUB_REPOSITORY=example/app GITHUB_SHA=abc GITHUB_SERVER_URL=https://github.com
export GITHUB_OUTPUT="$root/out" GITHUB_STEP_SUMMARY="$root/summary"
mkdir site
echo sample > site/index.html
touch site/.nojekyll
(cd site && sha256sum ./index.html) > site-SHA256SUMS
tar -cf artifact.tar -C site ./index.html
"$scripts/repository-site.sh" verify-archive
tar -rf artifact.tar -C site ./.nojekyll
if "$scripts/repository-site.sh" verify-archive; then exit 1; fi
mkdir site/nested
touch site/nested/.missing
(cd site && sha256sum ./nested/.missing) >> site-SHA256SUMS
tar -cf artifact.tar -C site ./index.html
if "$scripts/repository-site.sh" verify-archive; then exit 1; fi

gh() {
  case "$*" in
    *workflows/release.yml/runs*) printf '123\t%s\n' "$ATTEMPT" ;;
    */123/jobs*) jq -nc --arg name "$JOB" '[{name:$name,conclusion:"failure"}]' ;;
    */123/artifacts*) jq -nc --argjson expired "$EXPIRED" '{artifacts:[{name:"repository-site-checksums",expired:$expired}]}' ;;
    *) return 1 ;;
  esac
}
export -f gh
export ATTEMPT=1 EXPIRED=false JOB='Verify live repositories and publish release'
"$scripts/recover-finalize.sh"
grep -qx 'retry_run=123' out
for scenario in repeated expired packaging; do
  : > out
  case "$scenario" in
    repeated) ATTEMPT=2 ;;
    expired) ATTEMPT=1; EXPIRED=true ;;
    packaging) JOB='Package (linux-x64)' ;;
  esac
  "$scripts/recover-finalize.sh"
  if [[ "$scenario" == packaging ]]; then test ! -s out; else test "$(cat out)" = handled=true; fi
done
