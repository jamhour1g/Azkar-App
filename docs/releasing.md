# Releasing the desktop app

The desktop build is packaged with [Hydraulic Conveyor](https://conveyor.hydraulic.dev).
Stable releases come from version tags on `production`. Manually dispatching the
Release workflow from `dev` updates a separate rolling preview release. Both
channels are built for all three platforms from a Linux runner:

| OS      | Output                                | Auto-update mechanism        |
|---------|---------------------------------------|------------------------------|
| Windows | MSIX + `.appinstaller`                | Background MSIX updates      |
| macOS   | `.app.tar.gz` + Sparkle 2 appcast     | Sparkle (EdDSA signatures)   |
| Linux   | `.deb` + apt repository               | apt (signed repo metadata)   |

For production, Conveyor generates the download-page HTML and uploads the packages
into the versioned GitHub Release (`make copied-site`). For dev, the workflow
attaches build artifacts to the `dev-preview` GitHub prerelease and publishes the
full update site to a separate directory on GitHub Pages.

## One packaging path

Conveyor is the only thing that packages this app.

The Compose Multiplatform plugin's own `nativeDistributions` block used to sit
next to it in [app/build.gradle.kts](../app/build.gradle.kts) as a second,
jpackage-based packager, and it has been removed. The reason is Linux-only and
it is not cosmetic.

jpackage builds its desktop entry from
`classes/jdk/jpackage/internal/resources/template.desktop` inside the JDK, and
that template has **no `StartupWMClass` key at all**. A desktop entry without
one cannot be matched to the running window — the desktop manager matches on
the window's `WM_CLASS` — so a jpackage-built `.deb` puts the app in the dock
as an unassociated window: a generic Java icon, and the window's raw
`WM_CLASS` as the label. Conveyor's desktop entry does carry
`StartupWMClass`, and the Conveyor Gradle plugin derives it from the Compose
plugin's `mainClass`, so the two agree by construction.

Do not re-add a jpackage packaging path. If a second packager is ever needed,
it has to generate the desktop entry itself.

What the Compose extension still contributes to Conveyor:

| Compose setting | Becomes                                     |
|-----------------|---------------------------------------------|
| `mainClass`     | `app.jvm.gui.main-class`, `StartupWMClass`  |
| `jvmArgs`       | `app.jvm.options`                           |
| `buildTypes`    | the ProGuard-shrunk jars that get packaged  |

## Start at login

The runtime uses [Nucleus AutoLaunch](https://nucleusframework.dev/en/docs/lifecycle/auto-launch/)
with Conveyor packaging. `conveyor.conf` supplies the `nucleus.app.*` identity properties,
the executable-type hint for each OS, and a disabled-by-default MSIX startup task whose
ID matches `nucleus.app.startup.task.id`. Keep those values aligned when changing packaging.
The preview channel derives its own identity and does not migrate production registrations.

Windows uses the native MSIX StartupTask API; the macOS package requires macOS 13 or
newer because Nucleus's `SMAppService` native bridge declares that minimum in its Mach-O
header. `app.mac.info-plist.LSMinimumSystemVersion` must match it for both CPU architectures.
Host Linux uses a systemd user service. The settings toggle reads the
OS state, including user and policy restrictions, and refreshes when the window regains focus.
The saved preference is retained only as a record of the last choice and for migration.
Old `.cmd`, LaunchAgent, and `.desktop` registrations are retired after a successful migration;
failed migrations retain the old files. Gradle and IDE launches cannot register for startup.

Before releasing, test the installed app on each OS: enable startup, sign out and back in,
confirm it stays in the tray when available, then disable startup and repeat. On Windows,
also disable it through Task Manager and verify Azkar reflects that state and links to
system settings. Unit tests and cross-platform packaging checks do not verify a real login.

## The Linux desktop entry

A Linux desktop manager matches a running window to its `.desktop` entry
through two strings that have to be equal:

- the `StartupWMClass` in the desktop entry, and
- the window's `WM_CLASS`.

They are computed independently, which is what makes the agreement fragile:

- Conveyor writes `StartupWMClass` by taking the packaged entry point's class
  name and replacing its dots with hyphens (`com.azkar.MainKt` becomes
  `com-azkar-MainKt`),
- AWT reports `WM_CLASS` from the class name of the **bottom stack frame** of
  whichever thread first constructed the AWT toolkit, with the same
  dots-to-hyphens substitution.

`Main.kt` therefore calls `Toolkit.getDefaultToolkit()` as the first statement
of `main`, on the main thread, so the class AWT sees is `com.azkar.MainKt` and
the window reports `com-azkar-MainKt` — the same value Conveyor writes. Both
sides derive from one class name, so they cannot drift.

Two things follow, and both are easy to break by accident:

- **The toolkit must be initialised on the main thread, before any suspension
  point.** The startup sequence suspends (database warm-up, settings stores), and
  a coroutine worker touching AWT first would report
  `kotlinx-coroutines-scheduling-CoroutineScheduler$Worker` instead — which is
  exactly what the dock used to display.
- **ProGuard must not obfuscate.** `obfuscate` is off in the release build
  type, and that is load-bearing: renaming `com.azkar.MainKt` would change the
  window's `WM_CLASS` while the packaged `StartupWMClass` stayed as it is.

`DesktopWindowClassSuite` in
[app/src/test/kotlin/com/azkar](../app/src/test/kotlin/com/azkar) asserts this
agreement, so a refactor that breaks it fails the build rather than the dock.

A window with no matching desktop entry has no icon either. Running
`./gradlew :app:run` from source installs no `.desktop` file, so the dock shows
the unassociated-window appearance even when everything is correct. Install the
package to see the real thing:

```bash
sudo dpkg -i output/packages/linux/jamhour1g-azkar_<version>_amd64.deb
```

## One-time setup

1. Install the Conveyor CLI (see the [website](https://conveyor.hydraulic.dev)).
2. Generate the root signing key once (it derives every platform key: MSIX,
   Sparkle, apt repo metadata…). On Linux you will be prompted for a
   passphrase; choose a strong one — it encrypts the phrase on disk and in the
   CI secret:

   ```bash
   # Prompts: "Enter a passphrase" (pressing enter would create a passwordless key).
   conveyor keys generate
   ```

   The (encrypted) phrase is written to the per-user Conveyor defaults file
   (`~/.config/hydraulic/conveyor/defaults.conf`) as a single HOCON line. Push
   its value (**no quotes**) and the passphrase into two repo secrets:

   ```bash
   gh secret set SIGNING_KEY --repo jamhour1g/Azkar-App \
     < <(grep '^app.signing-key' ~/.config/hydraulic/conveyor/defaults.conf \
         | sed 's/^app.signing-key *= *//' | tr -d '"' | tr -d '\n')

   gh secret set SIGNING_PASSPHRASE --repo jamhour1g/Azkar-App
   ```

   The release workflow supplies the passphrase programmatically
   (`--passphrase=env:SIGNING_PASSPHRASE`), so nothing else is needed for CI.
   Locally, `conveyor` will prompt for it on each signing build, or accept
   `conveyor --passphrase=env:MY_PASS make`.

   Back the encrypted phrase **and the passphrase** up in a password manager
   immediately — losing the passphrase makes the key unusable, and after the
   first release there is no safe way to replace it.
3. Do **not** transfer keys into the repo. `conveyor.output/`, `.conveyor/` and
   `defaults.conf` are already gitignored.

## Rotating the signing key

- **Signature matter:** never rotate once real releases exist. Every installed
  app verifies future updates against the root key it signed with; a swapped
  key makes installed copies distrust (or refuse) the update feed. Treat the
  phrase as release-critical state that dies with the project.
- **Before the first release** (no installed base yet), rotation is free:

  ```bash
  # 1. nuke the local identity (defaults.conf + apple.csr) and regenerate
  rm ~/.config/hydraulic/conveyor/defaults.conf ~/.config/hydraulic/conveyor/apple.csr
  conveyor keys generate   # prompts for the passphrase again

  # 2. overwrite both GitHub secrets with the new values
  gh secret set SIGNING_KEY --repo jamhour1g/Azkar-App \
    < <(grep '^app.signing-key' ~/.config/hydraulic/conveyor/defaults.conf \
        | sed 's/^app.signing-key *= *//' | tr -d '"' | tr -d '\n')
  gh secret set SIGNING_PASSPHRASE --repo jamhour1g/Azkar-App
  ```

  Note the exact quoting: the pipeline yields the bare value (one line), which
  match how the release action wires the key (`conveyor -Kapp.signing-key=<secret>`).

  A **passphrase-only change** (keep the key itself) is safe at any time and
  never disturbs the update chain:

  ```bash
  conveyor keys passphrase     # prompts old + new
  gh secret set SIGNING_PASSPHRASE --repo jamhour1g/Azkar-App
  ```

## Releasing a version

The tag version must equal the Gradle `version` at the top of
[app/build.gradle.kts](../app/build.gradle.kts) — the workflow aborts on mismatch.
The tag's commit must also already be on `production`; tags from `dev` cannot
publish a stable release.

```bash
# 1. bump "version = \"x.y.z\"" in app/build.gradle.kts and commit
# 2. tag the commit, v-prefixed
git tag -a v0.2.0 -m "Azkar 0.2.0"
git push origin v0.2.0
```

The [Release workflow](../.github/workflows/release.yml) then:

1. Runs `./gradlew :app:checkTagParity`, which strips the leading `v` from the
   pushed tag and fails the build unless it equals the Gradle `version`.
2. Runs `./gradlew proguardReleaseJars` to shrink, optimize and obfuscate the
    classpath with ProGuard (keep rules live in `app/proguard-rules.pro`;
    output in `app/build/compose/tmp/main-release/proguard/`). This roughly
    halves the dependency footprint (~94 MB -> ~40 MB; unused Material icons
alone drop 36.9 MB -> 87 KB). ProGuard reads the JDK's own classes from its
     `jmods` directory, and Temurin dropped that folder in 24 (JEP 493), so the
     build (and CI) use **Zulu 25**, which still ships one. `:app:verifyJdkShipsJmods`
     runs before it and fails with that explanation, rather than leaving ProGuard to
     report the missing JDK as thousands of warnings about `java.lang.Object`.
     Conveyor never runs Gradle itself; app code (`com.azkar.**`) is fully kept, so
     verification only
    re-names third-party classes — run the app once after any ProGuard/rules
    or dependency change before tagging.
3. Runs `conveyor make copied-site`, which builds every platform and pushes the
   generated packages and `download.html` into the GitHub Release of that tag.

Users install from the Release page (`download.html`); installed copies pick up
later releases automatically (Windows background update, macOS Sparkle prompt,
Linux `apt upgrade`).

The download page is also published to GitHub Pages: `app.site.github.pages-branch`
in [conveyor.conf](../conveyor.conf) points at the `gh-pages` branch, so the
same `make copied-site` run leaves the page at
`https://jamhour1g.github.io/Azkar-App/download.html`. The page is the
storefront and the Release is the warehouse: the buttons on the page link
straight to `releases/latest/download/…`, so the page is only useful once a
release exists, and the auto-updates keep reading the Release either way.

## Testing a dev preview

Run the Release workflow manually with the `dev` branch selected to publish a
GitHub prerelease named `dev-preview`. The workflow runs checks before packaging.
The packages are
labeled **Azkar Dev** and use a
separate application identity (`com.azkar.app.dev` / `azkar-dev`), so testers
can install them alongside production. The preview's complete update site is
hosted at `https://jamhour1g.github.io/Azkar-App/dev-preview/`; publishing it to
GitHub Pages preserves the directory paths required by Debian clients. The
prerelease still provides a convenient place to download the build artifacts.
The preview does not read or replace the production `releases/latest` feed.
Previews do not run on ordinary pushes, so they consume Actions only when
requested.

Existing dev installations have the old GitHub Release URL saved in their APT
source. Install the first `.deb` built after this change once; that package
updates the source to the Pages repository, after which `apt upgrade` works.

The dev preview version uses the release workflow run number. Production keeps
using the Gradle version and the `v*` tag parity check. Promote code by opening a
PR from `dev` into `production`; after it passes the three-platform promotion
gate and merges, bump the Gradle version and tag that production commit.

## Caveats

- **Do not publish dev packages with `make copied-site`.** Production updates
  read `releases/latest/download`, which ignores GitHub prereleases. The dev
  workflow instead builds a separate preview identity and publishes a separate
  update site, so neither channel can move the other's update feed.
- **macOS is unsigned.** Without an Apple Developer ID the app is self-signed
  and Gatekeeper will warn on first launch (right-click -> Open). Sparkle
  signatures still protect updates from tampering; the EdDSA root key is the
  `SIGNING_KEY` secret.
- **Windows is self-signed with the same key.** For per-user MSIX installs, use
  the generated `.appinstaller` link; enterprise devices can trust the
  certificate bit further if desired.
- **App icon** currently uses `assets/icons/azkar.png` (256x256). Re-export a
  1024px PNG (macOS wants 1024) and point `app.icons` at it when available.
- **Version bumps must be real commits** in `app/build.gradle.kts`; the tag
  equality check is deliberately strict.
- **Free-license duty.** Conveyor is free for this project under its
  open-source license, which requires linking the tool in a reasonably visible
  place (their EULA §6.5, "link to the Conveyor website … in the README or
  download page"). Add a "Packaged with
  [Hydraulic Conveyor](https://conveyor.hydraulic.dev)" line to the README.
- **Local dry run needs `GITHUB_TOKEN=dummy`** exported; plugin include and
  `site.github.oauth-token` are otherwise unresolved substitutions.

## Local dry run

To package the stable production identity for all platforms without publishing it, run:

```bash
./gradlew :app:packageStableRelease
```

This uses the production app identity and version from `app/build.gradle.kts`. It keeps the
complete Conveyor site in `output/site/` and creates `output/packages/` with installer files
grouped under `linux/`, `windows/`, and `macos/`. The grouped files are hard links to the site
files when supported, so they do not duplicate large packages. Re-running the task replaces the
generated `output/` directory. The GitHub release workflow still uploads the Conveyor site in its
expected flat form; this task only organizes the local output. As with the preview task, provide
`CONVEYOR_PASSPHRASE` in the environment when the local signing key is encrypted.

To package a local preview for Linux, Windows, and macOS without publishing it, run:

```bash
./gradlew :app:packageDevPreview
```

The task uses the version from `app/build.gradle.kts`. To package a higher version, pass an
optional `-PdevVersion=x.y.z` override. It first builds the ProGuard jars, then writes the flat
Conveyor site to `output-dev/site/`. The task also creates `output-dev/packages/` with Linux,
Windows, and macOS installer archives grouped by platform; these are hard links to the site files
when supported, so they do not duplicate large packages. Keep the site directory intact because
its adjacent metadata and update files support installation and updates. The `Azkar Dev` package
identity allows preview installs to coexist with production. Since Gradle runs Conveyor without
an interactive console, enter the local root key passphrase through the environment before
invoking the task if the key is encrypted. Re-running the task replaces its previous generated
site and package index:

```bash
read -rsp 'Conveyor root-key passphrase: ' CONVEYOR_PASSPHRASE
printf '\n'
export CONVEYOR_PASSPHRASE
./gradlew :app:packageDevPreview
unset CONVEYOR_PASSPHRASE
```

For a key without a passphrase, run `conveyor keys passphrase` and set its new passphrase to
empty; the task then runs without the environment variable. Check whether that key is also used
by the GitHub release workflow before changing it, since CI's signing credentials may need to be
updated too. Conveyor can also create a new root key without a passphrase with
`conveyor keys generate --no-passphrase`, but that creates a different signing identity.

To install the Linux package, use the matching filename, for example:

```bash
sudo apt install ./output-dev/packages/linux/jamhour1g-azkar-dev_*_amd64.deb
```

On Windows, open the `.msix` in `output-dev/packages/windows/`; on macOS, extract a `.zip` from
`output-dev/packages/macos/` and launch `Azkar Dev.app`. For updates or web-based installation,
use the files in `output-dev/site/`. This task only creates local files; publishing still happens
through the release workflow.

GitHub Release assets stay at the release root because Conveyor's update metadata references
their exact filenames. The release notes include a platform-organized index with direct installer
links, while the full update and support files remain available in the asset list.

```bash
GITHUB_TOKEN=dummy ./gradlew :app:printConveyorConfig   # show merged config
./gradlew proguardReleaseJars                           # shrink jars into app/build/compose/tmp/main-release/proguard
conveyor keys generate --no-passphrase -o /tmp/dummykeys --no-keyring  # throwaway key (linux)
CONVEYOR_AGREE_TO_LICENSE=1 \
  conveyor "-Kapp.signing-key=$(grep '^app.signing-key' /tmp/dummykeys/defaults.conf | sed 's/^app.signing-key *= *//' | tr -d '"' | tr -d '\n')" \
  make site                                             # list tasks; builds via conveyor make site
```

The `-Kapp.signing-key=…` redirect lets you dry-run without decrypting the real
key (`conveyor make` remembers the host key but needs a console prompt for the
passphrase; headless runs must override).

To rehearse the workflow itself — the same steps CI runs, on every platform —
use act:

```bash
./gradlew :app:verifyReleaseWorkflow
```

It needs Docker and the `conveyor` CLI on PATH, generates a throwaway signing
key in `~/.conveyor-acttest` on first use, and builds and signs for roughly half
an hour. Publishing is the one step that cannot succeed, and that is the
point: the task strips the credentials from the staged workflow before the
rehearsal runs, because `act` hands the container the GitHub token it finds
on this host, and Conveyor would otherwise create a real release signed with
the throwaway key. The run therefore ends with a failed publish step, which
is the proof that nothing was published. Everything before it — the build, the
signing, the packaging, the upload — is the real workflow.

act copies its whole working directory into the runner container, and this
repository is mostly `.git` and build output, so the task first mirrors a pruned
copy into `app/build/act/staging/` (everything except `.git`, `.gradle`, `build`
directories and the Conveyor output folders) and rehearses from there. That copy
is what runs, so a rehearsal always tests the current working tree.

The runners are containers that are discarded afterwards, so the `.msi`, `.dmg`
and `.deb` files Conveyor produces are re-exported to
`app/build/act/artifacts/` (as zipped uploads, one per platform). Inside a run
they live in `conveyor.output/` in the container workspace.

Stop a rehearsal with `Ctrl+C`; act removes its runner on the way out. If a run
was killed hard (or the terminal closed) and left a container behind, clear it
with:

```bash
docker ps -aq --filter ancestor=catthehacker/ubuntu:act-latest | xargs -r docker rm -f
```
