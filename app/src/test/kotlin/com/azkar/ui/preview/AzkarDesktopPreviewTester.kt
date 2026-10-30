package com.azkar.ui.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import com.azkar.testing.assertNoAzkarRecompositions
import com.azkar.testing.assertTrackedRecompositions
import com.azkar.testing.withDesktopDejavu
import com.azkar.ui.theme.AZKAR_PREVIEW_CAPTURE_TAG
import com.azkar.withLosslessWebp
import com.github.takahirom.roborazzi.DefaultDesktopComposePreviewTester
import com.github.takahirom.roborazzi.DesktopComposePreviewTester
import com.github.takahirom.roborazzi.DesktopPreviewTestParameter
import com.github.takahirom.roborazzi.advanceMainClockFor
import dejavu.resetRecompositionCounts
import dejavu.setTrackedContent
import io.github.takahirom.roborazzi.captureRoboImage

@OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class, InternalComposeUiApi::class)
private val screenshotTester =
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

@OptIn(
    com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class,
    InternalComposeUiApi::class,
    ExperimentalTestApi::class,
)
private val recompositionTester =
    DefaultDesktopComposePreviewTester(
        capturer =
            DefaultDesktopComposePreviewTester.Capturer { parameter ->
                var probe by mutableIntStateOf(0)
                setTrackedContent {
                    Box(Modifier.fillMaxSize()) {
                        if (parameter.preview.previewInfo.uiMode and 0x30 == 0x10) {
                            CompositionLocalProvider(
                                LocalSystemTheme provides SystemTheme.Light,
                            ) { parameter.content() }
                        } else {
                            parameter.content()
                        }
                        PreviewTrackingProbe(probe)
                    }
                }
                advanceMainClockFor(parameter)
                waitForIdle()
                resetRecompositionCounts()
                runOnIdle { probe++ }
                waitForIdle()
                onNodeWithTag("preview-tracking-probe")
                    .assertTrackedRecompositions(exactly = 1, expectedFunction = "PreviewTrackingProbe")
                resetRecompositionCounts()
                mainClock.autoAdvance = false
                mainClock.advanceTimeBy(64)
                waitForIdle()
                assertNoAzkarRecompositions()
            },
    )

@Composable
private fun PreviewTrackingProbe(value: Int) {
    Text(value.toString(), modifier = Modifier.testTag("preview-tracking-probe"))
}

@OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
class AzkarDesktopPreviewTester : DesktopComposePreviewTester by screenshotTester {
    override fun test(testParameter: DesktopPreviewTestParameter) {
        screenshotTester.test(testParameter)
        withDesktopDejavu { recompositionTester.test(testParameter) }
    }
}
