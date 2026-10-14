package com.azkar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import de.infix.testBalloon.framework.core.testSuite
import org.assertj.core.api.Assertions.assertThat
import java.util.Locale

/**
 * The inverse pair, which is the one seam [toMaterialScheme] used to leave open.
 *
 * These two slots have no business being named by an individual control — they exist so that
 * Material's own defaults for anything drawn *on top of* the page, tooltips above all, resolve to the
 * app's palette instead of the library's. A tooltip that comes out as Material's baseline lilac in a
 * blue-and-amber app is not a bug in the tooltip; it is a hole in the projection, and it is invisible
 * from the call site because the call site never named a colour to get wrong.
 *
 * So the assertion is on the scheme rather than on a tooltip: it holds for whatever popup Material
 * decides to draw next, and it says *why* — the inverse pair has to invert. On the light page
 * [AzkarColors.textPrimary] is near-black, so the pair must come out dark-on-light, and on the dark
 * page it is near-white, so the pair must come out light-on-dark. A projection that merely copied one
 * value into both slots would pass a "did you set it" test and still put dark ink on a dark chip.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
val SchemeProjectionSuite by testSuite("The Material scheme the app hands to its controls") {
    test("the inverse pair inverts with the page, so an overlay is legible on either") {
        runComposeUiTest {
            setContent {
                AzkarTheme(darkTheme = false, uiLocale = Locale.ENGLISH) {
                    val colors = AzkarTheme.colors
                    val scheme = MaterialTheme.colorScheme
                    assertThat(scheme.inverseSurface).isEqualTo(colors.textPrimary)
                    assertThat(scheme.inverseOnSurface).isEqualTo(colors.cardBackground)
                    assertThat(colors.textPrimary.luminance())
                        .isLessThan(colors.cardBackground.luminance())
                }
            }
        }
    }

    test("the dark palette inverts the other way rather than copying the light one") {
        runComposeUiTest {
            setContent {
                AzkarTheme(darkTheme = true, uiLocale = Locale.ENGLISH) {
                    val colors = AzkarTheme.colors
                    val scheme = MaterialTheme.colorScheme
                    assertThat(scheme.inverseSurface).isEqualTo(colors.textPrimary)
                    assertThat(scheme.inverseOnSurface).isEqualTo(colors.cardBackground)
                    assertThat(colors.textPrimary.luminance())
                        .isGreaterThan(colors.cardBackground.luminance())
                }
            }
        }
    }
}
