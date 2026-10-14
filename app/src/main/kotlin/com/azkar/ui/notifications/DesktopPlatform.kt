package com.azkar.ui.notifications

import java.util.Locale

/**
 * Which desktop the process is running on, answered once.
 *
 * Two unrelated pieces of this app need the same answer and previously each asked for itself: the
 * start-on-login registration writes a different file per platform, and the close-to-tray decision
 * has to know whether a tray can exist at all. A single reading of `os.name` keeps them from
 * disagreeing — particularly about what "not Windows and not macOS" means, which is answered as
 * Linux rather than as an unknown, because that is the only reading either of them can act on.
 */
internal enum class DesktopPlatform {
    WINDOWS,
    MACOS,
    LINUX,
    ;

    companion object {
        val current: DesktopPlatform by lazy {
            val name = System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT)
            when {
                "win" in name -> WINDOWS
                "mac" in name -> MACOS
                else -> LINUX
            }
        }
    }
}
