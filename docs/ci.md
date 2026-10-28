# CI

The branch flow is **feature PRs → `dev` → `production`**. Both long-lived branches
require PRs and successful checks, including for administrators; no reviewer approval
count is required for the solo-maintainer workflow.

| PR target | Required status | Work |
|---|---|---|
| `dev` | `Azkar CI / Linux` | Linux build/tests/static analysis, packaged-runtime smoke test, release-script self-tests |
| `production` | Both `Azkar CI / Linux` and `Azkar CI / Installers` | Above plus Windows, Intel/ARM macOS, all eight installers and merged asset validation |

## Contributor approval

Anyone can open or update a PR. The owner's PRs run automatically. Other authors'
PRs start **no CI runners** until the owner reviews and manually dispatches CI with
both the PR number and its full current head SHA. A new commit requires fresh approval.

After reviewing the code, obtain the SHA and start the trusted default-branch workflow:

```bash
gh pr view 123 --json headRefOid --jq .headRefOid
gh workflow run ci.yml --ref dev -f pr_number=123 -f reviewed_sha=<full-reviewed-SHA>
```

The GitHub Actions “Run workflow” UI exposes the same inputs. Select `dev`, the default
branch, rather than a contributor's branch. The dispatch must be performed by the
repository owner. Retargeted PRs also need a fresh run. To rehearse workflow changes, the owner can
manually select their own same-repository PR branch; its branch and commit must match
the PR exactly. Contributor approvals must always use the trusted default branch.
Use `-f validate_installers=true` for a full four-runner rehearsal of a dev PR.

For an owner-reviewed documentation-only PR targeting `dev`, the owner can set
`skip_checks=true` to avoid the Gradle and installer jobs:

```bash
gh workflow run ci.yml --ref dev \
  -f pr_number=123 \
  -f reviewed_sha=<full-reviewed-SHA> \
  -f skip_checks=true
```

The trusted authorization job confirms the PR is still open at that exact head and targets `dev`,
then permits the skip only when every changed path is `README.md`, a Markdown file under
`docs/`, or the bundled seed-data notice. Renames check both the old and new paths. The
returned file count must match the PR's changed-file count, and PRs exceeding the API's
3,000-file limit cannot skip checks. It reports the required `Azkar CI / Linux`
status on the reviewed commit without starting build jobs. Production PRs and PRs that
change code or other files cannot use this option. A new commit needs a fresh owner review
and dispatch.

The `pull_request_target` trigger uses trusted base-branch workflow definitions. Its
owner-only authorization job never checks out PR code. It resolves the live PR through
the API and marks the reviewed head pending. Separate build jobs check out GitHub's
PR merge revision and verify both parents match the approved head and selected base.
This catches PR/base races before executing code. Contributors cannot bypass approval
by editing the CI YAML in their PR.

Build jobs use read-only repository permissions, no publishing secrets, and checkout
with `persist-credentials: false`. Contributor CI disables Gradle caching. The trusted
Release workflow caches Gradle state: only validation on the default `dev` branch writes
entries, while its parallel packaging/screenshot jobs and release tags are read-only. A
separate reporting runner, with no source checkout, writes the required statuses to the
reviewed head from job results. A changed head, base revision, or target branch cannot receive a
successful status from that run. If the base moves after the status is posted, GitHub branch
protection must require the branch to be up to date before merging.
Manual workflow job checks alone do not satisfy PR branch protection, which is why the
explicit `Azkar CI / …` commit statuses are used.
The reporting job also records the reviewed head/base and each job result in the run summary.

Configure GitHub's **approval for all external contributors** as an additional layer
for fork-authored workflows. Do not grant write access to untrusted contributors;
write access permits pushing workflow code to repository branches. New PR-triggered
workflows must preserve this approval model.

## Installer and runtime checks

Zulu JDK 25 is required because ProGuard needs `jmods`; `verifyJdkShipsJmods` fails early
if they are absent. `smokeTestPackagedRuntime` launches the linked release runtime in
headless mode against a fresh temporary database. It does not test native installation,
tray/login behavior or update replacement. See [installed validation](release-validation.md).

Production PRs build Linux DEB/RPM/AppImage, Windows EXE, and macOS DMG/ZIP on Intel and
Apple Silicon. The merger requires all eight files, matching versions, update metadata,
sizes and SHA-512 hashes. Failed/skipped packaging cannot produce a successful installer
status. Native build reports and installer artifacts are retained briefly for diagnosis.

## Release publication

Dev publication batches merged changes: schedules check every 30 minutes and publish only
when there are new commits and six hours have passed since the last Dev release. Manual
dispatch from `dev` can publish new commits sooner. PR checks still run per approved PR.
Stable publication
uses explicit `vMAJOR.MINOR.PATCH` tags on `production` and requires current recorded
installed-release evidence. Stable ancestry and installed-release evidence are checked in
the prepare job before packaging or screenshot runners start. Validation, packaging, and
screenshots then run in parallel, and publication requires all three to pass. Failed release
validation uploads test, Detekt, and shrinking logs for diagnosis. The release queue preserves
waiting runs (up to GitHub's 100-pending-run queue limit); Dev and Stable publication is serialized.

Repositories contain only the newest Stable and newest Dev DEB/RPM pair. Publication
builds a fresh signed site from those GitHub release assets, deploys it explicitly with
Pages Actions, verifies every live file against the generated site's checksums, and
then publishes the draft GitHub release. No package binaries are committed to Git.
See [releasing](releasing.md) for secrets and recovery.

Local `act` runs cannot upload artifacts or reach publication: all CI/package uploads
exclude `ACT=true`, and publication exits before any release or repository mutation.
Linux containers do not validate native Windows/macOS behavior.

## Local checks

The [scripts index](../scripts/README.md) lists CI, release, and test entry points.

```bash
./gradlew spotlessApply detekt test
./gradlew :app:smokeTestPackagedRuntime
./scripts/release/merge_nucleus_release.main.kts self-test
./scripts/release/publish_linux_repositories.main.kts self-test
./scripts/release/check_release_validation.main.kts self-test
./scripts/tests/test_ci_authorization.main.kts
./scripts/tests/test_release_workflow.main.kts
./scripts/tests/test_github_client.main.kts
./scripts/ci/check_workflows.sh
```

CI verifies formatting; local Gradle `build` applies it. Git and Spotless both enforce LF.
`check_workflows.sh` checks workflow expressions and embedded shell syntax with actionlint
and checks `scripts/release/*.sh` with ShellCheck (install ShellCheck locally; it is included
on the Ubuntu CI runner). If
actionlint is absent on Linux x64, the script downloads version 1.7.12 and verifies its pinned
SHA-256 checksum. On other platforms, install actionlint first. PR CI runs this before Gradle;
Release runs it during preparation. Until actionlint supports GitHub's `queue: max`, the
script replaces only that exact setting with a comment in the lint input; the actual workflow
keeps its full release queue, and other queue values still fail linting.

Native multi-platform jobs and Pages deployment must still be validated on GitHub after the new workflow definitions
reach the default branch.
