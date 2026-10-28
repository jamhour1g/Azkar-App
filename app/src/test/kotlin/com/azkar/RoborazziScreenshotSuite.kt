package com.azkar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.azkar.domain.model.TagSummary
import com.azkar.i18n.activateLocale
import com.azkar.settings.SettingsStore
import com.azkar.ui.components.ActionIcons
import com.azkar.ui.components.AzkarFilterChip
import com.azkar.ui.components.AzkarMenuItem
import com.azkar.ui.components.AzkarOutlinedField
import com.azkar.ui.components.AzkarToggleSwitch
import com.azkar.ui.components.PillToggle
import com.azkar.ui.components.PrimaryButton
import com.azkar.ui.components.SecondaryButton
import com.azkar.ui.home.HomeCollectionRail
import com.azkar.ui.library.AddRemembrancesContent
import com.azkar.ui.library.CollectionCard
import com.azkar.ui.library.CollectionsCatalogue
import com.azkar.ui.library.CollectionsScreen
import com.azkar.ui.library.ConfirmDeleteRemembranceContent
import com.azkar.ui.library.ConfirmRemovalContent
import com.azkar.ui.library.FakeTagGateway
import com.azkar.ui.library.RemembranceEditorContent
import com.azkar.ui.library.TagsScreen
import com.azkar.ui.library.TagsState
import com.azkar.ui.library.data.HomeCollections
import com.azkar.ui.library.rememberCollectionDetailState
import com.azkar.ui.notifications.GeocodingClient
import com.azkar.ui.notifications.LocationDetector
import com.azkar.ui.notifications.NotificationAction
import com.azkar.ui.notifications.NotificationPreferencesRepository
import com.azkar.ui.notifications.StartupRegistration
import com.azkar.ui.notifications.presentation.NotificationCard
import com.azkar.ui.notifications.presentation.PresentedNotice
import com.azkar.ui.preview.PREVIEW_COLLECTION_ID
import com.azkar.ui.preview.previewAzkarAppState
import com.azkar.ui.preview.previewCollectionTiles
import com.azkar.ui.preview.previewRailCards
import com.azkar.ui.preview.previewRemembrance
import com.azkar.ui.preview.previewReminders
import com.azkar.ui.shell.AppLayout
import com.azkar.ui.shell.AzkarDestination
import com.azkar.ui.shell.HeaderCallbacks
import com.azkar.ui.shell.SettingsGroup
import com.azkar.ui.shell.SettingsScreen
import com.azkar.ui.shell.ThemePreference
import com.azkar.ui.shell.UpdateAvailablePreview
import com.azkar.ui.shell.UpdateIdlePreview
import com.azkar.ui.theme.AzkarSpacing
import com.azkar.ui.theme.AzkarTheme
import com.azkar.updates.UpdateManager
import com.github.takahirom.roborazzi.RoborazziOptions
import de.infix.testBalloon.framework.core.testSuite
import io.github.takahirom.roborazzi.captureRoboImage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.nio.file.Files
import java.util.Locale

@OptIn(ExperimentalTestApi::class)
val RoborazziScreenshotSuite by testSuite("Compose Desktop screenshots") {
    listOf(false, true).forEach { dark ->
        val theme = if (dark) "dark" else "light"
        test("home shell $theme") {
            capture("home/screen-home-$theme", dark, readyText = "Friday, 20 February") {
                AppLayout(AzkarDestination.Home, HeaderCallbacks({}, {}, {})) { HomeShot() }
            }
        }
        test("update available $theme") {
            capture("settings/settings-update-available-$theme", dark, width = 720, height = 210) {
                Column(Modifier.width(720.dp).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    UpdateAvailablePreview()
                }
            }
        }
        test("update idle $theme") {
            capture("settings/settings-update-idle-$theme", dark, width = 720, height = 210) {
                Column(Modifier.width(720.dp).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    UpdateIdlePreview()
                }
            }
        }
        test("home rail $theme") {
            capture("home/home-rail-$theme", dark) {
                Column(Modifier.width(380.dp).padding(AzkarSpacing.screenPaddingHorizontal)) {
                    HomeCollectionRail(HomeCollections.pick(previewRailCards(), RAIL_SHOT_LIMIT), setOf(12L), {})
                }
            }
        }
        test("component states $theme") {
            capture("components/component-states-$theme", dark, readyText = "Nothing in here yet") {
                ComponentStatesShot()
            }
        }
        test("add picker selected $theme") {
            capture("dialogs/dialog-add-picker-selected-$theme", dark, readyText = "Clear") {
                LibraryShot { AddPickerShot(it, emptyResults = false) }
            }
        }
        test("add picker empty $theme") {
            capture("dialogs/dialog-add-picker-empty-$theme", dark, readyText = "Nothing left to add") {
                LibraryShot { AddPickerShot(it, emptyResults = true) }
            }
        }
        test("remembrance editor content $theme") {
            capture("dialogs/dialog-remembrance-editor-$theme", dark, readyText = "Edit this remembrance") {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    RemembranceEditorContent(previewRemembrance(id = 901L), {}, { _, _ -> })
                }
            }
        }
        test("remove confirmation content $theme") {
            capture("dialogs/dialog-remove-confirmation-$theme", dark, readyText = "Remove From Collection?") {
                DialogBodyFrame { ConfirmRemovalContent(count = 3, onDismiss = {}, onConfirm = {}) }
            }
        }
        test("delete confirmation content $theme") {
            capture("dialogs/dialog-delete-confirmation-$theme", dark, readyText = "Delete this remembrance?") {
                DialogBodyFrame { ConfirmDeleteRemembranceContent(onDismiss = {}, onConfirm = {}) }
            }
        }
        test("notifications $theme") { capture("notifications/notifications-$theme", dark) { NotificationStackShot() } }
        test("reminder card $theme") {
            capture("notifications/screen-reminder-$theme", dark, density = 3f) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(Modifier.width(420.dp).padding(16.dp)) { PassageReminderShot() }
                }
            }
        }
        test("settings $theme") {
            withSettingsServices {
                capture(
                    "settings/settings-$theme",
                    dark,
                ) { SettingsScreen(previewAzkarAppState(themePreference = dark.preference()), {}) }
            }
        }
        test("prayer settings $theme") {
            withSettingsServices {
                capture("settings/screen-prayer-$theme", dark) {
                    SettingsScreen(
                        previewAzkarAppState(themePreference = dark.preference()),
                        {},
                        initialGroup = SettingsGroup.PRAYER,
                    )
                }
            }
        }
        test("reminder settings $theme") {
            withSettingsServices {
                capture("settings/screen-schedule-$theme", dark) {
                    SettingsScreen(
                        previewAzkarAppState(themePreference = dark.preference()),
                        {},
                        initialGroup = SettingsGroup.REMINDERS,
                    )
                }
            }
        }
        test("collections $theme") {
            capture("library/screen-library-$theme", dark, readyText = "Morning Azkar") {
                LibraryShot { catalogue -> CollectionsScreen({}, {}, {}, { _, _, _ -> }, catalogue = catalogue) }
            }
        }
        test("collection detail $theme") {
            capture("library/screen-collection-$theme", dark, readyText = "Morning Azkar") {
                LibraryShot { CollectionDetailShot(it) }
            }
        }
        test("collection session $theme") {
            capture("library/screen-session-$theme", dark, readyText = "Morning Azkar") {
                LibraryShot { CollectionSessionShot(it) }
            }
        }
        test("remembrance $theme") {
            capture("library/screen-reader-$theme", dark, readyText = "O Allah, I ask You for wellbeing.") {
                LibraryShot { RemembranceShot(it) }
            }
        }
        test("tags $theme") {
            capture("library/tags-$theme", dark, readyText = "Morning") { TagsShot() }
        }
    }
}

@Composable
private fun AddPickerShot(
    catalogue: CollectionsCatalogue,
    emptyResults: Boolean,
) {
    val detail =
        rememberCollectionDetailState(catalogue, PREVIEW_COLLECTION_ID, previewReminders(PREVIEW_COLLECTION_ID))
    LaunchedEffect(detail, emptyResults) {
        detail.addPicker.open()
        if (emptyResults) {
            detail.addPicker.onQueryChanged("no matching remembrance")
        } else {
            detail.addPicker.tiles
                .take(2)
                .forEach { detail.addPicker.toggleSelection(it.id) }
        }
    }
    DialogBodyFrame { AddRemembrancesContent(detail) }
}

@Composable
private fun DialogBodyFrame(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun ComponentStatesShot() {
    val labelColor = AzkarTheme.colors.textPrimary
    Column(
        Modifier.width(1120.dp).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Collection cards", color = labelColor)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            previewCollectionTiles().forEachIndexed { index, tile ->
                CollectionCard(
                    tile = if (index == 0) tile.copy(inReminders = true) else tile,
                    preview = "The most beloved deeds are those done consistently.",
                    items = listOf(AzkarMenuItem(label = "Edit", onClick = {}, icon = ActionIcons.edit)),
                    onOpen = {},
                    covers = null,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text("Controls", color = labelColor)
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Reminder switches", color = labelColor)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    AzkarToggleSwitch(checked = true, onCheckedChange = {})
                    AzkarToggleSwitch(checked = false, onCheckedChange = {})
                    AzkarToggleSwitch(checked = false, onCheckedChange = {}, enabled = false)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillToggle(text = "Grid", selected = true, onClick = {})
                    PillToggle(text = "List", selected = false, onClick = {})
                    AzkarFilterChip(text = "Official", selected = true, onClick = {})
                    AzkarFilterChip(text = "Yours", selected = false, onClick = {})
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AzkarOutlinedField(value = "Morning", onValueChange = {}, placeholder = "Search")
                AzkarOutlinedField(value = "", onValueChange = {}, placeholder = "Search collections")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(text = "Start session", onClick = {}, leadingIcon = ActionIcons.read)
                    SecondaryButton(text = "Cancel", onClick = {})
                    PrimaryButton(text = "Unavailable", onClick = {}, enabled = false)
                }
            }
        }
    }
}

@OptIn(ExperimentalTestApi::class)
private fun capture(
    name: String,
    dark: Boolean,
    density: Float = 1f,
    readyText: String? = null,
    width: Int = 1440,
    height: Int = 900,
    content: @Composable () -> Unit,
) {
    val originalLocale = Locale.getDefault()
    try {
        Locale.ENGLISH.activateLocale()
        runDesktopComposeUiTest(width = width, height = height) {
            setContent {
                ScreenshotFrame(dark, density, content)
            }
            waitUntil(timeoutMillis = 30_000) {
                onAllNodesWithTag("screenshot-loading").fetchSemanticsNodes().isEmpty()
            }
            if (readyText != null) {
                waitUntil(timeoutMillis = 30_000) {
                    onAllNodesWithText(readyText, substring = true).fetchSemanticsNodes().isNotEmpty()
                }
                onAllNodesWithText(readyText, substring = true).onFirst().assertIsDisplayed()
            }
            waitForIdle()
            onRoot().captureRoboImage("src/test/roborazzi/$name.webp", RoborazziOptions().withLosslessWebp())
        }
    } finally {
        originalLocale.activateLocale()
    }
}

private suspend fun withSettingsServices(block: suspend () -> Unit) {
    val home = withContext(Dispatchers.IO) { Files.createTempDirectory("azkar-settings-screenshot-") }
    val serviceJob = SupervisorJob(currentCoroutineContext()[Job])
    val scope = CoroutineScope(serviceJob + Dispatchers.IO)
    val client = HttpClient(MockEngine { error("Screenshots must not make HTTP requests") })
    try {
        startKoin {
            modules(
                module {
                    single { SettingsStore.create(file = home.resolve("settings.preferences_pb"), scope = scope) }
                    single { NotificationPreferencesRepository(get()) }
                    single { LocationDetector(client) }
                    single { GeocodingClient(client) }
                    single { StartupRegistration(packaged = false, legacyPaths = emptyList()) }
                    single { UpdateManager(get(), client, scope) }
                },
            )
        }
        try {
            block()
        } finally {
            stopKoin()
        }
    } finally {
        withContext(NonCancellable) {
            serviceJob.cancelAndJoin()
            client.close()
            withContext(Dispatchers.IO) {
                Files.walk(home).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
            }
        }
    }
}

private fun Boolean.preference() = if (this) ThemePreference.DARK else ThemePreference.LIGHT

/**
 * Three cards at once, which is the stack cap and the layout that has to hold.
 *
 * The first of them is a real passage card rather than a bare notice, because the bare notice is the
 * one body the overlay almost never shows: the scheduler publishes a passage or a prayer time, and
 * the passage is the body that carries the star. A screenshot of three bare notices would show
 * exactly the cards that cannot have the new control on them.
 */
@Composable
private fun NotificationStackShot() {
    Column(
        modifier = Modifier.width(380.dp).padding(AzkarSpacing.screenPaddingHorizontal),
        verticalArrangement = Arrangement.spacedBy(AzkarSpacing.dashboard),
    ) {
        PassageReminderShot()
        listOf(
            "After Fajr" to "Say it once, slowly, and mean it.",
            "Asr is due in 20 minutes" to "Reminder queued from your morning collection.",
        ).forEachIndexed { index, (title, message) ->
            NotificationCard(
                item =
                    PresentedNotice(
                        id = (index + 1).toLong(),
                        title = title,
                        message = message,
                        action = NotificationAction.Remembrance((index + 1).toLong()),
                    ),
                onOpen = {},
                onDismiss = {},
                onPauseChanged = {},
            )
        }
    }
}

@Composable
private fun TagsShot() {
    val scope = rememberCoroutineScope()
    val tags =
        remember(scope) {
            TagsState(
                scope = scope,
                gateway =
                    FakeTagGateway(
                        tags =
                            listOf(
                                TagSummary(
                                    id = 1L,
                                    name = "Morning",
                                    createdAt = null,
                                    updatedAt = null,
                                    deletedAt = null,
                                    collectionCount = 2,
                                    remembranceCount = 4,
                                ),
                            ),
                        deleted = emptyList(),
                    ),
                onTagsChanged = {},
            )
        }
    TagsScreen(
        state = tags,
        onBack = {},
        showToast = { _, _, _ -> },
        onOpenCollection = {},
        onOpenRemembrance = {},
    )
    if (tags.loading) Box(Modifier.testTag("screenshot-loading"))
}

private const val RAIL_SHOT_LIMIT = 4
