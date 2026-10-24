package com.azkar.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.azkar.domain.model.Remembrance
import com.azkar.domain.model.RemembranceCollection
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.collectionDetailActionReminderChance
import com.azkar.generated.resources.collectionDetailActionReminderChanceHint
import com.azkar.generated.resources.collectionDetailEmptyHint
import com.azkar.generated.resources.collectionDetailEmptyTitle
import com.azkar.generated.resources.collectionDetailMissingHint
import com.azkar.generated.resources.collectionDetailMissingTitle
import com.azkar.generated.resources.collectionDetailSearchHint
import com.azkar.generated.resources.collectionsActionAddMembers
import com.azkar.generated.resources.collectionsActionAddToReminders
import com.azkar.generated.resources.collectionsActionDelete
import com.azkar.generated.resources.collectionsActionDuplicate
import com.azkar.generated.resources.collectionsActionEdit
import com.azkar.generated.resources.collectionsActionFavorite
import com.azkar.generated.resources.collectionsActionHideFromHome
import com.azkar.generated.resources.collectionsActionPin
import com.azkar.generated.resources.collectionsActionRemoveFromReminders
import com.azkar.generated.resources.collectionsActionShowOnHome
import com.azkar.generated.resources.collectionsActionUnfavorite
import com.azkar.generated.resources.collectionsActionUnpin
import com.azkar.generated.resources.collectionsDuplicateNameSuffix
import com.azkar.generated.resources.collectionsToastTitle
import com.azkar.generated.resources.libraryActionCopyCopied
import com.azkar.generated.resources.remembranceCreateActionOpen
import com.azkar.ui.components.ActionIcons
import com.azkar.ui.components.AzkarMenuItem
import com.azkar.ui.components.AzkarOutlinedField
import com.azkar.ui.components.SettingRow
import com.azkar.ui.components.StepperControl
import com.azkar.ui.library.CollectionDetailState
import com.azkar.ui.library.data.CollectionPresenter.Companion.ARABIC_LOCALE
import com.azkar.ui.library.data.findById
import com.azkar.ui.library.model.RemembranceTile
import com.azkar.ui.library.model.TagOption
import com.azkar.ui.preview.PREVIEW_COLLECTION_ID
import com.azkar.ui.preview.previewReminders
import com.azkar.ui.preview.rememberPreviewCatalogue
import com.azkar.ui.shell.NotificationPriority
import com.azkar.ui.shell.ShowToast
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.AzkarThemePreview
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * One collection, opened.
 *
 * A painted band that says what the collection is and how far through it you are, a card for the
 * passage that is waiting, a toolbar for narrowing twenty-nine members down to the ones you want, and
 * then the members. Those four regions used to be four separately-bordered boxes above a grid, which
 * read as four unrelated panels rather than as a page with a header and a body.
 *
 * It reads the shell's shared [CollectionsCatalogue] rather than a shelf of its own. That is what
 * makes a change made here show up on the tile behind it: one read of the catalogue serves the shelf,
 * this page and the session under it, and every write goes back through the same holder. Reading it
 * again per page was cheap enough to ignore and wrong enough to fix — [NavDisplay] composes only the
 * entry on top of the stack, so a per-page holder was discarded and rebuilt on every hop in and out.
 *
 * @param detail the page's state, already built by [rememberCollectionDetailState]. Handed in
 * rather than built here so the entry that pushed this page owns the holder's lifetime, which is the
 * entry's lifetime — and so this function is a plain function of a state a caller can also build
 * itself, in a test or a screenshot, without repeating what [rememberCollectionDetailState] knows.
 * @param onBack pops the stack rather than navigating by name — this page is always a child of the
 * shelf, and the stack already knows what is underneath it.
 */
@Composable
fun CollectionDetailScreen(
    detail: CollectionDetailState,
    onBack: () -> Unit,
    onStartSession: (Long) -> Unit,
    onOpenRemembrance: (Long) -> Unit,
    showToast: ShowToast,
    modifier: Modifier = Modifier,
) {
    CollectionDetailContent(
        collections = detail.catalogue,
        detail = detail,
        navigation =
            DetailNavigation(
                onBack = onBack,
                onStartSession = onStartSession,
                onOpenRemembrance = onOpenRemembrance,
            ),
        showToast = showToast,
        modifier = modifier,
    )
}

/**
 * The page's holder, alive for exactly as long as the composition that asked for it.
 *
 * Keyed on everything the holder reads rather than on nothing, so a shelf that has been re-read, a
 * reminder state that has been replaced, or a *different* collection arriving on the same entry all
 * produce the holder that matches what is on screen.
 *
 * [collectionId] names the row this page is about, and its absence is a state rather than an error:
 * a collection can be deleted from the shelf while this page is still on the stack.
 */
@Composable
fun rememberCollectionDetailState(
    catalogue: CollectionsCatalogue,
    collectionId: Long,
    reminders: CollectionReminderState,
): CollectionDetailState =
    remember(catalogue, collectionId, reminders) {
        CollectionDetailState(
            collections = catalogue,
            collectionId = collectionId,
            reminders = reminders,
        )
    }

/**
 * Everything that is *about* the collection, as opposed to everything that is *of* it.
 *
 * Three regions, not four: the band, the toolbar, and the strip that says how far through it you are.
 * The band says what the collection is and how to continue, to pin it, to schedule it and to change
 * it; the toolbar edits and orders the members; and the strip below them is the one place a count of
 * progress appears, because a card under the band repeating it — and the band's own copy of it — was
 * two ways to ask a question the reader had already been given the answer to.
 *
 * A composable of its own so that the page above reads as a page — one lazy container, three
 * regions, a dialog host — rather than as the band's argument list with a scroll container wrapped
 * round it.
 */
@Composable
internal fun CollectionRegion(
    detail: CollectionDetailState,
    collections: CollectionsCatalogue,
    dialogs: DetailDialogState,
    collection: RemembranceCollection,
    navigation: DetailNavigation,
) {
    val title = headerActions(collections, detail, collection, dialogs, navigation)

    CollectionDetailHero(
        collection = collection,
        memberCount = detail.memberCount,
        stats = detail.stats,
        inReminders = detail.inReminders,
        actions = detailHeroActions(detail = detail, collection = collection, title = title, navigation = navigation),
    )

    Spacer(Modifier.height(DETAIL_REGION_GAP))

    // How likely this collection is to be skipped, when the queue is weighted. It sits on the
    // collection's own page rather than in the settings because it is a fact about this collection
    // rather than about the queue — a shelf of long passages and a shelf of short ones are not
    // equally worth interrupting a reader for, and only the user knows which is which.
    if (detail.inReminders && detail.reminderState.isWeighted) {
        CollectionChanceRow(
            chance = detail.reminderState.dropChance(detail.collectionId),
            onChanceChange = { detail.reminderState.setDropChance(detail.collectionId, it) },
        )
        Spacer(Modifier.height(DETAIL_REGION_GAP))
    }

    // Progress is printed here rather than in a card of its own between the band and the members: it
    // is a fact about the collection rather than a way of changing it, so it sits with the other
    // facts, and a summary that owns a bordered box is one more thing to read past before the first
    // passage. Start sits here too, beside the counts it advances and the reset that undoes them.
    CollectionMemberToolbar(
        stats = detail.stats,
        sort = detail.memberSort,
        view = detail.memberView,
        gates =
            ToolbarGates(
                canStart = detail.stats.hasMembers,
                canResetProgress = detail.stats.totalRepeats > 0,
            ),
        actions =
            MemberToolbarActions(
                onStart = { navigation.onStartSession(detail.collectionId) },
                onRequestReset = { dialogs.pendingReset = true },
                onSortSelected = detail::setMemberSort,
                onViewSelected = detail::setMemberView,
            ),
    )
    if (detail.memberCount > 0) {
        Spacer(Modifier.height(DETAIL_REGION_GAP))
        AzkarOutlinedField(
            value = detail.memberQuery,
            onValueChange = detail::onMemberQueryChanged,
            placeholder = stringResource(Res.string.collectionDetailSearchHint),
            leadingIcon = Icons.Filled.Search,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * How likely the queue is to skip this collection, as a number the user can turn.
 *
 * A percentage rather than a sentence, because it is a weight: the queue's weighted mode drops a
 * passage when a die says so, and the die is this number. The settings hold the default for the
 * collections nobody has an opinion about; this is the opinion.
 *
 * The stepper is the same control the settings use, because it is the same bounded integer: a
 * chance cannot leave 0..100, and two buttons and a number cannot hold a value the queue would
 * refuse.
 */
@Composable
private fun CollectionChanceRow(
    chance: Int,
    onChanceChange: (Int) -> Unit,
) {
    SettingRow(
        label = stringResource(Res.string.collectionDetailActionReminderChance),
        hint = stringResource(Res.string.collectionDetailActionReminderChanceHint),
    ) {
        StepperControl(
            value = chance,
            onValueChange = onChanceChange,
            range = 0..100,
        )
    }
}

/**
 * The page body, split from [CollectionDetailScreen] so the shell, a screenshot harness or a future
 * test can hand it a state they already own instead of one built from the composition's scope.
 */
@Composable
internal fun CollectionDetailContent(
    collections: CollectionsCatalogue,
    detail: CollectionDetailState,
    navigation: DetailNavigation,
    showToast: ShowToast,
    modifier: Modifier = Modifier,
) {
    val dialogs = remember { DetailDialogState() }

    val toastTitle = stringResource(Res.string.collectionsToastTitle)
    val onBack = navigation.onBack

    DetailEffects(collections = collections, detail = detail, showToast = showToast, toastTitle = toastTitle)

    DetailLazyPage(
        collections = collections,
        detail = detail,
        dialogs = dialogs,
        navigation = navigation,
        showToast = showToast,
        modifier = modifier,
    )

    DetailDialogs(
        dialogs = dialogs,
        detail = detail,
        collections = collections,
        onBack = onBack,
    )
}

@Preview
@Composable
private fun CollectionDetailContentPreview() {
    AzkarThemePreview {
        val catalogue = rememberPreviewCatalogue()
        val reminders = previewReminders()
        CollectionDetailContent(
            collections = catalogue,
            detail = CollectionDetailState(catalogue, PREVIEW_COLLECTION_ID, reminders),
            navigation =
                DetailNavigation(
                    onBack = {},
                    onStartSession = {},
                    onOpenRemembrance = {},
                ),
            showToast = { _, _, _ -> },
        )
    }
}

/**
 * Everything this page has to do while it is on screen, gathered so the body reads as layout rather
 * than as wiring.
 *
 * The progress query is keyed on the row rather than on the collection: `loadProgress` is a no-op for
 * a row it has already read, so this costs a single query whether the collection itself has arrived
 * yet or is still loading behind it.
 */
@Composable
private fun DetailEffects(
    collections: CollectionsCatalogue,
    detail: CollectionDetailState,
    showToast: ShowToast,
    toastTitle: String,
) {
    LaunchedEffect(Unit) { collections.loadLabels() }
    LaunchedEffect(collections) { collections.loadData() }
    LaunchedEffect(detail.collectionId) { detail.loadProgress() }

    LaunchedEffect(collections.onNotice) {
        val key = (collections.onNotice as? CollectionNotice.Message)?.key ?: return@LaunchedEffect
        collections.onNotice = null
        showToast(toastTitle, getString(key.messageResource), key.priority)
    }
}

/**
 * What one member tile may ask the page to do.
 *
 * The clipboard and the "copied" wording are read here rather than passed in, so the caller builds
 * the four callbacks without carrying two values that only one of them needs.
 */
@Composable
internal fun memberActions(
    collections: CollectionsCatalogue,
    detail: CollectionDetailState,
    dialogs: DetailDialogState,
    onOpenRemembrance: (Long) -> Unit,
    showToast: ShowToast,
): MemberActions {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val toastTitle = stringResource(Res.string.collectionsToastTitle)
    val copiedLabel = stringResource(Res.string.libraryActionCopyCopied)
    return MemberActions(
        onRead = onOpenRemembrance,
        onToggleFavorite = collections::toggleFavorite,
        onCopy = { text ->
            scope.launch {
                clipboard.setPlainText(text)
                showToast(toastTitle, copiedLabel, NotificationPriority.LOW)
            }
        },
        onRequestEdit = { id ->
            dialogs.editingRemembrance = collections.allRemembrances.findById(id)
        },
        onRequestRemove =
            if (detail.isUncategorized || detail.isFavoritesCollection) {
                null
            } else {
                {
                    detail.toggleRemoveSelection(it)
                    dialogs.pendingRemoval = true
                }
            },
        onRequestDelete = { dialogs.pendingRemembranceDelete = it },
        onToggleReminder = { id ->
            collections.setReminderPick(id, !collections.isPicked(id))
        },
        isPicked = collections::isPicked,
    )
}

/**
 * Which of the page's dialogs is open.
 *
 * A holder rather than four loose locals because the dialogs are raised from the header, the tiles
 * and the footer and then answered in one place — four `mutableStateOf`s spread across two functions
 * is how a dialog ends up reachable from the wrong screen.
 */
@Stable
internal class DetailDialogState {
    var editor by mutableStateOf<CollectionEditorDraft?>(null)
    var pendingDelete by mutableStateOf(false)
    var pendingReset by mutableStateOf(false)
    var pendingRemoval by mutableStateOf(false)
    var pendingRemembranceDelete by mutableStateOf<Long?>(null)

    /**
     * The passage whose text is being corrected, or `null` when the editor is closed.
     *
     * The live model rather than a copy of its two strings, so reopening the dialog after a reload
     * shows what the catalogue now holds instead of what it held when the editor was first opened.
     */
    var editingRemembrance by mutableStateOf<Remembrance?>(null)
}

/** Every dialog this page can raise, each one still deciding for itself whether it is open. */
@Composable
private fun DetailDialogs(
    dialogs: DetailDialogState,
    detail: CollectionDetailState,
    collections: CollectionsCatalogue,
    onBack: () -> Unit,
) {
    if (detail.addPicker.isOpen) {
        AddRemembrancesDialog(detail = detail)
    }

    DetailRemembranceCreateHost(detail = detail, collections = collections)

    DetailConfirmations(dialogs = dialogs, detail = detail, collections = collections)

    val collection = detail.collection
    if (dialogs.pendingDelete && collection != null) {
        DetailDeleteHost(
            collection = collection,
            collections = collections,
            collectionId = detail.collectionId,
            onBack = onBack,
            onDismiss = { dialogs.pendingDelete = false },
        )
    }

    dialogs.editor?.let { draft ->
        DetailEditorHost(
            draft = draft,
            collections = collections,
            onDismiss = { dialogs.editor = null },
        )
    }

    val editing = dialogs.editingRemembrance
    val editingId = editing?.id
    if (editing != null && editingId != null) {
        RemembranceEditorDialog(
            remembrance = editing,
            onDismiss = { dialogs.editingRemembrance = null },
            onConfirm = { arabic, english ->
                dialogs.editingRemembrance = null
                collections.saveRemembranceTexts(editingId, arabic, english)
            },
        )
    }
}

@Composable
private fun DetailRemembranceCreateHost(
    detail: CollectionDetailState,
    collections: CollectionsCatalogue,
) {
    val draft = detail.remembranceDraft ?: return
    val tagLocale = AzkarTheme.locale
    RemembranceCreateDialog(
        draft = draft,
        availableTags = collections.shelfTags.map { TagOption.of(it, tagLocale) },
        onDismiss = { detail.remembranceDraft = null },
        onConfirm = { submitted ->
            detail.remembranceDraft = null
            collections.createRemembrance(
                collectionId = detail.collectionId,
                grade = submitted.grade,
                source = submitted.submittedSource,
                translations = submitted.submittedTranslations,
                tagNames = submitted.tags,
                tagLocale = tagLocale,
            )
        },
    )
}

/**
 * The two "are you sure" dialogs that ask before throwing something away.
 *
 * Both clear their own flag before acting, so closing the dialog and confirming it leave the page in
 * the same state either way — the alternative is a dialog that reappears after a successful
 * confirmation because the flag was only cleared on dismissal.
 */
@Composable
private fun DetailConfirmations(
    dialogs: DetailDialogState,
    detail: CollectionDetailState,
    collections: CollectionsCatalogue,
) {
    if (dialogs.pendingReset) {
        ConfirmProgressResetDialog(
            onDismiss = { dialogs.pendingReset = false },
            onConfirm = {
                dialogs.pendingReset = false
                detail.resetProgress()
            },
        )
    }

    val removing = detail.selectedToRemove
    if (dialogs.pendingRemoval && removing.isNotEmpty()) {
        ConfirmRemovalDialog(
            count = removing.size,
            onDismiss = {
                dialogs.pendingRemoval = false
                detail.clearRemoveSelection()
            },
            onConfirm = {
                dialogs.pendingRemoval = false
                detail.confirmRemove()
            },
        )
    }

    dialogs.pendingRemembranceDelete?.let { id ->
        ConfirmDeleteRemembranceDialog(
            onDismiss = { dialogs.pendingRemembranceDelete = null },
            onConfirm = {
                dialogs.pendingRemembranceDelete = null
                collections.deleteRemembrance(id)
            },
        )
    }
}

/**
 * What the title bar does, wired to the state and the open dialogs.
 *
 * Composable rather than a plain builder so it can read [AzkarTheme] for itself: the locale used to
 * name a duplicated collection was threaded in as an argument, and it was the sixth — the one that
 * made this the only signature on the page that could not be told apart from a mistake.
 */
@Composable
private fun headerActions(
    collections: CollectionsCatalogue,
    detail: CollectionDetailState,
    collection: RemembranceCollection?,
    dialogs: DetailDialogState,
    navigation: DetailNavigation,
): DetailActions {
    val activeLocale = AzkarTheme.locale
    return DetailActions(
        onBack = navigation.onBack,
        onEdit = { dialogs.editor = CollectionEditorDraft.edit(it) },
        onDuplicate = { target, newName ->
            dialogs.editor =
                CollectionEditorDraft.duplicate(collection = target, newName = newName, nameLocale = activeLocale)
        },
        onTogglePin = {
            if (collection != null) {
                collections.setDisplay(detail.collectionId, CollectionDisplayPreference.PINNED, !collection.isPinned)
            }
        },
        onToggleHome = {
            if (collection != null) {
                collections.setDisplay(detail.collectionId, CollectionDisplayPreference.ON_HOME, !collection.isOnHome)
            }
        },
        onToggleFavorite = {
            if (collection != null) {
                collections.setDisplay(
                    detail.collectionId,
                    CollectionDisplayPreference.FAVORITE,
                    !collection.isFavorite,
                )
            }
        },
        onDelete = { dialogs.pendingDelete = true },
        inReminders = detail.inReminders,
        onToggleReminders = { detail.setInReminders(!detail.inReminders) },
    )
}

/**
 * What the band's controls point at.
 *
 * Out of [CollectionRegion] so that the page above reads as a page — band, toolbar, search field —
 * rather than as twenty lines of wiring for a set of callbacks whose shape is already spelled out in
 * [CollectionHeroActions]. Two of them are conditional: the two shelves the app owns rather than the
 * user cannot have members added to them, and cannot be renamed either, so offering the control there
 * would offer one that silently does nothing.
 */
@Composable
private fun detailHeroActions(
    detail: CollectionDetailState,
    collection: RemembranceCollection,
    title: DetailActions,
    navigation: DetailNavigation,
): CollectionHeroActions =
    CollectionHeroActions(
        onBack = navigation.onBack,
        onAddMembers =
            if (detail.isUncategorized || detail.isFavoritesCollection) {
                null
            } else {
                detail.addPicker::open
            },
        onCreateRemembrance = {
            detail.remembranceDraft = detail.remembranceDraft ?: RemembranceCreateDraft(ARABIC_LOCALE)
        },
        onTogglePin = title.onTogglePin,
        onToggleReminder = { detail.setInReminders(!detail.inReminders) },
        menuItems =
            detailMenuItems(
                collection = collection,
                actions = title,
                isUncategorized = detail.isUncategorized,
                isFavoritesCollection = detail.isFavoritesCollection,
            ),
    )

// ---- members ------------------------------------------------------------------------------------------

/** What the page has under the toolbar: a wait, a message, or the members themselves. */
internal enum class DetailStage {
    LOADING,
    MISSING,
    EMPTY,
    MEMBERS,
}

/**
 * Which of those the page is in, decided without composing anything.
 *
 * The lazy container has to know before it builds a single item whether it is laying out cards or a
 * message, so this is a question about the state rather than a branch that happens to fall out of
 * composing it.
 *
 * Three messages and not four. There used to be a fourth for "a chip is hiding all of them", and it
 * was honest while it was reachable — a collection with twenty-nine remembrances and a filter
 * matching none of them has not run out of anything. The chip row is gone, so nothing can put the
 * grid in that state, and an unreachable branch with a message attached to it is a lie waiting for
 * the next change that makes it reachable again.
 */
internal fun detailStage(
    collections: CollectionsCatalogue,
    detail: CollectionDetailState,
): DetailStage {
    val collection = detail.collection
    return when {
        collections.loading && collections.allCollections.isEmpty() -> DetailStage.LOADING
        collection == null -> DetailStage.MISSING
        collection.members.isEmpty() -> DetailStage.EMPTY
        else -> DetailStage.MEMBERS
    }
}

/**
 * The page's waiting and empty states, as one full-width item.
 *
 * The messages take a floor rather than a height, so that a missing or empty collection still reads
 * as a panel in the middle of the page rather than as a line of text under the toolbar — and a floor
 * and not a height so that a taller window shows more of the message rather than more air. They can
 * no longer fill the space the members leave over: a lazy item is sized by its content, and a
 * `fillMaxSize` inside one would be measured against a height that does not exist yet.
 */
@Composable
internal fun DetailStatePanel(
    stage: DetailStage,
    detail: CollectionDetailState,
    footer: DetailFooterActions,
    modifier: Modifier = Modifier,
) {
    val panel = modifier.heightIn(min = DETAIL_EMPTY_STATE_MIN_HEIGHT).fillMaxWidth()
    when (stage) {
        DetailStage.LOADING -> CollectionsLoadingState(modifier = panel)

        DetailStage.MISSING ->
            CollectionsEmptyState(
                title = stringResource(Res.string.collectionDetailMissingTitle),
                hint = stringResource(Res.string.collectionDetailMissingHint),
                modifier = panel,
                action = {
                    EmptyStateAction(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        description = footer.backLabel,
                        onClick = footer.onBack,
                    )
                },
            )

        DetailStage.EMPTY ->
            CollectionsEmptyState(
                title = stringResource(Res.string.collectionDetailEmptyTitle),
                hint = stringResource(Res.string.collectionDetailEmptyHint),
                modifier = panel,
                action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (!detail.isUncategorized && !detail.isFavoritesCollection) {
                            EmptyStateAction(
                                icon = ActionIcons.addMembers,
                                description = stringResource(Res.string.collectionsActionAddMembers),
                                onClick = detail.addPicker::open,
                            )
                        }
                        EmptyStateAction(
                            icon = Icons.Filled.Add,
                            description = stringResource(Res.string.remembranceCreateActionOpen),
                            onClick = {
                                detail.remembranceDraft =
                                    detail.remembranceDraft ?: RemembranceCreateDraft(ARABIC_LOCALE)
                            },
                        )
                    }
                },
            )

        DetailStage.MEMBERS -> Unit
    }
}

/**
 * The one way back out of this page, carried as one value.
 *
 * The header it used to sit under is gone — the band's own "All Collections" link is the way back —
 * so all this carries now is the wording for the state where the collection itself is gone and the
 * empty state has to offer something to do instead.
 */
internal data class DetailFooterActions(
    val backLabel: String,
    val onBack: () -> Unit,
)

/**
 * What one member may ask the page to do, with the two collection-level actions decided here.
 *
 * Whether a tile offers "remove" is a fact about the collection rather than about the tile, so it is
 * answered at the point the tile is built instead of being a parameter threaded through the lazy
 * layout's item builder.
 */
internal fun memberTileActions(
    tile: RemembranceTile,
    actions: MemberActions,
): RemembranceTileActions =
    RemembranceTileActions(
        onRead = { actions.onRead(tile.id) },
        onToggleFavorite = { actions.onToggleFavorite(tile.id) },
        onCopy = { actions.onCopy(tile.primaryPreview) },
        onEdit = { actions.onRequestEdit(tile.id) },
        onRemoveFromCollection = actions.onRequestRemove?.let { onRemove -> { onRemove(tile.id) } },
        onDelete = { actions.onRequestDelete(tile.id) },
        onToggleReminder = { actions.onToggleReminder(tile.id) },
        inReminders = actions.isPicked(tile.id),
    )

/**
 * What the collection itself can be asked to do.
 *
 * Seven callbacks in a row is a signature nobody reads correctly; gathered into one value the band and
 * the menu can be handed them as a whole. The band's own pill reaches one of them directly — pinning —
 * and the rest come through [detailMenuItems].
 */
private data class DetailActions(
    val onBack: () -> Unit,
    val onEdit: (RemembranceCollection) -> Unit,
    val onDuplicate: (RemembranceCollection, String) -> Unit,
    val onTogglePin: () -> Unit,
    val onToggleHome: () -> Unit,
    val onToggleFavorite: () -> Unit,
    val onDelete: () -> Unit,
    /**
     * Whether the queue draws from this collection, and the way to change that.
     *
     * The two travel together because they are one fact and one way to change it: a menu entry that
     * says "remove from reminders" and a menu entry that says "add to reminders" are the same entry
     * read twice, and holding them apart is how a menu ends up offering to add a collection that is
     * already in the queue.
     */
    val inReminders: Boolean,
    val onToggleReminders: () -> Unit,
)

/**
 * Where this page can be asked to go next.
 *
 * The three ways out of the page — back to the shelf, into the session, into a single remembrance —
 * gathered into one value for the same reason [DetailActions] gathers the seven ways to change the
 * collection. They travel together into every dialog the page raises, so passing them as one value
 * keeps the shell's signature at three callbacks rather than five and the body's wiring at one line.
 */
internal data class DetailNavigation(
    val onBack: () -> Unit,
    val onStartSession: (Long) -> Unit,
    val onOpenRemembrance: (Long) -> Unit,
)

/** What one member tile can be asked to do. */
internal data class MemberActions(
    val onRead: (Long) -> Unit,
    val onToggleFavorite: (Long) -> Unit,
    val onCopy: (String) -> Unit,
    val onRequestEdit: (Long) -> Unit,
    val onRequestRemove: ((Long) -> Unit)?,
    val onRequestDelete: (Long) -> Unit,
    val onToggleReminder: (Long) -> Unit,
    val isPicked: (Long) -> Boolean,
)

/** The rename / duplicate form, dispatched on which of the two it was opened as. */
@Composable
private fun DetailEditorHost(
    draft: CollectionEditorDraft,
    collections: CollectionsCatalogue,
    onDismiss: () -> Unit,
) {
    val activeLocale = AzkarTheme.locale
    CollectionEditorDialog(
        draft = draft,
        // The same pool the shelf offers, so renaming a collection from its own page offers exactly the
        // tags that editing it from the shelf would. Both hosts read one derivation on the catalogue,
        // which is why neither of them sorts a list of tags.
        environment =
            CollectionEditorEnvironment(
                covers = collections.covers,
                availableTags = collections.shelfTags.map { TagOption.of(it, activeLocale) },
                onPickCover = { title -> collections.pickCoverImage(title) },
            ),
        onDismiss = onDismiss,
        onConfirm = { name, description, tagNames, coverImagePath, translations ->
            onDismiss()
            when (draft.mode) {
                // A collection is only created from the shelf, so the editor here never opens in this
                // mode; the branch exists rather than a `create` call that would put a new row on the
                // shelf while the user is reading someone else's collection.
                CollectionEditorMode.CREATE -> Unit
                CollectionEditorMode.EDIT ->
                    collections.editCollection(
                        id = draft.collectionId,
                        name = name,
                        description = description,
                        tagNames = tagNames,
                        coverImagePath = coverImagePath,
                        translations = translations,
                        nameLocale = draft.nameLocale,
                        tagLocale = activeLocale,
                    )

                // Only the name travels: a duplicate starts clean, and inheriting the cover would
                // leave two collections pointing at one file that either of them could delete.
                CollectionEditorMode.DUPLICATE ->
                    collections.duplicateCollection(
                        id = draft.collectionId,
                        newName = name,
                        nameLocale = draft.nameLocale,
                    )
            }
        },
    )
}

@Composable
private fun DetailDeleteHost(
    collection: RemembranceCollection,
    collections: CollectionsCatalogue,
    collectionId: Long,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmCollectionDeleteDialog(
        name = collection.name,
        onDismiss = onDismiss,
        onConfirm = {
            onDismiss()
            collections.deleteCollection(collectionId)
            // The page is about the row that just went away, so leaving it up would greet a
            // successful delete with "that collection is gone".
            onBack()
        },
    )
}

// ---- collection actions -------------------------------------------------------------------------------

/**
 * The collection's menu — everything here is about the collection rather than about a member.
 *
 * Rename and delete are offered only for a collection the user owns; a shipped collection's contents
 * come from the app's own catalogue, so the honest offer there is to copy it. Pinning, the Home rail
 * and the cover are offered everywhere, because all three are per-device preferences about how to
 * show a collection rather than claims about what is in it.
 *
 * Rename is kept inline rather than pulled into a helper of its own. It is the only entry whose
 * *availability* is conditional — the destructive delete at the end of the list is the other — so it is
 * written here where the reader can see both guards together and neither of them can drift away from the
 * rule that a shipped collection offers neither.
 */
@Composable
private fun detailMenuItems(
    collection: RemembranceCollection,
    actions: DetailActions,
    isUncategorized: Boolean,
    isFavoritesCollection: Boolean,
): List<AzkarMenuItem> {
    val duplicateName = stringResource(Res.string.collectionsDuplicateNameSuffix, collection.name)
    return buildList {
        if (!isUncategorized) {
            add(
                AzkarMenuItem(
                    label = stringResource(Res.string.collectionsActionEdit),
                    onClick = { actions.onEdit(collection) },
                    icon = ActionIcons.edit,
                ),
            )
        }
        addAll(
            identityMenuItems(
                collection = collection,
                actions = actions,
                duplicateName = duplicateName,
                isUncategorized = isUncategorized,
            ),
        )
        addAll(
            preferenceMenuItems(
                collection = collection,
                actions = actions,
            ),
        )
        if (!isUncategorized && !isFavoritesCollection) {
            add(
                AzkarMenuItem(
                    label = stringResource(Res.string.collectionsActionDelete),
                    onClick = actions.onDelete,
                    icon = ActionIcons.delete,
                ),
            )
        }
    }
}

/** What the collection *is*: another copy of it and its artwork. */
@Composable
private fun identityMenuItems(
    collection: RemembranceCollection,
    actions: DetailActions,
    duplicateName: String,
    isUncategorized: Boolean,
): List<AzkarMenuItem> =
    buildList {
        if (!isUncategorized) {
            add(
                AzkarMenuItem(
                    label = stringResource(Res.string.collectionsActionDuplicate),
                    onClick = { actions.onDuplicate(collection, duplicateName) },
                    icon = ActionIcons.duplicate,
                ),
            )
        }
    }

/**
 * Where this device shows the collection: on the shelf, on the Home rail, or starred.
 *
 * All three are per-device preferences about how to show a collection rather than claims about what is
 * in it, which is why they are offered for a shipped collection too, and why they belong together
 * ahead of the actions that would change the collection itself.
 */
@Composable
private fun preferenceMenuItems(
    collection: RemembranceCollection,
    actions: DetailActions,
): List<AzkarMenuItem> {
    val pinLabel =
        stringResource(
            if (collection.isPinned) Res.string.collectionsActionUnpin else Res.string.collectionsActionPin,
        )
    val homeLabel =
        stringResource(
            if (collection.isOnHome) {
                Res.string.collectionsActionHideFromHome
            } else {
                Res.string.collectionsActionShowOnHome
            },
        )
    val favoriteLabel =
        stringResource(
            if (collection.isFavorite) {
                Res.string.collectionsActionUnfavorite
            } else {
                Res.string.collectionsActionFavorite
            },
        )
    val reminderLabel =
        stringResource(
            if (actions.inReminders) {
                Res.string.collectionsActionRemoveFromReminders
            } else {
                Res.string.collectionsActionAddToReminders
            },
        )
    return listOf(
        AzkarMenuItem(
            label = pinLabel,
            onClick = actions.onTogglePin,
            icon = ActionIcons.pin(collection.isPinned),
        ),
        AzkarMenuItem(
            label = reminderLabel,
            onClick = actions.onToggleReminders,
            icon = if (actions.inReminders) ActionIcons.removeReminder else ActionIcons.addReminder,
        ),
        AzkarMenuItem(
            label = homeLabel,
            onClick = actions.onToggleHome,
            icon = ActionIcons.onHome(collection.isOnHome),
        ),
        AzkarMenuItem(
            label = favoriteLabel,
            onClick = actions.onToggleFavorite,
            icon = ActionIcons.favorite(collection.isFavorite),
        ),
    )
}

// ---- measurements --------------------------------------------------------------------------------------

/** The gap between the page's stacked regions, and the page's own inset. */
internal val DETAIL_PAGE_PADDING = 16.dp
private val DETAIL_REGION_GAP = 14.dp

/**
 * The gap between everything the page stacks.
 *
 * One gap rather than a per-region one, because the page's arrangement is what spaces its items now
 * that they are all in one container. It is the gap that used to be the band's-to-members gap, so it
 * is the number that moved rather than a new one.
 */
internal val MEMBER_GAP = 12.dp

/**
 * The shortest the loading, missing and empty panels may be.
 *
 * A floor rather than a fixed height, because the message inside is short and a fixed box would put
 * more air under it than the reader asked for; see the comment where it is applied for why these
 * states cannot fill what is left over.
 */
internal val DETAIL_EMPTY_STATE_MIN_HEIGHT = 320.dp

/**
 * The narrowest a member card may get before the grid drops a column.
 *
 * Not the width a card would look best at — the narrowest at which its three lines of passage and two
 * of translation still fit without the ellipsis landing in the same place on every card. That is what
 * makes a grid read as a grid, and it is why this is larger than the old tile's 220dp: the new card
 * spends its top 86dp on a wash rather than on a 28dp action row, and what is left has to hold the
 * same text in a narrower column.
 *
 * Handed to [fittedColumns] rather than given to `GridCells.Adaptive`, because the page fits the
 * columns itself: the grid's content padding is not part of its own measured width, so an adaptive
 * grid would be told it is wider than the space the cards actually get.
 */
internal val MEMBER_TILE_MIN_WIDTH = 250.dp

/**
 * Keys for the two full-width items that bracket the members.
 *
 * Named rather than left null because these items move — the band and the toolbar scroll away as
 * the members come up under them — and an unkeyed item is matched by position, which is the wrong
 * answer for content whose position is not its identity.
 */
internal const val REGION_KEY = "region"
internal const val INFO_KEY = "info"

/**
 * Every member card's height, so the grid reads as a grid.
 *
 * Not a guess at the tallest card — it is the card's budget, and the card's own constants add up to
 * it: 86dp of wash, then 14dp of padding, three lines of 17sp passage at the 24sp line box they
 * actually occupy, 6dp, two lines of 12sp translation at 17sp, a weighted gap that takes whatever is
 * left, the reference line, 8dp, the divider, 8dp, the 26dp footer row and 14dp of padding. That comes
 * to 282, and this is 292.
 *
 * Over-provisioned on purpose, and the extra room is the safe direction: the weighted gap inside the
 * card turns the slack into air above the divider, whereas coming up short pushes the footer off the
 * bottom of the card and clips it. A screenshot of this page is what caught the previous version at
 * 256, which was sized from nominal font sizes rather than from the line boxes Compose lays text out
 * in.
 *
 * The agreement is not to be edited on one side alone. The card clips rather than scrolls, so a line
 * added without raising this does not push the footer down — it deletes it.
 */
internal val MEMBER_TILE_HEIGHT = 292.dp
