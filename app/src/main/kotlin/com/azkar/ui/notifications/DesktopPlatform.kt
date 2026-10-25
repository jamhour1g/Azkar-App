package com.azkar.ui.notifications

import java.util.Locale

/**
 * Which desktop the process is running on, answered once.
 *
 * Two unrelated pieces of this app need the same answer and previously each asked for itself: the
 * legacy start-on-login cleanup has different paths per platform, and the close-to-tray decision
 * has to know whether a tray can exist at all. A single reading of `os.name` keeps them from
 * disagreeing. Unsupported operating systems stay unknown so platform-specific actions can fail
 * closed instead of accidentally using Linux paths.
 */
internal enum class DesktopPlatform {
    WINDOWS,
    MACOS,
    LINUX,
    UNKNOWN,
    ;

    companion object {
        val current: DesktopPlatform by lazy {
            desktopPlatform(System.getProperty("os.name").orEmpty())
        }
    }
}

internal fun desktopPlatform(osName: String): DesktopPlatform {
    val name = osName.lowercase(Locale.ROOT)
    return when {
        name.startsWith("mac") || name.startsWith("darwin") -> DesktopPlatform.MACOS
        name.startsWith("windows") || name.startsWith("win") -> DesktopPlatform.WINDOWS
        name.startsWith("linux") -> DesktopPlatform.LINUX
        else -> DesktopPlatform.UNKNOWN
    }
}

internal fun trayAvailable(
    headless: Boolean,
    platform: DesktopPlatform,
    sessionBusAddress: String?,
): Boolean =
    !headless &&
        when (platform) {
            DesktopPlatform.WINDOWS, DesktopPlatform.MACOS -> true
            DesktopPlatform.LINUX -> !sessionBusAddress.isNullOrBlank()
            DesktopPlatform.UNKNOWN -> false
        }
