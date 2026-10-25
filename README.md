# Azkar

Azkar is a desktop companion for daily adhkar and prayer times, built with Kotlin and
[Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/) for Windows, macOS,
and Linux. This repository contains the desktop rewrite of the original
[JavaFX app](https://github.com/AbdelrahmanBayoumi/Azkar-App).

## What you can do

- Read a bundled library of remembrances with Arabic text, translations, explanations, and sources.
- Create collections and tags, add your own remembrances, mark favorites, and track recitation
  progress. Collections can be imported and exported.
- See prayer times for a chosen location and calculation method, with configurable prayer alerts.
- Schedule on-screen remembrance reminders from selected library content.
- Use the interface in English or Arabic, with right-to-left layout, light and dark themes, and
  adjustable text settings.

The bundled seed library is based on the Muslim Data Azkars dataset. Its source, license, and
transformation details are recorded in the [seed data notice](data/src/main/resources/db/seed/SEED_DATA_NOTICE.md).

## Download

Get the latest stable installer from the [download page](https://jamhour1g.github.io/Azkar-App/download.html).
The page links to the supported installers and Linux repository setup instructions.

| Platform | Package | Updates |
| --- | --- | --- |
| Windows | NSIS `.exe` | Nucleus in-app updates |
| macOS | DMG installer | Nucleus replaces the app from a ZIP update |
| Linux | `.deb`, `.rpm`, AppImage | Signed apt/dnf repositories; Nucleus updates AppImage |

Release and Dev channel details are in [docs/releasing.md](docs/releasing.md).

## Run from source

Install a **JDK 25**. The Gradle wrapper downloads the required Gradle version and dependencies on
the first run.

```bash
git clone https://github.com/jamhour1g/Azkar-App.git
cd Azkar-App
./gradlew :app:run
```

On Windows, run `.\gradlew.bat :app:run` from the project directory.

## Build and verification

```bash
./gradlew test
CI=true ./gradlew build
```

`test` runs the module test suites. `build` also runs static analysis and formatting checks when
`CI` is set. In PowerShell, set `$env:CI = "true"` and run `.\gradlew.bat build`.
Without `CI`, the build applies Spotless formatting locally. Release shrinking uses
ProGuard and needs a JDK 25 distribution that includes `jmods` (Zulu 25 is used in CI); see
[docs/ci.md](docs/ci.md).

To render a screen to `app/build/screenshot.png` without opening the app:

```bash
./gradlew :app:runScreenshot -Pazkar.screenshot.screen=collections
```

The screenshot task also accepts `home`, `settings`, `collection-detail`, and options such as
`-Pazkar.screenshot.locale=ar` or `-Pazkar.screenshot.dark=true`.

## Project structure

| Module or directory | Responsibility |
| --- | --- |
| `app/` | Compose Desktop UI, app startup, settings, prayer times, and reminders |
| `domain/` | Library models, repository interfaces, and domain services |
| `data/` | H2 persistence, schema setup, repositories, and bundled seed data |
| `build-logic/` | Shared Gradle convention plugins and packaging tasks |
| `docs/` | CI, release, logging, and notification overlay documentation |

The main CI workflow runs for pull requests targeting `dev` or `production`; Windows and macOS
builds run for production pull requests and manual CI runs. Stable releases are published from
version tags on `production`, while validated pushes to `dev` publish versioned prereleases. See
[docs/ci.md](docs/ci.md) and [docs/releasing.md](docs/releasing.md).

For implementation details, see [docs/logging.md](docs/logging.md) and
[docs/notification-overlay.md](docs/notification-overlay.md).

## License

The application is licensed under the [MIT License](LICENSE). The bundled seed dataset retains the
separate attribution and license described in its [provenance notice](data/src/main/resources/db/seed/SEED_DATA_NOTICE.md).

---
