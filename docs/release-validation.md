# Installed release validation

Complete this record before the first Stable release and after changes to packaging,
updater, startup integration, native runtime, icons, or distribution workflows.
A successful package build or headless runtime smoke test does not replace these checks.

Build candidate installers from one committed revision. Record that full commit SHA,
the candidate version, OS versions, and results in a PR or a committed Markdown note.
Use disposable machines/profiles and two candidate versions to exercise real updates.

| Target | Required checks |
|---|---|
| Windows x64 | NSIS install, app/menu/shortcut icons, launch, tray, start at login, approved download/install/restart, Stable ↔ Dev including an older version |
| macOS Intel | DMG install, launch, menu bar, start at login, ZIP update/relaunch, Stable ↔ Dev |
| macOS Apple Silicon | Same checks on native ARM64 hardware |
| Debian/Ubuntu x64 | Signed APT metadata, install, launch/menu icon, update, Stable ↔ Dev including explicit downgrade commands |
| Fedora/DNF x64 | Signed RPM metadata, install, launch/menu icon, update, Stable ↔ Dev including explicit downgrade commands |
| Arch x64 | AppImage execution (document FUSE requirements), icon/tray, update in a writable location, relaunch, Stable ↔ Dev |

In each in-app update path, confirm automatic/manual checks, version/release notes,
checking/downloading/installing feedback, approval before download and installation,
and a clean return to “Up to Date” when no update exists. Confirm shared data remains
available after channel changes. Binary downgrades do not reverse database migrations.

After testing, update [release-validation.properties](release-validation.properties):

- `sourceCommit`: full tested source commit SHA; it must be an ancestor of the release.
- `artifactVersion`: version embedded in the tested installers.
- `evidence`: link to the recorded results or a committed report path with enough detail
  to identify the machines and tested update paths.
- Set each platform result to `passed` only after its checklist succeeds.

Commit the record separately after the candidate source commit. Stable validation runs
`./scripts/release/check_release_validation.main.kts`; it rejects missing platform results,
unknown commits, and distribution-source changes after the tested commit. Ordinary UI
changes do not invalidate the record. New distribution source files should be added to
that script's `distributionPaths` list when they live outside its existing directories.
The initial record deliberately remains pending; no native installation results have
been invented. Windows/macOS installers may remain unsigned during development; this
checklist does not claim signing or notarization.
