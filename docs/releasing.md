# Releasing the desktop app

Nucleus 2.5.18 packages the existing Compose Desktop JVM application. The Compose UI, AWT windows, tray and ProGuard rules remain in the app; only installation and update packaging moved from Conveyor.

| Platform | Installer and update path | Initial architecture |
|---|---|---|
| Windows | NSIS `.exe`; Nucleus checks, downloads and installs after approval | x64 |
| macOS | DMG for installation; ZIP is the Nucleus replacement payload | Intel and Apple Silicon |
| Linux | `.deb` and `.rpm` through signed apt/dnf repositories; AppImage through Nucleus | x64 |

Linux repositories have Stable and Dev suites. apt/dnf owns package transactions; Azkar shows package-manager commands for updates and channel changes. AppImage, Windows and macOS use Nucleus in-app updates. The app checks on startup and from Settings, shows release notes, and waits for approval before downloading and installing. A channel change is an explicit request and can offer an older version; Azkar warns that the installer does not roll back database changes.

## Local packaging

Use JDK 25 (Zulu in CI; ProGuard needs `jmods`). Nucleus packages on the matching host OS:

On Ubuntu/Debian, install the RPM build tool before building the full Linux format set:

```bash
sudo apt install rpm
```

The build checks for `rpmbuild` before packaging and gives the installation command when it is missing.

```bash
./gradlew :app:packageCurrentOS
```

For a local Dev package, use a unique SemVer prerelease with a `-beta.N` suffix:

```bash
./gradlew :app:packageCurrentOS -PappVersion=0.1.2-beta.1
```

Final packages, update metadata and `SHA256SUMS` are staged under `build/installers/<platform>/`, where platform is `linux-x64`, `windows-x64`, `macos-x64`, or `macos-arm64`. For example, a Dev AppImage is `build/installers/linux-x64/AppImage/azkar-0.1.2-beta.1-linux-x64.AppImage`. The staging task removes stale outputs for that platform. Intermediate Nucleus files stay under `app/build/compose/binaries/main-release/`.

`packageCurrentOS` also runs `smokeTestPackagedRuntime`: it launches the bundled release runtime in headless mode, opens/migrates a database in a fresh temporary profile, closes it, and requires a success marker within 90 seconds. It does not access the normal user profile, register startup tasks, or check for updates. The log is `app/build/reports/packaging/smoke-test.log`. Run `./gradlew :app:smokeTestPackagedRuntime` to check the runtime without rebuilding installer formats.

Linux CI packages DEB, RPM and AppImage; Windows CI packages NSIS; macOS CI packages DMG and ZIP on both supported architectures. Windows installer/uninstaller and macOS DMG icons are configured separately from the app icons. An AppImage embeds its icon, but file-manager thumbnails still depend on desktop support.

The release merger requires all eight platform/format artifacts and checks their exact versioned filenames, channel, manifest coverage, sizes and SHA-512 hashes. Duplicate filenames, missing architectures, unsafe URLs and unexpected files fail validation before output is written. Native installer metadata may contain the numeric base version for EXE/DMG/ZIP; final update manifests always carry the full release version. Run its fixture tests with `./scripts/merge_nucleus_release.main.kts self-test` (requires `kotlinc`).

Linux staging also checks the actual DEB/RPM internal versions and architectures. Both use `~beta.N` internally so Dev sorts below the next Stable version. The RPM task receives the full SemVer through Nucleus's public task API because its DSL validator rejects prereleases before electron-builder can convert them. Packaging regenerates each format's working directory because Nucleus 2.5.18 otherwise reuses an existing `package.json` and fallback manifest across versions.

Local `act` runs never upload GitHub artifacts or publish releases. In a disposable checkout, run a Linux-only packaging rehearsal with a Dev event payload:

```bash
printf '%s\n' '{"ref":"refs/heads/dev","repository":{"default_branch":"dev"}}' > /tmp/azkar-act-event.json
act push -W .github/workflows/release.yml -e /tmp/azkar-act-event.json -j package --matrix id:linux-x64 --bind
```

Local preparation uses `0.0.0-beta.0` without GitHub credentials. Outputs stay in that checkout's `build/installers/`; the publish job explicitly rejects `ACT=true`. Container-created files may have container-user ownership, so prefer the direct Gradle task in your working checkout. Linux containers cannot validate native Windows/macOS installers.

Nucleus 2.5.18 was selected as a released, pinned baseline. It has Compose Gradle integration through reflection and an internal Compose JDK-probe dependency. The Linux DEB package was built successfully against this checkout's Kotlin 2.4.20, Compose 1.12.1 and Zulu JDK 25. Re-run package and installed-update checks when upgrading Compose or Nucleus. Review any future Compose bytecode patch before enabling it; the released 2.5.18 packaging path does not enable the later default text patch.

## Stable releases

Stable publication is an explicit version tag on `production`. First bump the default app version in `app/build.gradle.kts`, commit it to production, then tag that commit:

```bash
git tag -a v0.1.2 -m "Azkar 0.1.2"
git push origin v0.1.2
```

The workflow verifies the tag is on `production`, runs source checks and packaged-runtime smoke tests, and builds on Linux, Windows, macOS Intel and Apple Silicon runners. It validates the complete artifact set, prepares a draft release, builds and verifies signed repository metadata, pushes the repository branch, then exposes the release. Already published assets cannot be overwritten by a rerun. The release tag and Gradle package version must match. This is ordered publication across two services, not an atomic transaction: if final release publication fails, retry the existing draft after checking repository state.

## Dev releases

Every successful push to `dev` runs checks before packaging. The workflow selects the next patch after the latest Stable version and adds a unique Beta suffix based on the workflow run and attempt, for example `0.1.2-beta.84.1`. This suffix is what Nucleus's released GitHub provider uses to select the Dev feed. Dev packages use the same application identity and data directory as Stable.

The workflow automatically publishes a versioned GitHub prerelease after updating the signed Dev apt and RPM repositories. Dev release notes describe changes since the previous successful Dev prerelease. Reruns receive a new version. Stable tags remain explicit and are accepted only from `production`.

## Signed Linux repositories

Repository metadata is signed by a dedicated GPG key, separate from any Windows or Apple signing identity. Before publishing, configure these GitHub Actions secrets:

- `LINUX_REPO_SIGNING_KEY`: ASCII-armored private GPG key used only by the repository publication job.
- `LINUX_REPO_SIGNING_PASSPHRASE`: its passphrase (may be empty for an unprotected automation key).

The public key and repository metadata are published to the `gh-pages` branch. The public key URL is `https://jamhour1g.github.io/Azkar-App/keys/azkar-repo.asc`. User-facing apt/dnf setup examples are maintained in [download.html](download.html). RPM repository metadata is signed; its package checksums are covered by that signed metadata. APT verifies package checksums through its signed Release metadata.

The `publish_linux_repositories.main.kts` script builds and verifies repository metadata, then commits it to `gh-pages`; the release workflow prepares the key and worktree before invoking it.

A repository release requires GitHub Pages publication to be enabled for `gh-pages`. Linux packages use the same package name in Stable and Dev, so switching suites replaces the selected package source rather than adding a second application.

## Package and runtime details

- The Linux desktop entry sets `StartupWMClass=com-azkar-MainKt`, matching the main-thread AWT initialization in `Main.kt`. ProGuard keeps class names unchanged for the same reason.
- The Nucleus AutoLaunch runtime identity is pinned to `com.azkar.app`; the macOS bundle identifier uses that same identity. macOS keeps its macOS 13 minimum for the AutoLaunch native bridge.
- Linux `.deb` and `.rpm` package versions order Dev below the next Stable version. The app's settings show the current version and selected channel; the package manager decides when the repository transaction is applied.
- Existing Conveyor migration is out of scope while Azkar is still in development. The new Windows installer has no in-place MSIX upgrade claim.

## Verification boundary

A successful Gradle package task proves build-time packaging only. Before public production release, install each artifact and check launch, shortcuts, tray behavior, update replacement and restart. Test the AppImage on Arch and test apt/dnf install, signed metadata verification, updates and both channel switches in disposable supported distributions. Verify macOS ZIP replacement separately from DMG installation. Real login-session checks remain necessary for start-at-login behavior.
