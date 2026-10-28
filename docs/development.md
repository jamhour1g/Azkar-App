# Development

This guide covers running Azkar from source, checking changes locally, capturing screenshots, and
finding the main parts of the project. For branch protections and contributor CI approval, see
[CI](ci.md). For release builds, see [Releasing](releasing.md).

## Requirements and first run

Azkar is a Kotlin desktop application built with
[Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/). Install **JDK 25**
(Zulu is used in CI) and clone the repository:

```bash
git clone https://github.com/jamhour1g/Azkar-App.git
cd Azkar-App
./gradlew :app:run
```

On Windows, use `.\gradlew.bat :app:run` in PowerShell. The Gradle wrapper downloads Gradle and
dependencies on the first run. Gradle 9.8 also supports running on JDK 27; when JDK 25 is not
installed, Gradle can download a matching Java 25 compilation toolchain via Foojay. This requires
network access and an available JDK for your platform. Alternatively, install JDK 25 and select it
with `JAVA_HOME` (and as the Gradle JVM in your IDE). Release shrinking uses the Gradle JVM's
`jmods` directory, so use Zulu JDK 25 for release builds; the Gradle checks report an error if
`jmods` is missing.

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

## Gallery images

Render the download page and README images independently of tests:

```bash
./gradlew :app:captureDownloadScreenshots
```

`app/src/gallery/kotlin/com/azkar/Gallery.kt` renders six scenes in both themes directly into
`site/images/` using Roborazzi's Compose Desktop capture API. The generator uses Compose's UI-test
renderer without running a test suite. Its record mode and capture reports are isolated from the
regression references and reports. This command does not read or change regression baselines.
The release workflow uploads these freshly rendered images and publishes them to the site; the
README uses the same hosted files.

## Screenshot regression tests

Record or verify the references for the named UI tests:

```bash
./gradlew :app:recordRoborazziJvm
./gradlew :app:verifyRoborazziJvm
```

References live under `app/src/test/roborazzi/`, grouped by UI area (`home/`, `library/`,
`settings/`, `notifications/`, `components/`, and `dialogs/`); comparison reports are under
`app/build/reports/roborazzi/`. PR CI verifies these references independently of gallery generation.
The reference PNGs are checked into Git so CI has a stable expected image and each UI change includes
its visual diff in review. Only generated `site/images/screen-*.png` gallery images are ignored.
Review the image diffs before accepting new references with the record task.

An experimental Compose Desktop preview scan is available separately:

```bash
./gradlew :app:recordRoborazziJvm -Pazkar.previewScanner=true
./gradlew :app:verifyRoborazziJvm -Pazkar.previewScanner=true
```

It scans `com.azkar.ui`, runs only the generated JUnit 4 preview tests, and writes output under
`app/build/roborazzi-preview-scanner/`; it does not touch the reviewed regression baselines. Private
previews are included. Preview functions marked `@RoborazziDesktopPreviewExclude` launch native
desktop windows, which create a second semantics root; keep those previews for the IDE and capture
their content panes directly when they need Roborazzi coverage.

Standard previews use `@AzkarPreview` to combine `@Preview` with the fixed English, light Azkar theme
and opaque app background. Use `@AzkarDarkPreview`, `@AzkarArabicPreview`, `@AzkarCompactPreview`, or
`@AzkarMediumPreview` to inspect those variants. These annotations have fixed configurations because
Kotlin cannot forward custom annotation arguments into `@Preview`; one-off dimensions and settings
can still use direct `@Preview` plus `AzkarThemePreview(width = ..., height = ...)`.

For an AI-readable tree of visible text, controls, and element bounds, enable Roborazzi's UI tree
dump during verification:

```bash
./gradlew :app:verifyRoborazziJvm -Proborazzi.dumpUiTree=true
```

Roborazzi writes the JSON sidecars and annotated images under `app/build/outputs/roborazzi/`; these
generated inspection files are not regression references. PR CI generates these trees and uploads
them with comparison images and reports when verification fails.

`RoborazziScreenshotSuite` owns test cases, readiness assertions, and comparisons. Gallery generation
and regression tests share only the scene composables and frame in `app/src/screenshotFixtures/`.
Add a regression case by passing the intended composable and state in a test; add a published image
in `Gallery.kt`. Shared fixtures use a fixed home date and prayer time and in-memory library data.
Settings tests use temporary preferences and a mock HTTP client with suspend cleanup.
For desktop modal UI, keep the native `Dialog` host as a thin wrapper and extract its pane into a
content composable. Screenshot tests render that production pane directly: this covers its layout,
theme, and supplied state without creating a native window. It does not verify platform modality,
placement, or dimming.

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
