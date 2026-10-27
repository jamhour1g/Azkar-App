package com.azkar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.tooling.preview.PreviewWrapperProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.azkar.i18n.ARABIC
import com.azkar.i18n.ENGLISH
import java.util.Locale

/** Standard English, light-theme preview. */
@Preview
@PreviewWrapper(AzkarPreviewWrapperProvider::class)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class AzkarPreview

/** Light and dark, English and Arabic, in portrait and landscape windows. */
@Preview(name = "Light EN portrait", locale = "en", widthDp = 720, heightDp = 1100, uiMode = 0x10)
@Preview(name = "Light EN landscape", locale = "en", widthDp = 1100, heightDp = 720, uiMode = 0x10)
@Preview(name = "Light AR portrait", locale = "ar", widthDp = 720, heightDp = 1100, uiMode = 0x10)
@Preview(name = "Light AR landscape", locale = "ar", widthDp = 1100, heightDp = 720, uiMode = 0x10)
@Preview(name = "Dark EN portrait", locale = "en", widthDp = 720, heightDp = 1100, uiMode = 0x20)
@Preview(name = "Dark EN landscape", locale = "en", widthDp = 1100, heightDp = 720, uiMode = 0x20)
@Preview(name = "Dark AR portrait", locale = "ar", widthDp = 720, heightDp = 1100, uiMode = 0x20)
@Preview(name = "Dark AR landscape", locale = "ar", widthDp = 1100, heightDp = 720, uiMode = 0x20)
@PreviewWrapper(AzkarVariantsPreviewWrapperProvider::class)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class AzkarVariantsPreview

/** The same variants on smaller canvases for individual components. */
@Preview(name = "Light EN portrait", locale = "en", widthDp = 240, heightDp = 360, uiMode = 0x10)
@Preview(name = "Light EN landscape", locale = "en", widthDp = 360, heightDp = 240, uiMode = 0x10)
@Preview(name = "Light AR portrait", locale = "ar", widthDp = 240, heightDp = 360, uiMode = 0x10)
@Preview(name = "Light AR landscape", locale = "ar", widthDp = 360, heightDp = 240, uiMode = 0x10)
@Preview(name = "Dark EN portrait", locale = "en", widthDp = 240, heightDp = 360, uiMode = 0x20)
@Preview(name = "Dark EN landscape", locale = "en", widthDp = 360, heightDp = 240, uiMode = 0x20)
@Preview(name = "Dark AR portrait", locale = "ar", widthDp = 240, heightDp = 360, uiMode = 0x20)
@Preview(name = "Dark AR landscape", locale = "ar", widthDp = 360, heightDp = 240, uiMode = 0x20)
@PreviewWrapper(AzkarVariantsPreviewWrapperProvider::class)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class AzkarComponentVariantsPreview

class AzkarVariantsPreviewWrapperProvider : PreviewWrapperProvider {
    @Composable
    override fun Wrap(content: @Composable () -> Unit) {
        val darkTheme = isSystemInDarkTheme()
        val locale = Locale.getDefault()
        BoxWithConstraints(Modifier.fillMaxSize()) {
            AzkarPreviewSurface(darkTheme, locale, maxWidth, maxHeight) {
                Box(Modifier.fillMaxSize()) { content() }
            }
        }
    }
}

/** Tall desktop canvas for full-screen previews that need to show content below the first fold. */
@Preview(name = "Full screen", widthDp = 1440, heightDp = 1500)
@PreviewWrapper(AzkarHomeScreenPreviewWrapperProvider::class)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class AzkarHomeScreenPreview

/** Wide content preview for layouts whose fixture is wider than the scanner's default surface. */
@Preview(name = "Wide", widthDp = 1064, heightDp = 220)
@PreviewWrapper(AzkarPreviewWrapperProvider::class)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class AzkarWidePreview

/** Dark-theme preview. */
@Preview(name = "Dark", uiMode = 0x20)
@PreviewWrapper(AzkarDarkPreviewWrapperProvider::class)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class AzkarDarkPreview

/** Arabic, right-to-left preview. */
@Preview(name = "Arabic", locale = "ar")
@PreviewWrapper(AzkarArabicPreviewWrapperProvider::class)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class AzkarArabicPreview

/** Compact-width preview using the app's compact layout breakpoint. */
@Preview(name = "Compact", widthDp = 720, heightDp = 800)
@PreviewWrapper(AzkarCompactPreviewWrapperProvider::class)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class AzkarCompactPreview

/** Medium-width preview using the app's medium layout breakpoint. */
@Preview(name = "Medium", widthDp = 1100, heightDp = 800)
@PreviewWrapper(AzkarMediumPreviewWrapperProvider::class)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class AzkarMediumPreview

class AzkarDarkPreviewWrapperProvider : PreviewWrapperProvider {
    @Composable
    override fun Wrap(content: @Composable () -> Unit) = AzkarPreviewSurface(darkTheme = true, content = content)
}

class AzkarArabicPreviewWrapperProvider : PreviewWrapperProvider {
    @Composable
    override fun Wrap(content: @Composable () -> Unit) = AzkarPreviewSurface(uiLocale = ARABIC, content = content)
}

class AzkarCompactPreviewWrapperProvider : PreviewWrapperProvider {
    @Composable
    override fun Wrap(content: @Composable () -> Unit) =
        AzkarPreviewSurface(width = 720.dp, height = 800.dp, content = content)
}

class AzkarMediumPreviewWrapperProvider : PreviewWrapperProvider {
    @Composable
    override fun Wrap(content: @Composable () -> Unit) =
        AzkarPreviewSurface(width = 1100.dp, height = 800.dp, content = content)
}

class AzkarHomeScreenPreviewWrapperProvider : PreviewWrapperProvider {
    @Composable
    override fun Wrap(content: @Composable () -> Unit) =
        AzkarPreviewSurface(width = 1440.dp, height = 1500.dp, content = content)
}

@Composable
internal fun AzkarPreviewSurface(
    darkTheme: Boolean = false,
    uiLocale: Locale = ENGLISH,
    width: Dp = DEFAULT_WINDOW_WIDTH,
    height: Dp = DEFAULT_WINDOW_HEIGHT,
    content: @Composable () -> Unit,
) {
    AzkarThemePreview(darkTheme = darkTheme, uiLocale = uiLocale, width = width, height = height, content = content)
}
