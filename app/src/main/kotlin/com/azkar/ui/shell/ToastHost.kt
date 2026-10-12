package com.azkar.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.generated.resources.*
import com.azkar.ui.theme.AzkarColors
import com.azkar.ui.theme.AzkarMotion
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.fadeThroughEnter
import com.azkar.ui.theme.fadeThroughExit
import com.azkar.ui.theme.isRtlLayout
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/** Signature every screen uses to raise a `.library-toast`, owned by the shell. */
typealias ShowToast = (title: String, message: String, priority: NotificationPriority) -> Unit

/**
 * `.library-toast` — Compose replacement for the JavaFX popup stack built by
 * `LibraryOverlaySupport.showNotificationPopup`. The original positioned itself at the
 * bottom-leading corner (x + 40) in RTL and the bottom-trailing corner (x + width - 360)
 * in LTR, and auto-hid after six seconds via `popupDismissExecutor`; the dismissal timer now
 * lives in [rememberToastController].
 */
@Composable
fun ToastHost(
    state: AzkarAppState,
    modifier: Modifier = Modifier,
) {
    val rtl = isRtlLayout()
    Box(modifier = modifier.fillMaxSize()) {
        state.toasts.forEach { toast ->
            // Keyed so a toast keeps its own transition state while it is on its way out. Without it
            // the list reuses each card's `remember` positionally, and a toast exiting behind another
            // would inherit its neighbour's visibility — one toast leaving would drag the rest with it.
            key(toast.id) {
                ToastCard(
                    toast = toast,
                    state = state,
                    modifier =
                        Modifier
                            .align(if (rtl) Alignment.BottomStart else Alignment.BottomEnd)
                            .padding(
                                start = 40.dp,
                                end = 40.dp,
                                bottom = 40.dp,
                            ),
                )
            }
        }
    }
}

@Composable
private fun ToastCard(
    toast: ToastMessage,
    state: AzkarAppState,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    val gradient = toast.priority.toastGradient(colors)
    val borderColor =
        when (toast.priority) {
            NotificationPriority.HIGH -> colors.toastHighStart
            NotificationPriority.MEDIUM -> colors.toastMediumStart
            NotificationPriority.LOW -> colors.toastLowStart
        }
    // Owned by the shell rather than remembered here: [AzkarAppState.dismissToast] has to reach it
    // from the controller's timer, six seconds away and with no access to this composition.
    val visibility =
        state.toastVisibility.getOrPut(toast.id) {
            MutableTransitionState(false).apply { targetState = true }
        }
    // Outside [AnimatedVisibility] on purpose: the content is torn down as soon as the exit ends,
    // which would dispose this and leave the toast in the shell's list forever. Waiting out the exit
    // on the clock rather than on a transition callback is exact here because the exit below is the
    // one and only animation this card has, and its duration is a constant.
    LaunchedEffect(toast.id, visibility.targetState) {
        if (visibility.targetState) return@LaunchedEffect
        delay(AzkarMotion.OVERLAY.toLong())
        state.retireToast(toast.id)
    }

    AnimatedVisibility(
        visibleState = visibility,
        // The exit was missing entirely while `visible = true` was hard-coded, so a toast blinked out
        // of existence the moment its six seconds ran out instead of leaving. Both halves now come
        // from the shared pair, which also shortens the entry from half the card's own height to a
        // quarter: a toast is pinned under the header, and sliding it its full half-height reads as
        // being thrown at the screen rather than set down on it.
        enter = fadeThroughEnter(),
        exit = fadeThroughExit(),
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .width(320.dp)
                    .background(gradient, RoundedCornerShape(10.dp))
                    .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                    .padding(12.dp),
        ) {
            Text(
                text = toast.title,
                color = colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = toast.message.ifBlank { stringResource(Res.string.toastMessageFallback) },
                color = colors.textSecondary,
                fontSize = 12.sp,
            )
        }
    }
}

private fun NotificationPriority.toastGradient(colors: AzkarColors): Brush =
    Brush.linearGradient(
        colors =
            when (this) {
                NotificationPriority.HIGH -> listOf(colors.toastHighStart, colors.toastHighEnd)
                NotificationPriority.MEDIUM -> listOf(colors.toastMediumStart, colors.toastMediumEnd)
                NotificationPriority.LOW -> listOf(colors.toastLowStart, colors.toastLowEnd)
            },
    )
