# GNOME panel development

Run these Gradle tasks from the repository root with GNOME Shell installed:

- `./gradlew :app:runGnomePanel` opens Azkar and the extension in a nested session from a Wayland desktop.
- `./gradlew :app:captureGnomePanelPreviews` captures the panel menus in an isolated session and writes the images to [`previews/`](../../previews/).
