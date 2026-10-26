# Development

This guide covers running Azkar from source, checking changes locally, capturing screenshots, and
finding the main parts of the project. For branch protections and contributor CI approval, see
[CI](ci.md). For release builds, see [Releasing](releasing.md).

## Requirements and first run

Azkar is a Kotlin desktop application built with
[Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/). Install **JDK 25** and
clone the repository:

```bash
git clone https://github.com/jamhour1g/Azkar-App.git
cd Azkar-App
./gradlew :app:run
```

On Windows, use `.\gradlew.bat :app:run` in PowerShell. The Gradle wrapper downloads Gradle and
dependencies on the first run. CI uses Zulu JDK 25. Release shrinking requires the JDK's `jmods`
directory; the Gradle checks report an error if it is missing.

## Local checks

Run the test suites and a CI-style build before submitting changes:

```bash
./gradlew test
CI=true ./gradlew build
```

`test` runs the module test suites. With `CI=true`, `build` checks formatting and static analysis;
without it, the build applies Spotless formatting locally. In PowerShell, set the environment first:

```powershell
$env:CI = "true"
.\gradlew.bat build
```

CI's full workflows, approval model, and installer boundaries are documented in [CI](ci.md).

## Capture screenshots

Refresh the download page's six screenshots in both themes:

```bash
./gradlew :app:captureDownloadScreenshots
```

The task renders the Compose UI with isolated, freshly seeded app data and a generic display name.
It copies the complete image set into `site/images/` after capture succeeds. Generated screenshots
are gitignored, so run this task before previewing the download page locally. Release CI captures and
uploads them in a job that runs alongside platform packaging, then includes the artifact in the
published site. The README uses the same hosted image files.

For a specific screen or reusable component, list available targets and render one:

```bash
./gradlew :app:runScreenshot -Pazkar.screenshot.list=true
./gradlew :app:runScreenshot -Pazkar.screenshot.screen=collection-detail
```

Render multiple targets and themes together:

```bash
./gradlew :app:runScreenshot -Pazkar.screenshot.targets=home,component.home-rail \
  -Pazkar.screenshot.themes=light,dark -Pazkar.screenshot.outDir=build/screenshots
```

The default single-image output is `app/build/screenshot.png`. Set `azkar.screenshot.width`,
`azkar.screenshot.height`, or `azkar.screenshot.locale=ar` to change the viewport or render the
Arabic layout.

## Project layout

| Path | Responsibility |
| --- | --- |
| `app/` | Desktop UI, startup, settings, prayer times, and reminders |
| `domain/` | Library models, repository contracts, and services |
| `data/` | H2 persistence, schema, repositories, and bundled seed data |
| `build-logic/` | Shared Gradle conventions and packaging tasks |
| `site/` | Download page, screenshots, and icons |
| `docs/` | Development and operational documentation |

The bundled library is maintained in `library.json`; its source, editing workflow, and attribution
are described in the [seed data notice](../data/src/main/resources/db/seed/SEED_DATA_NOTICE.md).
