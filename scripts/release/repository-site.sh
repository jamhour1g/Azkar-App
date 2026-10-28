#!/usr/bin/env bash
set -euo pipefail

import_key() {
  local key_id expected_key
  test -n "$REPO_PRIVATE_KEY" || { echo 'Set LINUX_REPO_SIGNING_KEY before publishing.'; exit 1; }
  export GNUPGHOME="$RUNNER_TEMP/azkar-repo-gnupg"
  mkdir -m 700 "$GNUPGHOME"
  printf '%s\n' "$REPO_PRIVATE_KEY" | gpg --batch --import
  key_id="$(gpg --batch --with-colons --list-secret-keys | awk -F: '$1 == "fpr" { print $10; exit }')"
  test -n "$key_id"
  expected_key="$(gpg --batch --with-colons --show-keys assets/keys/azkar-repository.asc | awk -F: '$1 == "fpr" { print $10; exit }')"
  test "$key_id" = "$expected_key" || { echo 'Signing key does not match the pinned repository public key.' >&2; return 1; }
  echo "$key_id" > "$RUNNER_TEMP/azkar-repo-key-id"
  gpg --batch --armor --export "$key_id" > "$RUNNER_TEMP/azkar-repo.asc"
  printf 'signing check\n' > "$RUNNER_TEMP/azkar-signing-check"
  printf '%s\n' "$REPO_PASSPHRASE" | gpg --batch --yes --pinentry-mode loopback --passphrase-fd 0 \
    --local-user "$key_id" --detach-sign --output "$RUNNER_TEMP/azkar-signing-check.sig" \
    "$RUNNER_TEMP/azkar-signing-check"
}

collect_packages() {
  [[ "$CHANNEL" == stable || "$CHANNEL" == dev ]] || { echo 'CHANNEL must be stable or dev.' >&2; return 1; }
  local other tag
  mkdir -p "repository-input/$CHANNEL"
  cp release-assets/*.deb release-assets/*.rpm release-assets/SHA256SUMS "repository-input/$CHANNEL/"
  other=stable
  if [[ "$CHANNEL" == stable ]]; then other=dev; fi
  tag="$("$(dirname "${BASH_SOURCE[0]}")/metadata.main.kts" "latest-$other-tag")"
  if [[ -n "$tag" ]]; then
    mkdir -p "repository-input/$other"
    gh release download "$tag" --dir "repository-input/$other" \
      --pattern '*-linux-x64.deb' --pattern '*-linux-x64.rpm' --pattern SHA256SUMS
  fi
}

build_site() {
  local key_id
  export GNUPGHOME="$RUNNER_TEMP/azkar-repo-gnupg"
  key_id="$(cat "$RUNNER_TEMP/azkar-repo-key-id")"
  ./scripts/release/publish_linux_repositories.main.kts \
    repository-input repository-site "$RUNNER_TEMP/azkar-repo.asc" "$key_id"
  (cd repository-site && find . -type f -print0 | sort -z | xargs -0 sha256sum) > "$RUNNER_TEMP/site-SHA256SUMS"
}

verify_site() {
  local site_url attempt current entry file
  site_url="${PAGE_URL%/}"
  for attempt in {1..12}; do
    current="$(curl --fail --silent --show-error "$site_url/$CHANNEL-version.txt" || true)"
    if [[ "$current" == "$APP_VERSION" ]]; then break; fi
    if [[ "$attempt" == 12 ]]; then echo 'Deployed repository version did not become available'; exit 1; fi
    sleep 5
  done
  mkdir verified
  while IFS= read -r entry; do
    file="${entry:66}"
    [[ "$file" == ./* && "$file" != *..* ]]
    mkdir -p "verified/$(dirname "$file")"
    curl --fail --location --silent --show-error --retry 5 --retry-all-errors \
      --output "verified/$file" "$site_url/${file#./}"
  done < expected/site-SHA256SUMS
  (cd verified && sha256sum --check ../expected/site-SHA256SUMS)
}

case "${1:-}" in
  import-key) import_key ;;
  collect-packages) collect_packages ;;
  build) build_site ;;
  verify) verify_site ;;
  *) echo "Usage: $0 {import-key|collect-packages|build|verify}" >&2; exit 2 ;;
esac
