#!/usr/bin/env bash
set -euo pipefail

pr="$(gh api "repos/$REPOSITORY/pulls/$PR_NUMBER")"
current=false
if [[ "$(jq -r .state <<< "$pr")" == open && "$(jq -r .head.sha <<< "$pr")" == "$REVIEWED_SHA" && \
      "$(jq -r .base.sha <<< "$pr")" == "$BASE_SHA" && "$(jq -r .base.ref <<< "$pr")" == "$BASE_BRANCH" ]]; then
  current=true
fi
post_status() {
  gh api --method POST "repos/$REPOSITORY/statuses/$REVIEWED_SHA" \
    -f "state=$1" -f "context=$2" -f "description=$3" \
    -f "target_url=$GITHUB_SERVER_URL/$REPOSITORY/actions/runs/$GITHUB_RUN_ID"
}
linux_state=failure
description="Linux ($LINUX_RESULT), site and scripts ($SITE_RESULT)"
if [[ "$SKIP_CHECKS" == true ]]; then description='Owner-approved documentation-only CI skip'; fi
if [[ "$current" == true && ( ( "$SITE_RESULT" == success && ( "$LINUX_RESULT" == success || ( "$SITE_ONLY" == true && "$LINUX_RESULT" == skipped ) ) ) || "$SKIP_CHECKS" == true ) ]]; then
  linux_state=success
fi
post_status "$linux_state" 'Azkar CI / Linux' "$description"
if [[ "$INSTALLERS_REQUIRED" == true ]]; then
  state=failure
  if [[ "$linux_state" == success && "$CROSS_RESULT" == success && "$INSTALLERS_RESULT" == success ]]; then state=success; fi
  post_status "$state" 'Azkar CI / Installers' "Linux, site and scripts ($linux_state), native builds ($CROSS_RESULT), asset validation ($INSTALLERS_RESULT)"
fi
{
  echo '### Reviewed PR checks'
  echo "- PR: #$PR_NUMBER"
  echo "- Reviewed head: $REVIEWED_SHA"
  echo "- Reviewed base: $BASE_BRANCH ($BASE_SHA)"
  echo "- PR still matches the reviewed revision: $current"
  echo "- Documentation-only skip: $SKIP_CHECKS"
  echo "- Linux: $LINUX_RESULT"
  echo "- Site and release scripts: $SITE_RESULT"
  echo "- Native builds: $CROSS_RESULT"
  echo "- Installer validation: $INSTALLERS_RESULT"
} >> "$GITHUB_STEP_SUMMARY"
