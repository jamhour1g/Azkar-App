# CI

The branch flow is **feature PRs → `dev` → `production`**. Both long-lived branches
require PRs and successful checks, including for administrators; no reviewer approval
count is required for the solo-maintainer workflow.

| PR target | Required status | Work |
|---|---|---|
| `dev` | `Azkar CI / Linux` | Linux build/tests/static analysis, packaged-runtime smoke test, release-script self-tests |
| `production` | Both `Azkar CI / Linux` and `Azkar CI / Installers` | Above plus Windows, Intel/ARM macOS, all eight installers and merged asset validation |

## Manual PR CI

Anyone can open or update a PR. PR events do not start CI. After reviewing a revision,
the owner manually dispatches CI with the PR number and its full current head SHA. A
new commit requires a fresh review and a new CI run before merge. This applies to the
owner's PRs too; the required status stays pending until that exact revision passes.

After reviewing the code, obtain the SHA and start the trusted default-branch workflow:

```bash
gh pr view 123 --json headRefOid --jq .headRefOid
gh workflow run ci.yml --ref dev -f pr_number=123 -f reviewed_sha=<full-reviewed-SHA>
```

The GitHub Actions “Run workflow” UI exposes the same inputs. The dispatch must be
performed by the repository owner. For contributor PRs, select `dev`, the default
branch, so the trusted workflow definition runs. To rehearse workflow changes in an
owner-authored PR, select that same-repository PR branch; its branch and commit must
match the PR exactly. Retargeted PRs need a fresh run. Use
`-f validate_installers=true` for a full four-runner rehearsal of a dev PR.

The manually dispatched workflow resolves the live PR through the API and marks the
reviewed head pending.
Separate build jobs check out GitHub's PR merge revision and verify both parents match
the approved head and selected base. This catches PR/base races before executing code.
For contributor PRs, the workflow definition comes from trusted `dev`; contributor
workflow files are never used for their CI run.

Build jobs use read-only repository permissions, no publishing secrets, and checkout
with `persist-credentials: false`. All workflows disable Gradle Actions caches so
PR runs cannot poison caches later consumed by releases. A separate reporting
runner, with no source checkout, writes the required statuses to the reviewed head
from job results. A stale head never receives a successful status from that run.
Manual workflow job checks alone do not satisfy PR branch protection, which is why the
explicit `Azkar CI / …` commit statuses are used.

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

Validated pushes to `dev` publish uniquely versioned Dev prereleases. Stable publication
uses explicit `vMAJOR.MINOR.PATCH` tags on `production` and requires current recorded
installed-release evidence. The release queue preserves waiting runs (up to GitHub's
100-pending-run queue limit); Dev and Stable publication is serialized.

Repositories contain only the newest Stable and newest Dev DEB/RPM pair. Publication
builds a fresh signed site from those GitHub release assets, deploys it explicitly with
Pages Actions, verifies every live file against the generated site's checksums, and
then publishes the draft GitHub release. No package binaries are committed to Git.
See [releasing](releasing.md) for secrets and recovery.

Local `act` runs cannot upload artifacts or reach publication: all CI/package uploads
exclude `ACT=true`, and publication exits before any release or repository mutation.
Linux containers do not validate native Windows/macOS behavior.

## Local checks

```bash
./gradlew spotlessApply detekt test
./gradlew :app:smokeTestPackagedRuntime
./scripts/merge_nucleus_release.main.kts self-test
./scripts/publish_linux_repositories.main.kts self-test
./scripts/check_release_validation.main.kts self-test
```

CI verifies formatting; local Gradle `build` applies it. Git and Spotless both enforce LF.
Use `actionlint` for workflow expressions and shell syntax. Native multi-platform jobs
and Pages deployment must still be validated on GitHub after the new workflow definitions
reach the default branch.
