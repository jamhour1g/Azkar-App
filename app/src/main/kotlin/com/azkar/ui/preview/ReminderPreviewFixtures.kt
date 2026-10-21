package com.azkar.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.azkar.ui.notifications.NotificationPreferencesRepository
import com.azkar.ui.notifications.NotificationSettingsState
import com.azkar.ui.notifications.ReminderScheduleEditor

/*
 * The notification-related screens' preview fixtures, split out of [PreviewFixtures].
 *
 * Same rules as that file — in-memory only, never registered in `appModule`, reachable only from a
 * `@Preview` body or `Screenshot.kt`. It lives here rather than there because the catalogue
 * fixtures already ran [PreviewFixtures] past the per-file size limit, and because a screen's own
 * fixtures read better beside the screen than buried in a list of everything else's.
 */

/**
 * The settings page's notification half, over a throwaway settings file.
 *
 * Neither platform capability is supplied: [NotificationSettingsState] takes the detector and the
 * startup registration as nullable, so a preview host that cannot reach a network or a login manager
 * still draws every row, and pressing "Auto-detect" there says the lookup is unavailable rather than
 * reaching for a real socket. That is the same answer the button gives offline, so the preview shows
 * the real failure text instead of a state the app can never reach.
 */
@Composable
fun rememberPreviewNotificationSettingsState(): NotificationSettingsState {
    val scope = rememberCoroutineScope()
    val settingsStore = rememberPreviewSettingsStore()
    val preferences = remember(settingsStore) { NotificationPreferencesRepository(settingsStore) }
    return remember(scope, preferences) {
        NotificationSettingsState(scope = scope, preferences = preferences)
    }
}

/**
 * The schedule editor, over a throwaway settings file.
 *
 * Separate from [rememberPreviewNotificationSettingsState] rather than reached through it, because the
 * schedule card's preview is the only thing that needs the editor and not the location draft or the
 * startup notice. A fixture that handed out the whole settings holder would have every caller reach
 * through two levels of namespace for the one it meant.
 */
@Composable
fun rememberPreviewReminderScheduleEditor(): ReminderScheduleEditor {
    val scope = rememberCoroutineScope()
    val settingsStore = rememberPreviewSettingsStore()
    val preferences = remember(settingsStore) { NotificationPreferencesRepository(settingsStore) }
    return remember(scope, preferences) {
        ReminderScheduleEditor(scope = scope, preferences = preferences)
    }
}
