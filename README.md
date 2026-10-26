# Azkar

A desktop companion for daily adhkar and prayer times. Read, keep your place, and receive
reminders from the passages you choose—on Windows, macOS, and Linux.

**[Download Azkar](https://jamhour1g.github.io/Azkar-App/download.html)** ·
[Browse releases](https://github.com/jamhour1g/Azkar-App/releases) ·
[Report an issue](https://github.com/jamhour1g/Azkar-App/issues)

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://jamhour1g.github.io/Azkar-App/images/screen-home-dark.png">
  <source media="(prefers-color-scheme: light)" srcset="https://jamhour1g.github.io/Azkar-App/images/screen-home-light.png">
  <img src="https://jamhour1g.github.io/Azkar-App/images/screen-home-light.png" alt="Azkar Home with a daily remembrance, today's prayer times, and a countdown to the next prayer" width="1440" height="900">
</picture>

## Make room for remembrance in your day

- **Find the adhkar you need.** Explore the bundled collections, browse topics, or search for a passage.
- **Understand what you read.** Read Arabic alongside translations, with explanations and source
  references where available.
- **Pick up where you left off.** Recite a collection one passage at a time, track repetitions, and
  resume your progress later.
- **Build your own library.** Add remembrances, arrange collections, choose covers, organize tags,
  and keep favorites close. Pin regular collections or show them on Home.
- **Receive reminders from your library.** Choose collections or individual passages, set a cadence
  around your day, and pause reminders when you need to focus. Reminder cards can stay available
  while the main window is closed.
- **Keep track of prayer times.** See today's schedule and the next-prayer countdown on Home, with
  individual prayer alerts and location settings.
- **Read comfortably.** English and Arabic interfaces, right-to-left layout, light and dark themes,
  and adjustable text size and typeface.

## A closer look

<table>
  <tr>
    <td width="50%">
      <strong>Find your daily adhkar</strong><br>
      Browse collections and topics, or search for a passage.
      <picture>
        <source media="(prefers-color-scheme: dark)" srcset="https://jamhour1g.github.io/Azkar-App/images/screen-library-dark.png">
        <img src="https://jamhour1g.github.io/Azkar-App/images/screen-library-light.png" alt="Azkar library with collections, topic filters, and search">
      </picture>
    </td>
    <td width="50%">
      <strong>Read with context</strong><br>
      Keep the Arabic text, meaning, and references together.
      <picture>
        <source media="(prefers-color-scheme: dark)" srcset="https://jamhour1g.github.io/Azkar-App/images/screen-reader-dark.png">
        <img src="https://jamhour1g.github.io/Azkar-App/images/screen-reader-light.png" alt="A remembrance reader with Arabic text, translation, and source details">
      </picture>
    </td>
  </tr>
  <tr>
    <td width="50%">
      <strong>Keep your place</strong><br>
      Follow a recitation session and track your progress.
      <picture>
        <source media="(prefers-color-scheme: dark)" srcset="https://jamhour1g.github.io/Azkar-App/images/screen-session-dark.png">
        <img src="https://jamhour1g.github.io/Azkar-App/images/screen-session-light.png" alt="An Azkar recitation session with passage navigation and progress">
      </picture>
    </td>
    <td width="50%">
      <strong>A reminder during your day</strong><br>
      Read a passage on your desktop and save it as a favorite.
      <picture>
        <source media="(prefers-color-scheme: dark)" srcset="https://jamhour1g.github.io/Azkar-App/images/screen-reminder-dark.png">
        <img src="https://jamhour1g.github.io/Azkar-App/images/screen-reminder-light.png" alt="An Azkar reminder card with Arabic text, favorite and dismiss controls">
      </picture>
    </td>
  </tr>
</table>

Screenshots follow your light or dark appearance. The
[download page](https://jamhour1g.github.io/Azkar-App/download.html#showcase-title) includes the full gallery.

## Install

Choose your installer on the **[download page](https://jamhour1g.github.io/Azkar-App/download.html)**,
or browse the [GitHub releases](https://github.com/jamhour1g/Azkar-App/releases).

| Platform | Installer |
| --- | --- |
| Windows | `.exe` installer |
| macOS | `.dmg` for Apple silicon or Intel |
| Linux | `.deb` for Debian/Ubuntu, `.rpm` for Fedora, or AppImage for other distributions |

For a one-time Linux install, open the downloaded DEB or RPM in your software installer.
Optional signed APT and RPM repositories for automatic updates are explained on the download page.
See [release documentation](docs/releasing.md) for Stable and Dev channels and packaging details.

## Run from source

Azkar is built with Kotlin and [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/).
Install **JDK 25**, then run:

```bash
git clone https://github.com/jamhour1g/Azkar-App.git
cd Azkar-App
./gradlew :app:run
```

On Windows, use `.\gradlew.bat :app:run`. The Gradle wrapper downloads Gradle and dependencies on the
first run. Zulu JDK 25 is used in CI; release shrinking also needs its `jmods` directory.

## Contribute

Bug reports and focused improvements are welcome. Include your operating system, app version, and
steps to reproduce when reporting a problem. For a UI change, include screenshots of the affected
light and dark appearances.

```bash
./gradlew test
CI=true ./gradlew build
```

`test` runs the module test suites. With `CI=true`, `build` also checks formatting and static analysis;
without it, the build applies Spotless formatting locally. In PowerShell, use `$env:CI = "true"`
before `.\gradlew.bat build`. See [CI documentation](docs/ci.md) for the complete workflow.

### Capture screenshots

Refresh all six download page screenshots in both themes:

```bash
./gradlew :app:captureDownloadScreenshots
```

The task renders the actual Compose UI with isolated, freshly seeded app data and a generic display
name. It copies the complete image set into `site/images/` after capture succeeds. Generated
screenshots are gitignored; run this task before previewing the download page locally. Release CI
regenerates them before deploying the page.

For a specific screen or reusable component:

```bash
./gradlew :app:runScreenshot -Pazkar.screenshot.list=true
./gradlew :app:runScreenshot -Pazkar.screenshot.screen=collection-detail
./gradlew :app:runScreenshot -Pazkar.screenshot.targets=home,component.home-rail \
  -Pazkar.screenshot.themes=light,dark -Pazkar.screenshot.outDir=build/screenshots
```

The default single-image output is `app/build/screenshot.png`. Use `azkar.screenshot.width`,
`azkar.screenshot.height`, and `azkar.screenshot.locale=ar` for another viewport or Arabic layout.

### Find your way around

| Path | What lives here |
| --- | --- |
| `app/` | Desktop UI, app startup, settings, prayer times, and reminders |
| `domain/` | Library models, repository contracts, and services |
| `data/` | H2 persistence, schema, repositories, and bundled seed data |
| `build-logic/` | Shared Gradle conventions and packaging tasks |
| `site/` | Download page and its screenshots and icons |
| `docs/` | CI, releases, logging, and notification documentation |

Useful references: [release process](docs/releasing.md), [logging](docs/logging.md), and
[notification overlay](docs/notification-overlay.md).

## Credits and license

This is the Kotlin/Compose Desktop rewrite of the original
[JavaFX Azkar app](https://github.com/AbdelrahmanBayoumi/Azkar-App).

The application is licensed under the [MIT License](LICENSE). The bundled remembrance library is
based on the Muslim Data Azkars dataset and retains its separate attribution and license; see the
[seed data notice](data/src/main/resources/db/seed/SEED_DATA_NOTICE.md) for its source and transformations.
