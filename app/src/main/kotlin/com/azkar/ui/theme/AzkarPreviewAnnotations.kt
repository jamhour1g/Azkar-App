package com.azkar.ui.theme

import androidx.compose.runtime.Composable
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
