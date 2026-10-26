package com.azkar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.azkar.i18n.activateLocale
import com.azkar.ui.library.CollectionsScreen
import com.azkar.ui.shell.AppLayout
import com.azkar.ui.shell.AzkarDestination
import com.azkar.ui.shell.HeaderCallbacks
import io.github.takahirom.roborazzi.captureRoboImage
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

fun main(args: Array<String>) {
    val output = Path.of(args.single())
    Files.createDirectories(output)
    Locale.ENGLISH.activateLocale()
    System.setProperty("user.name", "Azkar")
    listOf(false, true).forEach { dark ->
        val theme = if (dark) "dark" else "light"
        galleryImage(output.resolve("screen-home-$theme.png"), dark) { ready ->
            AppLayout(AzkarDestination.Home, HeaderCallbacks({}, {}, {})) { HomeShot(onReady = ready) }
        }
        galleryImage(output.resolve("screen-library-$theme.png"), dark) { ready ->
            LibraryShot(onReady = ready) { CollectionsScreen({}, {}, {}, { _, _, _ -> }, catalogue = it) }
        }
        galleryImage(output.resolve("screen-collection-$theme.png"), dark) { ready ->
            LibraryShot(onReady = ready) { CollectionDetailShot(it) }
        }
        galleryImage(output.resolve("screen-session-$theme.png"), dark) { ready ->
            LibraryShot(onReady = ready) { CollectionSessionShot(it) }
        }
        galleryImage(output.resolve("screen-reader-$theme.png"), dark) { ready ->
            LibraryShot(onReady = ready) { RemembranceShot(it) }
        }
        galleryImage(output.resolve("screen-reminder-$theme.png"), dark, density = 3f) { ready ->
            SideEffect(ready)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(Modifier.width(420.dp).padding(16.dp)) { PassageReminderShot() }
            }
        }
    }
}

@OptIn(ExperimentalTestApi::class)
private fun galleryImage(
    file: Path,
    dark: Boolean,
    density: Float = 1f,
    content: @Composable (onReady: () -> Unit) -> Unit,
) {
    val ready = AtomicBoolean(false)
    runDesktopComposeUiTest(width = 1440, height = 900) {
        setContent { ScreenshotFrame(dark, density) { content { ready.set(true) } } }
        waitUntil(timeoutMillis = 30_000) { ready.get() }
        waitForIdle()
        onRoot().captureRoboImage(filePath = file.toString())
    }
}
