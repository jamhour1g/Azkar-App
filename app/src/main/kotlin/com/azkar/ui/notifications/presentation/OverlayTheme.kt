package com.azkar.ui.notifications.presentation

import androidx.compose.runtime.Immutable
import com.azkar.ui.theme.AppTypography
import java.util.Locale

/**
 * What the overlay draws with: the three inputs [com.azkar.ui.theme.AzkarTheme] is given.
 *
 * A holder rather than three parameters because that is the fourth theme input and the function was
 * already at the point where the next one would have to be threaded by hand past a limit rather than
 * because it made the call site nicer. It also draws the line the overlay actually has to stay on:
 * the *reader-facing* appearance settings — dark theme, language, font and text size — as opposed to
 * the *reminder* settings, which arrive separately as [NotificationPresentationSettings]. A reminder
 * that appeared in a different typeface from the app it came out of would look like a notification
 * from somewhere else, so the overlay takes the app's appearance and the reminder's own settings and
 * nothing else.
 */
@Immutable
data class OverlayTheme(
    val darkTheme: Boolean,
    val locale: Locale,
    val typography: AppTypography,
)
