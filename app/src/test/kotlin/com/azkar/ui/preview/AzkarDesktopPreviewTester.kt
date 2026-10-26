package com.azkar.ui.preview

import androidx.compose.ui.test.onNodeWithTag
import com.azkar.ui.theme.AZKAR_PREVIEW_CAPTURE_TAG
import com.github.takahirom.roborazzi.DefaultDesktopComposePreviewTester
import com.github.takahirom.roborazzi.DesktopComposePreviewTester
import com.github.takahirom.roborazzi.advanceMainClockFor
import io.github.takahirom.roborazzi.captureRoboImage

@OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
class AzkarDesktopPreviewTester :
    DesktopComposePreviewTester by
    DefaultDesktopComposePreviewTester(
        capturer =
            DefaultDesktopComposePreviewTester.Capturer { parameter ->
                setContent(parameter.content)
                advanceMainClockFor(parameter)
                onNodeWithTag(AZKAR_PREVIEW_CAPTURE_TAG, useUnmergedTree = true)
                    .captureRoboImage(parameter.filePath, parameter.roborazziOptions)
            },
    )
