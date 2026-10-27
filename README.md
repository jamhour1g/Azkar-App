# Azkar

A desktop companion for daily adhkar and prayer times. Read, keep your place, and receive
reminders from the passages you choose—on Windows, macOS, and Linux.

**[Download Azkar](https://jamhour1g.github.io/Azkar-App/download.html)** ·
[Browse releases](https://github.com/jamhour1g/Azkar-App/releases) ·
[Report an issue](https://github.com/jamhour1g/Azkar-App/issues)

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://jamhour1g.github.io/Azkar-App/images/screen-home-dark.webp">
  <source media="(prefers-color-scheme: light)" srcset="https://jamhour1g.github.io/Azkar-App/images/screen-home-light.webp">
  <img src="https://jamhour1g.github.io/Azkar-App/images/screen-home-light.webp" alt="Azkar Home with a daily remembrance, today's prayer times, and a countdown to the next prayer" width="1440" height="900">
</picture>

## Make room for remembrance in your day

- **Find the adhkar you need.** Explore the bundled collections, browse topics, or search for a passage.
- **Understand what you read.** Read Arabic alongside translations, with explanations and source references where available.
- **Pick up where you left off.** Recite a collection one passage at a time, track repetitions, and resume your progress later.
- **Build your own library.** Add remembrances, arrange collections, choose covers, organize tags, and keep favorites close.
- **Receive reminders from your library.** Choose collections or passages, set a cadence, and pause reminders when you need to focus.
- **Keep track of prayer times.** See today's schedule and the next-prayer countdown, with individual prayer alerts and location settings.
- **Read comfortably.** Use English or Arabic, right-to-left layout, light or dark themes, and adjustable text size and typeface.

## A closer look

<table>
  <tr>
    <td width="50%">
      <strong>Find your daily adhkar</strong><br>
      Browse collections and topics, or search for a passage.
      <picture>
        <source media="(prefers-color-scheme: dark)" srcset="https://jamhour1g.github.io/Azkar-App/images/screen-library-dark.webp">
        <img src="https://jamhour1g.github.io/Azkar-App/images/screen-library-light.webp" alt="Azkar library with collections, topic filters, and search">
      </picture>
    </td>
    <td width="50%">
      <strong>Read with context</strong><br>
      Keep the Arabic text, meaning, and references together.
      <picture>
        <source media="(prefers-color-scheme: dark)" srcset="https://jamhour1g.github.io/Azkar-App/images/screen-reader-dark.webp">
        <img src="https://jamhour1g.github.io/Azkar-App/images/screen-reader-light.webp" alt="A remembrance reader with Arabic text, translation, and source details">
      </picture>
    </td>
  </tr>
  <tr>
    <td width="50%">
      <strong>Keep your place</strong><br>
      Follow a recitation session and track your progress.
      <picture>
        <source media="(prefers-color-scheme: dark)" srcset="https://jamhour1g.github.io/Azkar-App/images/screen-session-dark.webp">
        <img src="https://jamhour1g.github.io/Azkar-App/images/screen-session-light.webp" alt="An Azkar recitation session with passage navigation and progress">
      </picture>
    </td>
    <td width="50%">
      <strong>A reminder during your day</strong><br>
      Read a passage on your desktop and save it as a favorite.
      <picture>
        <source media="(prefers-color-scheme: dark)" srcset="https://jamhour1g.github.io/Azkar-App/images/screen-reminder-dark.webp">
        <img src="https://jamhour1g.github.io/Azkar-App/images/screen-reminder-light.webp" alt="An Azkar reminder card with Arabic text, favorite and dismiss controls">
      </picture>
    </td>
  </tr>
</table>

Screenshots follow your light or dark appearance. The
[download page](https://jamhour1g.github.io/Azkar-App/download.html#showcase-title) has the full gallery.

## Install

Choose an installer on the **[download page](https://jamhour1g.github.io/Azkar-App/download.html)**,
or browse [GitHub releases](https://github.com/jamhour1g/Azkar-App/releases).

| Platform | Available formats |
| --- | --- |
| Windows | `.exe` installer |
| macOS | `.dmg` for Apple silicon or Intel |
| Linux | `.deb`, `.rpm`, or AppImage |

For Linux package repositories, update channels, and installation guidance, see the download page.
For release channels and packaging details, see the [release guide](docs/releasing.md).

## Contribute

Bug reports and focused improvements are welcome. Include your operating system, app version, and
steps to reproduce. For UI changes, update the Roborazzi light and dark baselines; their image diffs
are included in the pull request. CI comparison artifacts are available when verification fails.

Start with the [development guide](docs/development.md) for setup, local checks, screenshots, and
the project layout. Read [CI documentation](docs/ci.md) before opening a pull request.

## Project documentation

- [Development setup and project layout](docs/development.md)
- [Continuous integration and contributor approval](docs/ci.md)
- [Release process and packaging](docs/releasing.md)
- [Installed release validation](docs/release-validation.md)
- [Logging conventions](docs/logging.md)
- [Notification overlay behavior](docs/notification-overlay.md)
- [Bundled seed data notice](data/src/main/resources/db/seed/SEED_DATA_NOTICE.md)

## License

The application is licensed under the [MIT License](LICENSE). The bundled remembrance library has
separate attribution and licensing; see the
[seed data notice](data/src/main/resources/db/seed/SEED_DATA_NOTICE.md).
