package com.azkar.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.azkar.ui.library.model.CollectionsSnapshot
import com.azkar.ui.notifications.NotificationPreferencesRepository
import com.azkar.ui.notifications.NotificationSettingsState
import com.azkar.ui.notifications.ReminderCatalogueSource
import com.azkar.ui.notifications.ReminderManagerState
import com.azkar.ui.notifications.ReminderScheduleEditor
import java.time.ZoneId

/*
 * The notification-related screens' preview fixtures, split out of [PreviewFixtures].
 *
 * Same rules as that file — in-memory only, never registered in `appModule`, reachable only from a
 * `@Preview` body or `Screenshot.kt`. It lives here rather than there because the catalogue
 * fixtures already ran [PreviewFixtures] past the per-file size limit, and because a screen's own
 * fixtures read better beside the screen than buried in a list of everything else's.
 */

/**
 * The reminders screen's state holder, over [previewGateway] and a throwaway settings file.
 *
 * `remember`ed against the settings file so a re-render keeps whatever has been loaded — a preview
 * that rebuilt the repository every frame would never finish reading it.
 */
@Composable
fun rememberPreviewReminderManagerState(zone: ZoneId = ZoneId.of("UTC")): ReminderManagerState {
    val scope = rememberCoroutineScope()
    val settingsStore = rememberPreviewSettingsStore()
    val gateway = remember { previewGateway() }
    val catalogueSource = remember(gateway) { PreviewReminderCatalogueSource(gateway) }
    val preferences = remember(settingsStore) { NotificationPreferencesRepository(settingsStore) }
    return remember(scope, preferences, catalogueSource, zone) {
        ReminderManagerState(
            scope = scope,
            preferences = preferences,
            catalogueSource = catalogueSource,
            zone = zone,
        )
    }
}

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

/**
 * The one-method adapter the screen wants instead of the whole [com.azkar.ui.library.data.CollectionsGateway].
 *
 * Named rather than an anonymous `object` so the fixture reads as what it is — a preview seam — and
 * so [remember] has a stable type to key on.
 */
private class PreviewReminderCatalogueSource(
    private val gateway: PreviewCollectionsGateway,
) : ReminderCatalogueSource {
    override suspend fun snapshot(): CollectionsSnapshot = gateway.fetchSnapshot()
}
