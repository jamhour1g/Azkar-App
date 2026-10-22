# Azkar

A desktop Islamic *azkar* app (morning/evening remembrances, prayers, tasbih)
built with [Kotlin Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
for Windows, macOS and Linux. It is the Compose Multiplatform rewrite of the
JavaFX original in
[AbdelrahmanBayoumi/Azkar-App](https://github.com/AbdelrahmanBayoumi/Azkar-App).
Licensed MIT.

## Download

Grab the latest build from the
[download page](https://jamhour1g.github.io/Azkar-App/download.html) — it picks the
package for the platform you are on and explains how to install it:

- **Windows** — MSIX installer; updates install automatically in the background.
- **macOS** — Sparkle-based updater prompts when a new build is published.
- **Linux** — `.deb` package; updates arrive via `apt upgrade` (signed apt repo).

Releases are cut by pushing a `v*` tag that matches the Gradle version — see
[docs/releasing.md](docs/releasing.md).

> **macOS note:** the app is self-signed (no Apple Developer ID yet), so
> Gatekeeper asks on first launch: right-click → *Open*. Update signatures are
> still verified end-to-end.

## Running from source

```bash
./gradlew :app:run          # JDK 25 (toolchain-pinned)
```

## Continuous integration

The three platforms are built on demand: run the `CI` workflow from the Actions tab
when you want them checked. A second pipeline runs formatting, static analysis and
the test suite once a maintainer allows it with the `run-quality` label. See
[docs/ci.md](docs/ci.md).

See [docs/logging.md](docs/logging.md) and
[docs/notification-overlay.md](docs/notification-overlay.md) for internal
documentation.

---

Packaged with [Hydraulic Conveyor](https://conveyor.hydraulic.dev) — native
installers and auto-updates from one config.
