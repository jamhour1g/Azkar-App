#!/usr/bin/env bash
set -euo pipefail

validate_release() {
  : "${RELEASE_TAG:?RELEASE_TAG is required}" "${PRE_RELEASE:?PRE_RELEASE is required}"
  case "$PRE_RELEASE" in
    true)
      [[ "$RELEASE_TAG" =~ ^v[0-9]+\.[0-9]+\.[0-9]+-beta\.[0-9]+(\.[0-9]+)*$ ]] || {
        echo 'Dev releases require a versioned Beta tag.' >&2; return 1;
      } ;;
    false)
      [[ "$RELEASE_TAG" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || {
        echo 'Stable releases require a vMAJOR.MINOR.PATCH tag.' >&2; return 1;
      } ;;
    *) echo 'PRE_RELEASE must be true or false.' >&2; return 1 ;;
  esac
}

prepare_draft() {
  validate_release
  : "${APP_VERSION:?APP_VERSION is required}" "${RELEASE_CHANNEL:?RELEASE_CHANNEL is required}"
  [[ "$RELEASE_TAG" == "v$APP_VERSION" ]] || { echo 'Release tag and app version must match.' >&2; return 1; }
  [[ ( "$RELEASE_CHANNEL" == dev && "$PRE_RELEASE" == true ) || ( "$RELEASE_CHANNEL" == stable && "$PRE_RELEASE" == false ) ]] || {
    echo 'Release channel and prerelease flag must match.' >&2; return 1;
  }
  local notes_file previous
  local -a note_args release_args
  notes_file="$RUNNER_TEMP/release-notes.md"
  if [[ "$RELEASE_CHANNEL" == stable ]]; then
    gh api --method POST "repos/${GITHUB_REPOSITORY}/releases/generate-notes" \
      -f "tag_name=$RELEASE_TAG" -f "target_commitish=$GITHUB_SHA" --jq .body > "$notes_file"
  else
    previous="$("$(dirname "${BASH_SOURCE[0]}")/metadata.main.kts" latest-dev-tag)"
    note_args=()
    if [[ -n "$previous" ]]; then note_args+=(-f "previous_tag_name=$previous"); fi
    gh api --method POST "repos/${GITHUB_REPOSITORY}/releases/generate-notes" \
      -f "tag_name=$RELEASE_TAG" -f "target_commitish=$GITHUB_SHA" "${note_args[@]}" --jq .body > "$notes_file"
  fi
  if gh release view "$RELEASE_TAG" >/dev/null 2>&1; then
    test "$(gh release view "$RELEASE_TAG" --json isDraft --jq .isDraft)" == true || {
      echo 'Refusing to replace an already published release. Use a new version.'
      exit 1
    }
    gh release upload "$RELEASE_TAG" release-assets/* --clobber
    gh release edit "$RELEASE_TAG" --title "Azkar $APP_VERSION" --notes-file "$notes_file" --prerelease="$PRE_RELEASE"
  else
    release_args=()
    if [[ "$PRE_RELEASE" == true ]]; then release_args+=(--prerelease); fi
    gh release create "$RELEASE_TAG" release-assets/* --draft --title "Azkar $APP_VERSION" \
      --notes-file "$notes_file" --target "$GITHUB_SHA" "${release_args[@]}"
  fi
}

publish_release() {
  validate_release
  local latest=false
  if [[ "$PRE_RELEASE" == false ]]; then latest=true; fi
  gh release edit "$RELEASE_TAG" --draft=false --prerelease="$PRE_RELEASE" --latest="$latest"
}

case "${1:-}" in
  prepare-draft) prepare_draft ;;
  publish) publish_release ;;
  *) echo "Usage: $0 {prepare-draft|publish}" >&2; exit 2 ;;
esac
