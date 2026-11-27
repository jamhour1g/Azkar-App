#!/usr/bin/env bash
set -euo pipefail

trap 'echo "::error::Authorization failed at line $LINENO. Check the PR state, reviewed SHA, owner, and dispatch branch."' ERR
[[ "$PR_NUMBER" =~ ^[1-9][0-9]*$ ]]
[[ "$REVIEWED_SHA" =~ ^[0-9a-f]{40}$ ]]
if [[ "$EVENT" == workflow_dispatch ]]; then
  test "$ACTOR" = "$OWNER"
  default_branch="$(gh api "repos/$REPOSITORY" --jq .default_branch)"
fi
pr="$(gh api "repos/$REPOSITORY/pulls/$PR_NUMBER")"
test "$(jq -r .state <<< "$pr")" = open
test "$(jq -r .head.sha <<< "$pr")" = "$REVIEWED_SHA"
test "$(jq -r .base.repo.full_name <<< "$pr")" = "$REPOSITORY"
if [[ "$EVENT" == workflow_dispatch && "$DISPATCH_REF" != "refs/heads/$default_branch" ]]; then
  # The owner can rehearse their own workflow changes before landing on dev.
  test "$(jq -r .user.login <<< "$pr")" = "$OWNER"
  test "$(jq -r .head.repo.full_name <<< "$pr")" = "$REPOSITORY"
  test "$DISPATCH_REF" = "refs/heads/$(jq -r .head.ref <<< "$pr")"
  test "$GITHUB_SHA" = "$REVIEWED_SHA"
fi
if [[ "$EVENT" == pull_request_target ]]; then
  test "$(jq -r .user.login <<< "$pr")" = "$OWNER"
fi
base="$(jq -r .base.ref <<< "$pr")"
[[ "$base" == dev || "$base" == production ]]
installers=false
if [[ "$base" == production || "$FULL_INSTALLERS" == true ]]; then installers=true; fi
skip_checks=false
site_only=false
if [[ "$base" == dev && "$installers" == false && ( "$PROFILE_GRADLE" != true || "${SKIP_CHECKS:-false}" == true ) ]]; then
  count="$(jq -r .changed_files <<< "$pr")"
  if (( count > 0 && count <= 3000 )); then
    files_json="$(gh api --paginate --slurp "repos/$REPOSITORY/pulls/$PR_NUMBER/files?per_page=100")"
    if jq -e --argjson count "$count" '[.[][]] | length == $count and
      all(.[]; all((.filename, (.previous_filename // empty)); startswith("site/src/") or startswith("site/tests/")))' \
      <<< "$files_json" >/dev/null; then
      site_only=true
    fi
  fi
fi
if [[ "${SKIP_CHECKS:-false}" == true ]]; then
  test "$EVENT" = workflow_dispatch
  test "$DISPATCH_REF" = "refs/heads/$default_branch"
  test "$base" = dev
  test "$installers" = false
  jq -e --argjson count "$count" '[.[][]] | length > 0 and length == $count and
    all(.[]; all((.filename, (.previous_filename // empty)); . == "README.md" or
      . == "data/src/main/resources/db/seed/SEED_DATA_NOTICE.md" or test("^docs/.*\\.md\\z")))' \
    <<< "${files_json:-[]}" >/dev/null
  skip_checks=true
fi
{
  echo "installers=$installers"
  echo "site_only=$site_only"
  echo "skip_checks=$skip_checks"
  echo "pr_number=$PR_NUMBER"
  echo "head_sha=$REVIEWED_SHA"
  echo "base_sha=$(jq -r .base.sha <<< "$pr")"
  echo "base_branch=$base"
} >> "$GITHUB_OUTPUT"
contexts=('Azkar CI / Linux')
if [[ "$installers" == true ]]; then contexts+=('Azkar CI / Installers'); fi
for context in "${contexts[@]}"; do
  description='Reviewed revision queued for CI'
  if [[ "$skip_checks" == true ]]; then description='Owner-approved documentation-only skip queued'; fi
  gh api --method POST "repos/$REPOSITORY/statuses/$REVIEWED_SHA" \
    -f state=pending -f "context=$context" -f "description=$description" \
    -f "target_url=$GITHUB_SERVER_URL/$REPOSITORY/actions/runs/$GITHUB_RUN_ID"
done
