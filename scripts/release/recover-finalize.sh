#!/usr/bin/env bash
set -euo pipefail

api="repos/$GITHUB_REPOSITORY/actions"
# Inspect the latest real failure for this scheduled revision. Successful no-op
# schedules can intervene while a failed finalization waits for investigation.
run="$(gh api "$api/workflows/release.yml/runs?branch=dev&event=schedule&head_sha=$GITHUB_SHA&status=failure&per_page=1" --jq '.workflow_runs[0] | select(. != null) | [.id,.run_attempt] | @tsv')"
if [[ -z "$run" ]]; then exit 0; fi
read -r id attempt <<< "$run"
jobs="$(gh api --paginate "$api/runs/$id/jobs?per_page=100")"
if ! jq -es '[.[][] | select(.conclusion == "failure") | .name] == ["Verify live repositories and publish release"]' <<< "$jobs" >/dev/null; then
  exit 0
fi
url="$GITHUB_SERVER_URL/$GITHUB_REPOSITORY/actions/runs/$id"
artifacts="$(gh api --paginate "$api/runs/$id/artifacts?per_page=100")"
if [[ "$attempt" == 1 ]] && jq -es 'any(.[].artifacts[]; .name == "repository-site-checksums" and .expired == false)' <<< "$artifacts" >/dev/null; then
  echo "retry_run=$id" >> "$GITHUB_OUTPUT"
  echo "Selected finalization retry on $url." >> "$GITHUB_STEP_SUMMARY"
else
  echo "Release held for $GITHUB_SHA after finalization failed on $url. Inspect the failed step, then dispatch Release manually to rebuild." >> "$GITHUB_STEP_SUMMARY"
fi
echo 'handled=true' >> "$GITHUB_OUTPUT"
