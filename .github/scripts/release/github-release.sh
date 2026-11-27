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

find_release() {
  # The list endpoint includes drafts for the workflow's write token.
  gh api --paginate "repos/${GITHUB_REPOSITORY:?}/releases?per_page=100" |
    jq -cs --arg tag "$RELEASE_TAG" '
      [.[][] | select(.tag_name == $tag) | {id, draft}] |
      if length > 1 then error("Multiple releases match " + $tag) else .[] end
    '
}

prepare_draft() {
  validate_release
  : "${APP_VERSION:?APP_VERSION is required}" "${RELEASE_CHANNEL:?RELEASE_CHANNEL is required}" "${GITHUB_SHA:?GITHUB_SHA is required}"
  [[ "$RELEASE_TAG" == "v$APP_VERSION" ]] || { echo 'Release tag and app version must match.' >&2; return 1; }
  [[ ( "$RELEASE_CHANNEL" == dev && "$PRE_RELEASE" == true ) || ( "$RELEASE_CHANNEL" == stable && "$PRE_RELEASE" == false ) ]] || {
    echo 'Release channel and prerelease flag must match.' >&2; return 1;
  }
  local notes_file previous tag_ref tag_commit build_commit release
  local -a note_args release_args
  tag_ref="refs/tags/$RELEASE_TAG"
  if git show-ref --verify --quiet "$tag_ref"; then
    tag_commit="$(git rev-parse --verify "$tag_ref^{commit}")"
    build_commit="$(git rev-parse --verify "$GITHUB_SHA^{commit}")"
    if [[ "$tag_commit" != "$build_commit" ]]; then
      echo "Release tag $RELEASE_TAG points to a different commit than the build." >&2
      return 1
    fi
  fi
  release="$(find_release)"
  if [[ -n "$release" ]] && ! jq -e '.draft == true' <<< "$release" >/dev/null; then
    echo 'Refusing to replace an already published release. Use a new version.' >&2
    return 1
  fi
  notes_file="$RUNNER_TEMP/release-notes.md"
  if [[ "$RELEASE_CHANNEL" == stable ]]; then
    gh api --method POST "repos/${GITHUB_REPOSITORY}/releases/generate-notes" \
      -f "tag_name=$RELEASE_TAG" -f "target_commitish=$GITHUB_SHA" --jq .body > "$notes_file"
  else
    previous="$(python3 -B "$(dirname "${BASH_SOURCE[0]}")/metadata.py" latest-dev-tag)"
    note_args=()
    if [[ -n "$previous" ]]; then note_args+=(-f "previous_tag_name=$previous"); fi
    gh api --method POST "repos/${GITHUB_REPOSITORY}/releases/generate-notes" \
      -f "tag_name=$RELEASE_TAG" -f "target_commitish=$GITHUB_SHA" "${note_args[@]}" --jq .body > "$notes_file"
  fi
  if [[ -n "$release" ]]; then
    gh release upload "$RELEASE_TAG" release-assets/* --clobber
    gh release edit "$RELEASE_TAG" --target "$GITHUB_SHA" --title "Azkar $APP_VERSION" \
      --notes-file "$notes_file" --prerelease="$PRE_RELEASE"
  else
    release_args=()
    if [[ "$PRE_RELEASE" == true ]]; then release_args+=(--prerelease); fi
    gh release create "$RELEASE_TAG" release-assets/* --draft --title "Azkar $APP_VERSION" \
      --notes-file "$notes_file" --target "$GITHUB_SHA" "${release_args[@]}"
  fi
}

publish_release() {
  validate_release
  local manifest="${1:?Expected release asset checksum file is required}"
  local release release_id actual latest=false
  [[ -s "$manifest" ]] || { echo "Missing or empty asset checksum file: $manifest" >&2; return 1; }
  release="$(find_release)"
  [[ -n "$release" ]] || { echo "Release $RELEASE_TAG was not found." >&2; return 1; }
  release_id="$(jq -er '.id' <<< "$release")"
  actual="$(gh api --paginate "repos/$GITHUB_REPOSITORY/releases/$release_id/assets?per_page=100" |
    jq -r '.[] |
      if .state == "uploaded" and (.digest | type == "string" and test("^sha256:[0-9a-f]{64}$"))
      then "\(.digest | ltrimstr("sha256:"))  \(.name)"
      else error("Missing SHA-256 digest or incomplete upload: " + .name)
      end' |
    LC_ALL=C sort)"
  if ! diff -u --label 'Expected build assets' --label 'GitHub release assets' \
    <(LC_ALL=C sort -- "$manifest") <(printf '%s\n' "$actual"); then
    echo "Release $RELEASE_TAG assets differ from the build. Check the draft before retrying." >&2
    return 1
  fi
  if [[ "$PRE_RELEASE" == false ]]; then latest=true; fi
  gh release edit "$RELEASE_TAG" --draft=false --prerelease="$PRE_RELEASE" --latest="$latest"
}

case "${1:-}" in
  prepare-draft) prepare_draft ;;
  publish) publish_release "${2:-}" ;;
  *) echo "Usage: $0 prepare-draft | publish <asset-checksums>" >&2; exit 2 ;;
esac
