package com.azkar.ui.preview

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.test.onNodeWithTag
import com.azkar.ui.theme.AZKAR_PREVIEW_CAPTURE_TAG
import com.azkar.withLosslessWebp
import com.github.takahirom.roborazzi.DefaultDesktopComposePreviewTester
import com.github.takahirom.roborazzi.DesktopComposePreviewTester
import com.github.takahirom.roborazzi.advanceMainClockFor
import io.github.takahirom.roborazzi.captureRoboImage

@OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class, InternalComposeUiApi::class)
class AzkarDesktopPreviewTester :
    DesktopComposePreviewTester by
    DefaultDesktopComposePreviewTester(
        capturer =
            DefaultDesktopComposePreviewTester.Capturer { parameter ->
                setContent {
                    // Roborazzi supplies dark mode, but leaves explicit day mode to the host theme.
                    if (parameter.preview.previewInfo.uiMode and 0x30 == 0x10) {
                        CompositionLocalProvider(LocalSystemTheme provides SystemTheme.Light) { parameter.content() }
                    } else {
                        parameter.content()
                    }
                }
                advanceMainClockFor(parameter)
                onNodeWithTag(AZKAR_PREVIEW_CAPTURE_TAG, useUnmergedTree = true)
                    .captureRoboImage(parameter.filePath, parameter.roborazziOptions.withLosslessWebp())
            },
    )
