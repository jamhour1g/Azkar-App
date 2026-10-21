# Releasing the desktop app

The desktop build is packaged with [Hydraulic Conveyor](https://conveyor.hydraulic.dev).
One tag push produces all three platform packages from a single Linux runner and
publishes them to the GitHub Release:

| OS      | Output                                | Auto-update mechanism        |
|---------|---------------------------------------|------------------------------|
| Windows | MSIX + `.appinstaller`                | Background MSIX updates      |
| macOS   | `.app.tar.gz` + Sparkle 2 appcast     | Sparkle (EdDSA signatures)   |
| Linux   | `.deb` + apt repository               | apt (signed repo metadata)   |

Conveyor also generates the download-page HTML and uploads everything into the
Release assets (`make copied-site` in
[.github/workflows/release.yml](../.github/workflows/release.yml)).

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
sudo dpkg -i output/jamhour1g-azkar_<version>_amd64.deb
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
     build (and CI) use **Zulu 25**, which still ships one. Conveyor never runs
     Gradle itself; app code (`com.azkar.**`) is fully kept, so verification only
    re-names third-party classes — run the app once after any ProGuard/rules
    or dependency change before tagging.
3. Runs `conveyor make copied-site`, which builds every platform and pushes the
   generated packages and `download.html` into the GitHub Release of that tag.

Users install from the Release page (`download.html`); installed copies pick up
later releases automatically (Windows background update, macOS Sparkle prompt,
Linux `apt upgrade`).

## Caveats

- **No pre-release tags.** The update feed reads
  `releases/latest/download`, which ignores pre-releases and their assets.
  Publishing a `-alpha`/`-beta` tag would leave existing installs orphaned.
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
an hour. Publishing is the one step that cannot succeed: `GITHUB_TOKEN` is a
dummy, which is what stops the rehearsal from touching a real release.

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
