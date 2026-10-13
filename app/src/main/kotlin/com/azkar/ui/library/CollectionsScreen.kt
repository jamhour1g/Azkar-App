package com.azkar.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.domain.model.RemembranceCollection
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.collectionsActionNew
import com.azkar.generated.resources.collectionsMemberCount
import com.azkar.generated.resources.collectionsNoMatchesHint
import com.azkar.generated.resources.collectionsNoMatchesTitle
import com.azkar.generated.resources.collectionsSearchEmptyHint
import com.azkar.generated.resources.collectionsSearchEmptyTitle
import com.azkar.generated.resources.collectionsToastTitle
import com.azkar.generated.resources.libraryActionCopy
import com.azkar.generated.resources.libraryActionCopyCopied
import com.azkar.generated.resources.libraryActionFavorite
import com.azkar.ui.components.ActionIcons
import com.azkar.ui.components.AzkarCard
import com.azkar.ui.components.TooltipIconButton
import com.azkar.ui.components.pressableClick
import com.azkar.ui.library.CollectionsState
import com.azkar.ui.library.model.RemembranceTile
import com.azkar.ui.library.model.TagOption
import com.azkar.ui.preview.rememberPreviewCatalogue
import com.azkar.ui.shell.NotificationPriority
import com.azkar.ui.shell.ShowToast
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.AzkarThemePreview
import com.azkar.ui.theme.CardSize
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The library's one and only view: the shelf.
 *
 * This replaced a browse pane and a detail pane sharing a row, which made the library's primary job
 * — finding azkar — a filtered table, and demoted collections to a dropdown of tags. The shelf puts
 * the collection first and the remembrance inside it, which is how a library is actually organised,
 * and the read dialog replaces the side pane that now has nothing to sit beside.
 *
 * @param onOpenCollection pushes the collection's own page. A callback rather than a navigator,
 * because this is a `ui/library` component and the shell owns the back stack.
 * @param showToast the shell's toast, used for the outcomes a mutation reports. The state cannot
 * raise one itself — the host owns it, and a library screen reaching for a service locator to do so
 * is exactly what the gateway interface removed.
 * @param catalogue the library's one shared read of the shelf, owned by the shell. The shelf's own
 * filters and sort are built on top of it rather than alongside it, because three library screens
 * opened on one session would otherwise each read every collection and every remembrance for
 * themselves, and the shelf pushed under a collection would be read again on the way back.
 */
@Composable
fun CollectionsScreen(
    onOpenCollection: (Long) -> Unit,
    onOpenRemembrance: (Long) -> Unit,
    onManageTags: () -> Unit,
    showToast: ShowToast,
    modifier: Modifier = Modifier,
    catalogue: CollectionsCatalogue,
) {
    val scope = rememberCoroutineScope()
    val state = remember(catalogue) { CollectionsState(scope = scope, catalogue = catalogue) }
    CollectionsScreenContent(
        state = state,
        onOpenCollection = onOpenCollection,
        onOpenRemembrance = onOpenRemembrance,
        onManageTags = onManageTags,
        showToast = showToast,
        modifier = modifier,
    )
}

/**
 * The screen body, split from [CollectionsScreen] so a test can drive it against a fake gateway with
 * a scope it controls rather than the composition's.
 */
@Composable
internal fun CollectionsScreenContent(
    state: CollectionsState,
    onOpenCollection: (Long) -> Unit,
    onOpenRemembrance: (Long) -> Unit,
    onManageTags: () -> Unit,
    showToast: ShowToast,
    modifier: Modifier = Modifier,
) {
    var editor by remember { mutableStateOf<CollectionEditorDraft?>(null) }
    var pendingDelete by remember { mutableStateOf<RemembranceCollection?>(null) }

    // The import needs a scope of its own for the write it kicks off, and one remembered here is
    // cancelled the moment the hub leaves the composition — which is the right answer for an import
    // the user abandoned halfway, since the gateway re-plans against the shelf anyway.
    val importScope = rememberCoroutineScope()
    val importer = remember(state, importScope) { CollectionImportState(state.catalogue, importScope) }

    val toastTitle = stringResource(Res.string.collectionsToastTitle)
    val copiedLabel = stringResource(Res.string.libraryActionCopyCopied)
    val clipboard = LocalClipboardManager.current

    ShelfEffects(state = state, showToast = showToast, toastTitle = toastTitle)

    LibraryHub(
        state = state,
        callbacks =
            ShelfCallbacks(
                onCreate = { editor = CollectionEditorDraft.create(AzkarTheme.locale) },
                onImport = { importer.open() },
                onOpen = { id ->
                    state.findCollection(id)
                    onOpenCollection(id)
                },
                onEdit = { collection -> editor = CollectionEditorDraft.edit(collection) },
                // The copy's name arrives pre-resolved: this lambda is not a composable, so it
                // could not call `stringResource` for itself.
                onDuplicate = { collection, newName ->
                    editor = CollectionEditorDraft.duplicate(collection, newName, AzkarTheme.locale)
                },
                onDelete = { collection -> pendingDelete = collection },
                onRead = onOpenRemembrance,
                onCopy = { text ->
                    clipboard.setText(AnnotatedString(text))
                    showToast(toastTitle, copiedLabel, NotificationPriority.LOW)
                },
                onManageTags = onManageTags,
            ),
        modifier = modifier,
    )

    editor?.let { draft -> CollectionEditorHost(draft, state, onDismiss = { editor = null }) }

    if (importer.isOpen) {
        ImportCollectionDialog(state = importer, onDismiss = importer::close)
    }

    pendingDelete?.let { collection ->
        CollectionDeleteHost(
            collection = collection,
            state = state,
            onDismiss = { pendingDelete = null },
        )
    }
}

@Preview
@Composable
private fun CollectionsScreenContentPreview() {
    AzkarThemePreview {
        val catalogue = rememberPreviewCatalogue()
        CollectionsScreenContent(
            state = CollectionsState(rememberCoroutineScope(), catalogue),
            onOpenCollection = {},
            onOpenRemembrance = {},
            onManageTags = {},
            showToast = { _, _, _ -> },
        )
    }
}

/**
 * Everything that has to happen while the hub is on screen: read the strings, read the shelf, and
 * shut the state holder down on the way out.
 *
 * The outcome is consumed and cleared rather than read during composition: a `LaunchedEffect` keyed
 * on it runs after the toast is raised, and leaving it set would re-raise the same sentence on
 * every recomposition. The cast is needed because `onNotice` is a snapshot property, and a `val`
 * local cannot smart-cast out of a property Kotlin may consider re-read.
 */
@Composable
private fun ShelfEffects(
    state: CollectionsState,
    showToast: ShowToast,
    toastTitle: String,
) {
    LaunchedEffect(Unit) { state.loadLabels() }
    LaunchedEffect(state) { state.loadData() }
    DisposableEffect(state) { onDispose { state.shutdown() } }

    LaunchedEffect(state.onNotice) {
        val key = (state.onNotice as? CollectionNotice.Message)?.key ?: return@LaunchedEffect
        state.onNotice = null
        showToast(toastTitle, getString(key.messageResource), key.priority)
    }
}

// ---- what the shelf can be asked to do ----------------------------------------------------------------

/**
 * Everything the shelf body can be asked to do, gathered into one value.
 *
 * Eight callbacks in a row would be a worse signature than a list of them: the reader of the call
 * site would have to count the arguments to work out which button does what.
 */
internal data class ShelfCallbacks(
    val onCreate: () -> Unit,
    val onImport: () -> Unit,
    val onOpen: (Long) -> Unit,
    val onEdit: (RemembranceCollection) -> Unit,
    val onDuplicate: (RemembranceCollection, String) -> Unit,
    val onDelete: (RemembranceCollection) -> Unit,
    val onRead: (Long) -> Unit,
    val onCopy: (String) -> Unit,
    /** Opens the Manage tags screen. Not a shelf action, but it is reached from the shelf. */
    val onManageTags: () -> Unit,
)

/**
 * Whatever the shelf is currently showing.
 *
 * Loading wins over everything else, because a shelf that has not arrived yet looks exactly like an
 * empty one — and telling a user their library is empty while it is still being read is the one
 * answer that cannot be taken back.
 *
 * The height is this composable's to give rather than the caller's to weigh, because the column
 * around it scrolls and a scrolling parent has no bounded height to hand out. The three states that
 * want to fill whatever they are given would otherwise be measured against infinity.
 */
@Composable
internal fun ShelfBody(
    state: CollectionsState,
    callbacks: ShelfCallbacks,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        when {
            state.loading && state.allCollections.isEmpty() ->
                CollectionsLoadingState(modifier = Modifier.height(STATE_BOX_HEIGHT))

            state.isSearchingRemembrances ->
                SearchResultsPane(state = state, onRead = callbacks.onRead, onCopy = callbacks.onCopy)

            state.isEmpty ->
                CollectionsEmptyState(
                    title = EmptyShelfTitle,
                    hint = EmptyShelfHint,
                    modifier = Modifier.height(STATE_BOX_HEIGHT),
                    action = {
                        EmptyStateAction(
                            icon = Icons.Filled.Add,
                            description = stringResource(Res.string.collectionsActionNew),
                            onClick = callbacks.onCreate,
                        )
                    },
                )

            state.visibleCollections.isEmpty() ->
                CollectionsEmptyState(
                    title = stringResource(Res.string.collectionsNoMatchesTitle),
                    hint = stringResource(Res.string.collectionsNoMatchesHint),
                    modifier = Modifier.height(STATE_BOX_HEIGHT),
                )

            else -> CollectionShelf(state = state, callbacks = callbacks)
        }
    }
}

@Preview
@Composable
private fun ShelfBodyPreview() {
    AzkarThemePreview {
        ShelfBody(
            state = CollectionsState(rememberCoroutineScope(), rememberPreviewCatalogue()),
            callbacks =
                ShelfCallbacks(
                    onCreate = {},
                    onImport = {},
                    onOpen = {},
                    onEdit = {},
                    onDuplicate = { _, _ -> },
                    onDelete = {},
                    onRead = {},
                    onCopy = {},
                    onManageTags = {},
                ),
        )
    }
}

// ---- dialogs ------------------------------------------------------------------------------------------

/** The create / rename / duplicate form, dispatched on which of the three it was opened as. */
@Composable
private fun CollectionEditorHost(
    draft: CollectionEditorDraft,
    state: CollectionsState,
    onDismiss: () -> Unit,
) {
    CollectionEditorDialog(
        draft = draft,
        // The shelf's own labels, not every tag the database knows about: a collection lives on the
        // shelf, so the only tags worth offering are the ones its neighbours already carry. Anything
        // else is still reachable through the panel's own "create a tag" row.
        environment =
            CollectionEditorEnvironment(
                covers = state.covers,
                availableTags = state.shelfLabels.map { TagOption.of(it, AzkarTheme.locale) },
                // The chooser runs the platform's own file dialog, which blocks, and the copy afterwards
                // touches the filesystem. Both are what the dialog's `onPickCover` suspending shape is
                // for: it awaits the whole thing and reports a cancelled pick as `null` rather than
                // leaving the caller to guess which of "no file chosen" and "chosen but unreadable" it
                // was handed.
                onPickCover = { title -> state.pickCoverImage(title) },
            ),
        onDismiss = onDismiss,
        onConfirm = { name, description, tagNames, coverImagePath, translations ->
            onDismiss()
            when (draft.mode) {
                CollectionEditorMode.CREATE ->
                    state.createCollection(
                        name = name,
                        description = description,
                        tagNames = tagNames,
                        coverImagePath = coverImagePath,
                        translations = translations,
                        nameLocale = draft.nameLocale,
                        tagLocale = AzkarTheme.locale,
                        onCreated = {},
                    )

                CollectionEditorMode.EDIT ->
                    state.editCollection(
                        id = draft.collectionId,
                        name = name,
                        description = description,
                        tagNames = tagNames,
                        coverImagePath = coverImagePath,
                        translations = translations,
                        nameLocale = draft.nameLocale,
                    )

                CollectionEditorMode.DUPLICATE ->
                    state.duplicateCollection(
                        id = draft.collectionId,
                        newName = name,
                        nameLocale = draft.nameLocale,
                    )
            }
        },
    )
}

@Composable
private fun CollectionDeleteHost(
    collection: RemembranceCollection,
    state: CollectionsState,
    onDismiss: () -> Unit,
) {
    ConfirmCollectionDeleteDialog(
        name = collection.name,
        onDismiss = onDismiss,
        onConfirm = {
            onDismiss()
            // A row on the shelf always has an id, so this cannot be null — and if it ever were,
            // failing loudly beats deleting whatever happens to be at that index.
            state.deleteCollection(requireNotNull(collection.id) { "A shelf collection has no identifier" })
        },
    )
}

// ---- cross-collection search ------------------------------------------------------------------------

/**
 * What the hub's search box found, grouped by the collection each hit came from.
 *
 * The grouping is not decoration: the same remembrance is legitimately in six collections, and six
 * identical flat rows are indistinguishable from six different results. The header is also the way
 * back into the collection the hit was found in.
 *
 * A plain column rather than a `LazyColumn`, because the shelf column around it is the scroll
 * container now and a second vertical scroll inside one has no bounded height to measure against.
 * The results are bounded by the catalogue anyway, so there was never a list long enough to need
 * recycling.
 */
@Composable
private fun SearchResultsPane(
    state: CollectionsState,
    onRead: (Long) -> Unit,
    onCopy: (String) -> Unit,
) {
    val groups = state.searchGroups
    if (groups.isEmpty()) {
        CollectionsEmptyState(
            title = stringResource(Res.string.collectionsSearchEmptyTitle),
            hint = stringResource(Res.string.collectionsSearchEmptyHint),
            modifier = Modifier.height(STATE_BOX_HEIGHT),
        )
        return
    }
    val colors = AzkarTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        groups.forEach { group ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = group.collectionName,
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text =
                        pluralStringResource(
                            Res.plurals.collectionsMemberCount,
                            group.results.size,
                            group.results.size,
                        ),
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                group.results.forEach { hit ->
                    val tile = state.presenter.toTile(hit, state.membershipCounts.collectionsIn(hit.id ?: -1L))
                    SearchResultRow(
                        tile = tile,
                        onRead = { onRead(tile.id) },
                        onToggleFavorite = { state.toggleFavorite(tile.id) },
                        onCopy = { onCopy(tile.primaryPreview) },
                    )
                }
            }
        }
    }
}

/** One hit: enough of the remembrance to recognise it, and the two things you can do to it. */
@Composable
private fun SearchResultRow(
    tile: RemembranceTile,
    onRead: () -> Unit,
    onToggleFavorite: () -> Unit,
    onCopy: () -> Unit,
) {
    val colors = AzkarTheme.colors
    AzkarCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .pressableClick(scale = 1f, onClick = onRead),
        size = CardSize.SM,
        shape = RoundedCornerShape(12.dp),
        contentPadding = 12.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tile.category,
                    color = colors.primary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = tile.primaryPreview,
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            TooltipIconButton(
                icon = ActionIcons.favorite(tile.isFavorite),
                tooltip = stringResource(Res.string.libraryActionFavorite),
                onClick = onToggleFavorite,
                favorite = tile.isFavorite,
            )
            TooltipIconButton(
                icon = Icons.Filled.ContentCopy,
                tooltip = stringResource(Res.string.libraryActionCopy),
                onClick = onCopy,
            )
        }
    }
}

/**
 * How tall the shelf's own states are drawn inside the scrolling column.
 *
 * A fixed height rather than a fill, because the column this sits in scrolls: a child asked to fill
 * a vertically unbounded parent has nothing to fill. Two card rows' worth is enough to read a title,
 * a sentence of explanation and the button that acts on it.
 */
private val STATE_BOX_HEIGHT = 300.dp
