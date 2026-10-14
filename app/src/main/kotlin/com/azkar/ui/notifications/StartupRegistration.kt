package com.azkar.ui.notifications

import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

/** Registers the packaged desktop launcher with the current user's login session. */
class StartupRegistration {
    fun setEnabled(enabled: Boolean): Result<Unit> =
        runCatching {
            val path = registrationPath()
            if (enabled) {
                val launcher = findLauncher() ?: error("Start-on-login is available from the installed desktop app.")
                Files.createDirectories(path.parent)
                Files.writeString(path, registrationContents(launcher))
            } else {
                Files.deleteIfExists(path)
            }
        }

    private fun registrationPath(): Path =
        when (DesktopPlatform.current) {
            DesktopPlatform.WINDOWS -> {
                val appData =
                    System.getenv("APPDATA")?.takeIf(String::isNotBlank)
                        ?: error("Windows startup folder is unavailable")
                Path.of(
                    appData,
                    "Microsoft/Windows/Start Menu/Programs/Startup/Azkar.cmd",
                )
            }

            DesktopPlatform.MACOS ->
                Path.of(
                    System.getProperty("user.home"),
                    "Library/LaunchAgents/com.azkar.app.plist",
                )

            DesktopPlatform.LINUX -> Path.of(System.getProperty("user.home"), ".config/autostart/azkar.desktop")
        }

    private fun registrationContents(launcher: Path): String =
        when (DesktopPlatform.current) {
            DesktopPlatform.WINDOWS -> "@echo off\r\nstart \"\" \"$launcher\" --start-hidden\r\n"
            DesktopPlatform.MACOS -> macOsPlist(launcher)
            DesktopPlatform.LINUX -> linuxDesktopEntry(launcher)
        }

    /** JPackage launches a bundled JVM, so walk up from its executable to the native launcher. */
    private fun findLauncher(): Path? {
        val processPath = processPath() ?: return null
        val directLauncher = processPath.takeUnless(::isJvmLauncher)
        return listOfNotNull(directLauncher)
            .asSequence()
            .plus(launcherCandidates(processPath.parent))
            .plus(jpackageLauncherCandidates())
            .firstOrNull { Files.isRegularFile(it) && Files.isExecutable(it) }
    }

    private fun processPath(): Path? =
        ProcessHandle
            .current()
            .info()
            .command()
            .orElse(null)
            ?.let { Path.of(it).toAbsolutePath().normalize() }

    private fun isJvmLauncher(path: Path): Boolean =
        path.fileName?.toString()?.lowercase(Locale.ROOT) in
            setOf("java", "javaw", "java.exe", "javaw.exe")

    private fun jpackageLauncherCandidates(): Sequence<Path> =
        System
            .getProperty("jpackage.app-path")
            ?.let { Path.of(it).toAbsolutePath().normalize() }
            ?.let(::launcherCandidates)
            .orEmpty()

    private fun launcherCandidates(start: Path?): Sequence<Path> =
        start
            ?.let { first ->
                generateSequence(first) { it.parent }
                    .mapNotNull(::launcherAt)
            }.orEmpty()

    private fun launcherAt(ancestor: Path): Path? =
        when (DesktopPlatform.current) {
            DesktopPlatform.WINDOWS -> ancestor.resolve("Azkar.exe")
            DesktopPlatform.MACOS ->
                if (ancestor.fileName?.toString()?.endsWith(".app") == true) {
                    ancestor.resolve("Contents/MacOS/Azkar")
                } else {
                    null
                }

            DesktopPlatform.LINUX -> ancestor.resolve("bin/azkar")
        }

    private fun linuxDesktopEntry(launcher: Path): String {
        val escaped =
            launcher
                .toString()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("`", "\\`")
                .replace("$", "\\$")
        return """[Desktop Entry]
Type=Application
Name=Azkar
Exec="$escaped" --start-hidden
Terminal=false
X-GNOME-Autostart-enabled=true
"""
    }

    private fun macOsPlist(launcher: Path): String =
        """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
<key>Label</key><string>com.azkar.app</string>
<key>ProgramArguments</key><array>
<string>${xml(launcher.toString())}</string>
<string>--start-hidden</string></array>
<key>RunAtLoad</key><true/>
</dict></plist>
"""

    private fun xml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
