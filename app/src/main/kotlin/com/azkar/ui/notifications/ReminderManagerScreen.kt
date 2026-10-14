package com.azkar.ui.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.domain.model.Remembrance
import com.azkar.domain.model.RemembranceCollection
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.notificationChannelSystem
import com.azkar.generated.resources.notificationChannelSystemNote
import com.azkar.generated.resources.notificationDuration
import com.azkar.generated.resources.notificationOverlayPolicy
import com.azkar.generated.resources.notificationPlacement
import com.azkar.generated.resources.notificationPlacementBottomEnd
import com.azkar.generated.resources.notificationPlacementBottomStart
import com.azkar.generated.resources.notificationPlacementTopEnd
import com.azkar.generated.resources.notificationPlacementTopStart
import com.azkar.generated.resources.notificationPresentationSubtitle
import com.azkar.generated.resources.notificationPresentationTitle
import com.azkar.generated.resources.notificationStackSize
import com.azkar.generated.resources.reminderManagerSubtitle
import com.azkar.generated.resources.reminderManagerTitle
import com.azkar.i18n.RemembranceI18n
import com.azkar.ui.components.AzkarCard
import com.azkar.ui.home.PrayerKey
import com.azkar.ui.notifications.presentation.OverlayPlacement
import com.azkar.ui.theme.AzkarTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import java.time.LocalTime
import java.util.Locale

/** Global editor for the mixed remembrance queue, prayer alerts and launch behavior. */
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
fun ReminderManagerScreen(
    initialCollectionId: Long? = null,
    modifier: Modifier = Modifier,
) {
    val preferences = koinInject<NotificationPreferencesRepository>()
    val collectionsGateway = koinInject<com.azkar.ui.library.data.CollectionsGateway>()
    val locationDetector = koinInject<LocationDetector>()
    val startupRegistration = koinInject<StartupRegistration>()
    val scope = rememberCoroutineScope()
    val queue by preferences.queue.collectAsState()
    val location by preferences.prayerLocation.collectAsState()
    val prayerFlags by preferences.prayerFlags.collectAsState()
    val startOnLogin by preferences.startOnLogin.collectAsState()
    val presentation by preferences.presentation.collectAsState()
    var collections by remember { mutableStateOf<List<RemembranceCollection>>(emptyList()) }
    var remembrances by remember { mutableStateOf<List<Remembrance>>(emptyList()) }
    var uncategorizedId by remember { mutableStateOf<Long?>(null) }
    var query by remember { mutableStateOf("") }
    var city by remember(location) { mutableStateOf(location.city) }
    var latitude by remember(location) { mutableStateOf(location.latitude.toString()) }
    var longitude by remember(location) { mutableStateOf(location.longitude.toString()) }
    var zoneId by remember(location) { mutableStateOf(location.zoneId) }
    var locationError by remember { mutableStateOf<String?>(null) }
    var locationStatus by remember { mutableStateOf<String?>(null) }
    var methodMenu by remember { mutableStateOf(false) }
    var modeMenu by remember { mutableStateOf(false) }
    var intervalMenu by remember { mutableStateOf(false) }
    var placementMenu by remember { mutableStateOf(false) }
    var autoSelectionApplied by remember(initialCollectionId) { mutableStateOf(false) }

    LaunchedEffect(preferences) {
        preferences.refresh()
        preferences.observe(scope)
    }
    LaunchedEffect(collectionsGateway) {
        runCatching { collectionsGateway.fetchSnapshot() }
            .onSuccess { snapshot ->
                collections = snapshot.collections
                remembrances = snapshot.remembrances
                uncategorizedId = snapshot.uncategorizedCollectionId
            }
    }
    LaunchedEffect(initialCollectionId, collections, queue.collectionIds) {
        val id = initialCollectionId ?: return@LaunchedEffect
        if (!autoSelectionApplied && collections.any { it.id == id }) {
            if (id !in queue.collectionIds) {
                preferences.updateQueue { it.copy(collectionIds = it.collectionIds + id) }
            }
            autoSelectionApplied = true
        }
    }

    val locale = AzkarTheme.locale
    val persistedRemembrances = remembrances.filter { it.id != null }
    val collectionItems = collections.filter { it.id != null && it.id != uncategorizedId }
    val availableIds =
        remember(queue.collectionIds, queue.individualRemembranceIds, collectionItems, persistedRemembrances) {
            val members =
                collectionItems
                    .filter { it.id in queue.collectionIds }
                    .flatMap { collection -> collection.members.mapNotNull { it.id } }
            (members + queue.individualRemembranceIds)
                .distinct()
                .filter { id -> persistedRemembrances.any { it.id == id } }
        }
    val manualItems =
        (queue.manualOrder.filter { it in availableIds } + availableIds.filterNot { it in queue.manualOrder })
            .distinct()
            .mapNotNull { id -> persistedRemembrances.firstOrNull { it.id == id } }
    val sourceDropChance: (Long) -> Int = { id ->
        collectionItems
            .filter { collection ->
                collection.id in queue.collectionIds && collection.members.any { it.id == id }
            }.mapNotNull { collection ->
                collection.id?.let { queue.collectionDropChances[it.toString()] ?: queue.defaultDropChance }
            }.minOrNull() ?: queue.defaultDropChance
    }
    val filteredItems =
        persistedRemembrances
            .filter { item ->
                query.isBlank() || notificationLabel(item, locale).contains(query, ignoreCase = true)
            }.take(150)

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                stringResource(Res.string.reminderManagerTitle),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = AzkarTheme.colors.textPrimary,
            )
            Text(stringResource(Res.string.reminderManagerSubtitle), color = AzkarTheme.colors.textSecondary)
        }

        AzkarCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SettingSwitchRow("Enable remembrance queue", queue.enabled) { enabled ->
                    scope.launch { preferences.updateQueue { it.copy(enabled = enabled) } }
                }
                Text("Queue order", fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { modeMenu = true }) {
                        Text(
                            queue.mode.name
                                .lowercase()
                                .replaceFirstChar(Char::uppercase),
                        )
                    }
                    DropdownMenu(expanded = modeMenu, onDismissRequest = { modeMenu = false }) {
                        QueueMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.name.lowercase().replaceFirstChar(Char::uppercase)) },
                                onClick = {
                                    modeMenu = false
                                    scope.launch { preferences.updateQueue { it.copy(mode = mode) } }
                                },
                            )
                        }
                    }
                    if (queue.mode == QueueMode.WEIGHTED) {
                        Text(
                            "Higher drop % means more likely to skip",
                            color = AzkarTheme.colors.textSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }
                if (queue.mode == QueueMode.WEIGHTED) {
                    NumberSettingRow("Default drop chance (%)", queue.defaultDropChance) { value ->
                        scope.launch { preferences.updateQueue { it.copy(defaultDropChance = value.coerceIn(0, 100)) } }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Interval", modifier = Modifier.weight(1f))
                    TextButton(onClick = { intervalMenu = true }) { Text("${queue.intervalMinutes} minutes") }
                    DropdownMenu(expanded = intervalMenu, onDismissRequest = { intervalMenu = false }) {
                        listOf(5, 10, 15, 30, 60, 90, 120, 180).forEach { minutes ->
                            DropdownMenuItem(
                                text = { Text("$minutes minutes") },
                                onClick = {
                                    intervalMenu = false
                                    scope.launch { preferences.updateQueue { it.copy(intervalMinutes = minutes) } }
                                },
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TimeSettingField("Start (HH:mm)", queue.windowStartMinute, Modifier.weight(1f)) { minute ->
                        scope.launch { preferences.updateQueue { it.withWindowStart(minute) } }
                    }
                    TimeSettingField("End (HH:mm)", queue.windowEndMinute, Modifier.weight(1f)) { minute ->
                        scope.launch { preferences.updateQueue { it.withWindowEnd(minute) } }
                    }
                }
                Text(
                    "Queue repeats after every cycle. Missed intervals are not replayed.",
                    color = AzkarTheme.colors.textSecondary,
                    fontSize = 12.sp,
                )
            }
        }

        AzkarCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Collections", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                collectionItems.forEach { collection ->
                    val id = requireNotNull(collection.id)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = id in queue.collectionIds, onCheckedChange = { checked ->
                            scope.launch {
                                preferences.updateQueue { config ->
                                    config.copy(
                                        collectionIds =
                                            if (checked) {
                                                (config.collectionIds + id).distinct()
                                            } else {
                                                config.collectionIds -
                                                    id
                                            },
                                    )
                                }
                            }
                        })
                        Text(collection.displayName(locale), modifier = Modifier.weight(1f))
                        if (queue.mode == QueueMode.WEIGHTED && id in queue.collectionIds) {
                            NumberSettingRow(
                                "Drop %",
                                queue.collectionDropChances[id.toString()] ?: queue.defaultDropChance,
                                Modifier.width(150.dp),
                            ) { value ->
                                scope.launch {
                                    preferences.updateQueue {
                                        it.copy(
                                            collectionDropChances =
                                                it.collectionDropChances + (id.toString() to value.coerceIn(0, 100)),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Text(
                    "Individual remembrances",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search remembrances") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    filteredItems.forEach { remembrance ->
                        val id = requireNotNull(remembrance.id)
                        val selected = id in queue.individualRemembranceIds
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = selected, onCheckedChange = { checked ->
                                scope.launch {
                                    preferences.updateQueue { config ->
                                        config.copy(
                                            individualRemembranceIds =
                                                if (checked) {
                                                    (config.individualRemembranceIds + id).distinct()
                                                } else {
                                                    config.individualRemembranceIds - id
                                                },
                                        )
                                    }
                                }
                            })
                            Text(
                                notificationLabel(remembrance, locale),
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                fontSize = 13.sp,
                            )
                            if (queue.mode == QueueMode.WEIGHTED && (selected || id in availableIds)) {
                                NumberSettingRow(
                                    "Drop %",
                                    queue.remembranceDropOverrides[id.toString()] ?: sourceDropChance(id),
                                    Modifier.width(150.dp),
                                ) { value ->
                                    scope.launch {
                                        preferences.updateQueue {
                                            it.copy(
                                                remembranceDropOverrides =
                                                    it.remembranceDropOverrides +
                                                        (id.toString() to value.coerceIn(0, 100)),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (queue.mode == QueueMode.MANUAL && manualItems.isNotEmpty()) {
            AzkarCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Manual order", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    manualItems.forEachIndexed { index, remembrance ->
                        val id = requireNotNull(remembrance.id)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                notificationLabel(remembrance, locale),
                                modifier = Modifier.weight(1f),
                                fontSize = 13.sp,
                                maxLines = 2,
                            )
                            TextButton(
                                enabled = index > 0,
                                onClick = {
                                    scope.launch {
                                        preferences.updateQueue {
                                            it.copy(manualOrder = move(it.manualOrder, availableIds, id, -1))
                                        }
                                    }
                                },
                            ) { Text("↑") }
                            TextButton(
                                enabled = index < manualItems.lastIndex,
                                onClick = {
                                    scope.launch {
                                        preferences.updateQueue {
                                            it.copy(manualOrder = move(it.manualOrder, availableIds, id, 1))
                                        }
                                    }
                                },
                            ) { Text("↓") }
                        }
                    }
                }
            }
        }

        AzkarCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Prayer notifications", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                PrayerKey.entries.forEach { key ->
                    SettingSwitchRow(
                        key.name.lowercase().replaceFirstChar(Char::uppercase),
                        prayerFlags[key] ?: true,
                    ) { enabled ->
                        scope.launch { preferences.setPrayerEnabled(key, enabled) }
                    }
                }
                Text(
                    "Location and calculation",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp),
                )
                OutlinedTextField(
                    city,
                    { city = it },
                    label = { Text("City / place") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        latitude,
                        { latitude = it },
                        label = { Text("Latitude") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    OutlinedTextField(
                        longitude,
                        { longitude = it },
                        label = { Text("Longitude") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                OutlinedTextField(
                    zoneId,
                    { zoneId = it },
                    label = { Text("Time zone (e.g. Europe/London)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { methodMenu = true }) { Text(location.method.displayName) }
                    DropdownMenu(expanded = methodMenu, onDismissRequest = { methodMenu = false }) {
                        PrayerCalculationMethod.entries.forEach { method ->
                            DropdownMenuItem(
                                text = { Text(method.displayName) },
                                onClick = {
                                    methodMenu = false
                                    scope.launch { preferences.savePrayerLocation(location.copy(method = method)) }
                                },
                            )
                        }
                    }
                    Button(onClick = {
                        val lat = latitude.toDoubleOrNull()
                        val lon = longitude.toDoubleOrNull()
                        val candidate =
                            runCatching {
                                location.copy(
                                    latitude = requireNotNull(lat),
                                    longitude = requireNotNull(lon),
                                    city = city,
                                    zoneId = zoneId,
                                    autoDetect = false,
                                )
                            }.getOrNull()
                        if (candidate == null) {
                            locationError = "Enter valid coordinates and a time zone."
                        } else {
                            scope.launch {
                                runCatching { java.time.ZoneId.of(candidate.zoneId) }
                                    .onSuccess {
                                        preferences.savePrayerLocation(candidate)
                                        locationError = null
                                        locationStatus = "Saved"
                                    }.onFailure { locationError = "Enter a valid time zone." }
                            }
                        }
                    }) { Text("Save location") }
                    TextButton(onClick = {
                        scope.launch {
                            runCatching { locationDetector.detect() }
                                .onSuccess { detected ->
                                    val updated = detected.copy(method = location.method)
                                    preferences.savePrayerLocation(updated)
                                    city = updated.city
                                    latitude = updated.latitude.toString()
                                    longitude = updated.longitude.toString()
                                    zoneId = updated.zoneId
                                    locationError = null
                                    locationStatus = "Detected ${updated.city}"
                                }.onFailure { locationError = it.message ?: "Could not detect location." }
                        }
                    }) { Text("Auto-detect") }
                }
                locationError?.let { Text(it, color = AzkarTheme.colors.primaryStrong) }
                locationStatus?.let { Text(it, color = AzkarTheme.colors.textSecondary, fontSize = 12.sp) }
            }
        }

        AzkarCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(Res.string.notificationPresentationTitle),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
                Text(
                    stringResource(Res.string.notificationPresentationSubtitle),
                    color = AzkarTheme.colors.textSecondary,
                    fontSize = 13.sp,
                )
                SettingSwitchRow(
                    stringResource(Res.string.notificationChannelSystem),
                    presentation.systemNotificationsEnabled,
                ) { enabled ->
                    scope.launch { preferences.updatePresentation { it.copy(systemNotificationsEnabled = enabled) } }
                }
                Text(
                    stringResource(Res.string.notificationChannelSystemNote),
                    color = AzkarTheme.colors.textSecondary,
                    fontSize = 12.sp,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NumberSettingRow(
                        stringResource(Res.string.notificationDuration),
                        presentation.durationSeconds,
                        Modifier.width(160.dp),
                    ) { value ->
                        scope.launch {
                            preferences.updatePresentation {
                                it.copy(durationSeconds = value.coerceIn(3, 120))
                            }
                        }
                    }
                    NumberSettingRow(
                        stringResource(Res.string.notificationStackSize),
                        presentation.maxVisible,
                        Modifier.width(160.dp),
                    ) { value ->
                        scope.launch {
                            preferences.updatePresentation { it.copy(maxVisible = value.coerceIn(1, 5)) }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { placementMenu = true }) {
                        Text(
                            stringResource(Res.string.notificationPlacement) +
                                ": " +
                                placementLabel(presentation.placement),
                        )
                    }
                    DropdownMenu(
                        expanded = placementMenu,
                        onDismissRequest = { placementMenu = false },
                    ) {
                        OverlayPlacement.entries.forEach { placement ->
                            DropdownMenuItem(
                                text = { Text(placementLabel(placement)) },
                                onClick = {
                                    placementMenu = false
                                    scope.launch {
                                        preferences.updatePresentation { it.copy(placement = placement) }
                                    }
                                },
                            )
                        }
                    }
                }
                Text(
                    stringResource(Res.string.notificationOverlayPolicy),
                    color = AzkarTheme.colors.textSecondary,
                    fontSize = 12.sp,
                )
            }
        }

        AzkarCard(modifier = Modifier.fillMaxWidth()) {
            SettingSwitchRow("Start Azkar when I sign in", startOnLogin) { enabled ->
                scope.launch {
                    startupRegistration
                        .setEnabled(enabled)
                        .onSuccess {
                            preferences.setStartOnLogin(enabled)
                            locationError = null
                        }.onFailure { locationError = it.message ?: "Could not update startup setting." }
                }
            }
        }
    }
}

@Composable
private fun placementLabel(placement: OverlayPlacement): String =
    stringResource(
        when (placement) {
            OverlayPlacement.TOP_START -> Res.string.notificationPlacementTopStart
            OverlayPlacement.TOP_END -> Res.string.notificationPlacementTopEnd
            OverlayPlacement.BOTTOM_START -> Res.string.notificationPlacementBottomStart
            OverlayPlacement.BOTTOM_END -> Res.string.notificationPlacementBottomEnd
        },
    )

@Composable
private fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun NumberSettingRow(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    onValueChange: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input.filter(Char::isDigit).take(3)
            text.toIntOrNull()?.let(onValueChange)
        },
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
private fun TimeSettingField(
    label: String,
    minute: Int,
    modifier: Modifier,
    onMinuteChange: (Int) -> Unit,
) {
    val formatted = remember(minute) { "%02d:%02d".format(Locale.ROOT, minute / 60, minute % 60) }
    var text by remember(minute) { mutableStateOf(formatted) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input.filter { it.isDigit() || it == ':' }.take(5)
            runCatching { LocalTime.parse(text) }.getOrNull()?.let { onMinuteChange(it.hour * 60 + it.minute) }
        },
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
    )
}

private fun notificationLabel(
    remembrance: Remembrance,
    locale: Locale,
): String {
    val translations = RemembranceI18n.resolveTexts(remembrance, locale, "", "")
    return translations.translation
        .ifBlank {
            remembrance.translations.values
                .firstOrNull()
                ?.translationPair
                ?.text
                .orEmpty()
        }.replace('\n', ' ')
        .ifBlank { "Remembrance ${remembrance.id}" }
}

private fun move(
    current: List<Long>,
    available: List<Long>,
    id: Long,
    offset: Int,
): List<Long> {
    val order = (current.filter { it in available } + available.filterNot { it in current }).toMutableList()
    val from = order.indexOf(id)
    val to = (from + offset).coerceIn(0, order.lastIndex)
    if (from >= 0 && to in order.indices) {
        val item = order.removeAt(from)
        order.add(to, item)
    }
    return order
}

private fun ReminderQueueConfig.withWindowStart(minute: Int): ReminderQueueConfig =
    copy(
        windowStartMinute = minute,
        windowEndMinute = if (minute == windowEndMinute) (minute + 1) % 1440 else windowEndMinute,
    )

private fun ReminderQueueConfig.withWindowEnd(minute: Int): ReminderQueueConfig =
    copy(
        windowStartMinute = if (minute == windowStartMinute) (minute + 1439) % 1440 else windowStartMinute,
        windowEndMinute = minute,
    )
