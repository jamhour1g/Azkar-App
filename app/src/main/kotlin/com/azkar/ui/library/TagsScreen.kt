package com.azkar.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.domain.model.TagSummary
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.collectionsToastTitle
import com.azkar.generated.resources.tagsActionDelete
import com.azkar.generated.resources.tagsActionMore
import com.azkar.generated.resources.tagsActionNew
import com.azkar.generated.resources.tagsActionRename
import com.azkar.generated.resources.tagsActionRestore
import com.azkar.generated.resources.tagsActionSave
import com.azkar.generated.resources.tagsActionViewUsage
import com.azkar.generated.resources.tagsDeletedTitle
import com.azkar.generated.resources.tagsDialogNewHint
import com.azkar.generated.resources.tagsDialogNewTitle
import com.azkar.generated.resources.tagsDialogRenameCurrent
import com.azkar.generated.resources.tagsDialogRenameNote
import com.azkar.generated.resources.tagsDialogRenameTitle
import com.azkar.generated.resources.tagsEmptyDeletedHint
import com.azkar.generated.resources.tagsEmptyDeletedTitle
import com.azkar.generated.resources.tagsEmptyHint
import com.azkar.generated.resources.tagsEmptyTitle
import com.azkar.generated.resources.tagsFooterShowing
import com.azkar.generated.resources.tagsPageLabel
import com.azkar.generated.resources.tagsPageNext
import com.azkar.generated.resources.tagsPagePrevious
import com.azkar.generated.resources.tagsRowAdded
import com.azkar.generated.resources.tagsSearchEmptyHint
import com.azkar.generated.resources.tagsSearchEmptyTitle
import com.azkar.generated.resources.tagsUnusedLabel
import com.azkar.generated.resources.tagsUsageCollections
import com.azkar.generated.resources.tagsUsageRemembrances
import com.azkar.ui.components.AzkarCard
import com.azkar.ui.components.AzkarIcons
import com.azkar.ui.components.AzkarMenuItem
import com.azkar.ui.components.AzkarOverflowMenu
import com.azkar.ui.components.IconActionButton
import com.azkar.ui.components.SecondaryButton
import com.azkar.ui.library.data.CollectionPresenter
import com.azkar.ui.shell.ShowToast
import com.azkar.ui.theme.AzkarSpacing
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.AzkarThemePreview
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * `ui/library/TagsScreen.kt` — the tag vocabulary, and the only place a tag can be renamed or retired.
 *
 * Reached from the shelf rather than from Settings, which is the one placement decision this screen
 * rests on. A tag is not a setting and not decoration: it is a word the library is sorted by, and
 * the shelf is where a person notices one is wrong. Opening a second surface to look after the first
 * shelf's own vocabulary would have been the alternative, and it is the arrangement the rest of the
 * app already avoids.
 *
 * It is a destination pushed on top of the shelf rather than a dialog over it, so the shelf's
 * filters, its search and its scroll position are still there underneath when [onBack] returns —
 * which matters more here than elsewhere, because the most likely reason to open this screen is
 * noticing a wrong label while looking at a narrowed shelf.
 *
 * The band at the top, the counts on every row and the drill-down behind *View usage* are the three
 * ways this page answers the question it exists for. "Is this label right?" cannot be answered by a
 * list of names, and "is it safe to retire?" cannot be answered without saying what it is attached
 * to — so the counts are on the row, the band totals them, and the one row worth arguing about can
 * be opened up.
 *
 * @param onBack pops the stack. The shelf underneath keeps what it was showing.
 * @param showToast the shell's toast. The state cannot raise one itself; see [TagsState.onNotice].
 * @param onOpenCollection opens a collection from the usage panel. Pushed onto the stack rather than
 * replacing this page, so the panel is still open underneath it; [TagsState.closeUsage] says so.
 * @param onOpenRemembrance opens a passage from the usage panel, the same way.
 */
@Composable
fun TagsScreen(
    state: TagsState,
    onBack: () -> Unit,
    showToast: ShowToast,
    onOpenCollection: (Long) -> Unit,
    onOpenRemembrance: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val targets = remember { TagDialogTargets() }

    TagScreenEffects(state = state, showToast = showToast)

    // One background, one inset, one scroll container — the shape every other library screen draws,
    // rather than the capped-and-centred one `SettingsScreen` uses. This is a page of the library and
    // not a modal over it: the shelf it was pushed on top of runs full-bleed to the window edges, and a
    // narrower page sitting in the middle of it reads as a dialog that forgot to say what it was for.
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(AzkarTheme.colors.background)
                .padding(PAGE_PADDING)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AzkarSpacing.section),
    ) {
        TagsHero(
            stats = state.stats,
            onBack = onBack,
            onCreate = targets::openCreate,
        )
        TagsToolbar(state = state)
        TagListCard(
            state = state,
            onRename = targets::openRename,
            onDelete = targets::openDelete,
        )
        TagsFooter(state = state)
    }

    TagDialogs(
        state = state,
        targets = targets,
        onOpenCollection = onOpenCollection,
        onOpenRemembrance = onOpenRemembrance,
    )
}

/**
 * Which dialog is open, and about which row.
 *
 * Composition state rather than state-holder state: which row is being renamed is a fact about this
 * screen being open, and holding it in a holder the shell outlives would leave a dialog standing
 * over the shelf after a trip somewhere else and back. The three fields are kept together because
 * they are three answers to one question — what is being edited right now — and answering it through
 * three separate `remember`s is what makes the next row of this screen start looking like a
 * bookkeeping exercise.
 */
@Stable
private class TagDialogTargets {
    var renaming: TagSummary? by mutableStateOf(null)
        private set

    var deleting: TagSummary? by mutableStateOf(null)
        private set

    var creating: Boolean by mutableStateOf(false)
        private set

    fun openCreate() {
        creating = true
    }

    fun openRename(tag: TagSummary) {
        renaming = tag
    }

    fun openDelete(tag: TagSummary) {
        deleting = tag
    }

    fun closeName() {
        creating = false
        renaming = null
    }

    fun closeDelete() {
        deleting = null
    }
}

/**
 * Every overlay this screen can raise, and nothing else.
 *
 * Drawn after the page rather than inside it, which is the arrangement `Dialog` expects: a dialog is
 * its own window, so nesting it in the scroll column would tie its position to wherever the page
 * happened to be scrolled.
 *
 * The usage panel is here too rather than in the page body because it opens *from* a row and is a
 * window of its own; the only thing the page owes it is the two callbacks it navigates with, and
 * closing it before either one leaves is what keeps the stack from growing a dead panel underneath
 * the destination.
 */
@Composable
private fun TagDialogs(
    state: TagsState,
    targets: TagDialogTargets,
    onOpenCollection: (Long) -> Unit,
    onOpenRemembrance: (Long) -> Unit,
) {
    if (targets.creating) {
        TagNameDialog(
            title = stringResource(Res.string.tagsDialogNewTitle),
            initialName = "",
            confirmLabel = stringResource(Res.string.tagsActionNew),
            note = stringResource(Res.string.tagsDialogNewHint),
            onSubmit = { name, translations -> state.create(name, translations, AzkarTheme.locale) },
            onDismiss = targets::closeName,
        )
    }

    targets.renaming?.let { tag ->
        TagNameDialog(
            title = stringResource(Res.string.tagsDialogRenameTitle),
            // The stored name, not the one on screen. The field below is filled with it, so the
            // "Currently: X" line and the field it sits over have to be reading the same word or one
            // of them is describing a tag that does not exist.
            initialName = tag.name,
            confirmLabel = stringResource(Res.string.tagsActionSave),
            subtitle = stringResource(Res.string.tagsDialogRenameCurrent, tag.name),
            note = stringResource(Res.string.tagsDialogRenameNote),
            initialTranslations = tag.translationEntries(),
            initialNameLocale = tag.nameLocale,
            onSubmit = { name, translations -> state.rename(tag.id, name, translations, tag.nameLocale) },
            onDismiss = targets::closeName,
        )
    }

    targets.deleting?.let { tag ->
        TagDeleteDialog(
            tag = tag,
            onDismiss = targets::closeDelete,
            onConfirm = {
                targets.closeDelete()
                state.softDelete(tag.id)
            },
        )
    }

    TagUsagePanel(
        state = state,
        onOpenCollection = { id ->
            state.closeUsage()
            onOpenCollection(id)
        },
        onOpenRemembrance = { id ->
            state.closeUsage()
            onOpenRemembrance(id)
        },
    )
}

/**
 * Reads the vocabulary once on the way in, and raises whatever the state last reported.
 *
 * The notice is consumed and cleared rather than read during composition, for the reason it is done
 * that way on every library screen: a `LaunchedEffect` keyed on it fires after the toast is raised,
 * and leaving it set would raise the same sentence again on the next recomposition.
 */
@Composable
private fun TagScreenEffects(
    state: TagsState,
    showToast: ShowToast,
) {
    val toastTitle = stringResource(Res.string.collectionsToastTitle)

    LaunchedEffect(Unit) { state.load() }

    LaunchedEffect(state.onNotice) {
        val key = (state.onNotice as? CollectionNotice.Message)?.key ?: return@LaunchedEffect
        state.onNotice = null
        showToast(toastTitle, getString(key.messageResource), key.priority)
    }
}

/**
 * The tags, in one card, with a rule between rows.
 *
 * One card rather than a row per card: the rows are now four columns wide, and a card each would put
 * a gutter between them that made four columns of unrelated facts look like four unrelated screens.
 * A single surface is also what makes the page a *list* — which is what a list of names is — instead
 * of a gallery of cards.
 *
 * A plain column rather than a `LazyColumn` because the column around it is the scroll container and
 * a second vertical scroll inside one has no bounded height to measure against. What bounds the list
 * is [TagsState.pagedRows]: at most [TAGS_PAGE_SIZE] rows are ever composed, which is the reason a
 * list this size can be a plain column at all.
 *
 * The empty states are told apart because they are three different things a user needs to know:
 * nothing has ever been typed, something has but the search missed all of it, and there are rows but
 * the read has not landed. Telling someone they have no tags while the search is what hid them is
 * the one answer that cannot be taken back. The deleted filter gets its own answer for the same
 * reason: the library can be full of tags and still show nothing while the deleted half is open.
 */
@Composable
private fun TagListCard(
    state: TagsState,
    onRename: (TagSummary) -> Unit,
    onDelete: (TagSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.loading ->
            CollectionsLoadingState(
                modifier = modifier.height(STATE_BOX_HEIGHT),
            )
        state.searchMissedEverything ->
            TagEmptyState(
                title = stringResource(Res.string.tagsSearchEmptyTitle, state.query.trim()),
                hint = stringResource(Res.string.tagsSearchEmptyHint),
                modifier = modifier,
            )
        state.noDeletedTags ->
            TagEmptyState(
                title = stringResource(Res.string.tagsEmptyDeletedTitle),
                hint = stringResource(Res.string.tagsEmptyDeletedHint),
                modifier = modifier,
            )
        state.isEmpty ->
            TagEmptyState(
                title = stringResource(Res.string.tagsEmptyTitle),
                hint = stringResource(Res.string.tagsEmptyHint),
                modifier = modifier,
            )
        else -> TagRows(state = state, onRename = onRename, onDelete = onDelete, modifier = modifier)
    }
}

/**
 * The card, its rules, and the label that introduces the deleted rows.
 *
 * The rules are drawn between rows rather than around each, so the card has one silhouette instead of
 * twelve, and the only text inside it that is not a tag is the deleted label — placed on its own rule
 * so the change of kind is something the eye finds before it reads.
 *
 * The label is left out when the deleted filter has taken the live tags out, because it then heads a
 * list that is entirely one kind: a heading over rows that are all the same thing says the rows
 * before it were something else, and in that view they are not on screen.
 */
@Composable
private fun TagRows(
    state: TagsState,
    onRename: (TagSummary) -> Unit,
    onDelete: (TagSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    val rows = state.pagedRows
    val onlyDeleted = rows.isNotEmpty() && rows.all { it.isDeleted }
    AzkarCard(modifier = modifier.fillMaxWidth(), contentPadding = 0.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
            rows.forEachIndexed { index, tag ->
                val firstOfItsKind = index == 0 || rows[index - 1].isDeleted != tag.isDeleted
                if (!firstOfItsKind || tag.isDeleted) {
                    HorizontalDivider(color = colors.border)
                }
                if (tag.isDeleted && firstOfItsKind && !onlyDeleted) {
                    TagSectionLabel(text = stringResource(Res.string.tagsDeletedTitle))
                }
                if (tag.isDeleted) {
                    DeletedTagRow(tag = tag, onRestore = { state.restore(tag.id) })
                } else {
                    TagRow(
                        tag = tag,
                        onRename = { onRename(tag) },
                        onDelete = { onDelete(tag) },
                        onUsage = { state.openUsage(tag.id) },
                    )
                }
            }
        }
    }
}

/**
 * One tag: its mark, its name, what it is on, when it arrived, and the three things done to it.
 *
 * The counts are the reason this row is four columns wide. "How many shelves use this label" is the
 * question a delete raises, and answering it here means the confirmation is agreeing with something
 * already on screen rather than introducing a figure for the first time. The *View usage* button is
 * the third step of the same argument: the row says how many, and this is what says which.
 *
 * The pencil beside the button and the delete behind the kebab are both deliberate. Renaming is the
 * edit somebody makes by noticing the word is wrong, and a word is read in a row, so its control is
 * in the row; deleting is destructive and rate-limited by how far away it is, which the kebab already
 * is on every other row in the app.
 */
@Composable
private fun TagRow(
    tag: TagSummary,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onUsage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    val locale = AzkarTheme.locale
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = ROW_PADDING, vertical = ROW_PADDING_VERTICAL),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TagAvatar(tag)
        Spacer(Modifier.width(ROW_GAP))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tag.displayName(locale),
                color = colors.textPrimary,
                fontSize = ROW_NAME_SP,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            UsageLine(
                tag = tag,
                modifier = Modifier.padding(top = ROW_DETAIL_GAP),
            )
        }
        Spacer(Modifier.width(SMALL_GAP))
        AddedColumn(tag = tag)
        Spacer(Modifier.width(SMALL_GAP))
        SecondaryButton(
            text = stringResource(Res.string.tagsActionViewUsage),
            onClick = onUsage,
            leadingIcon = Icons.Outlined.Visibility,
        )
        IconActionButton(
            icon = Icons.Outlined.Edit,
            contentDescription = stringResource(Res.string.tagsActionRename),
            onClick = onRename,
            tint = colors.textSecondary,
        )
        // Rename is the pencil beside this and is not repeated here. It used to be, and the row
        // carried two controls that opened the same dialog — which reads as one of them being
        // broken rather than as the choice being offered twice. What is left in here is the action
        // that has no glyph out here: a menu of one is a button that happens to open a list.
        AzkarOverflowMenu(
            icon = Icons.Filled.MoreVert,
            contentDescription = stringResource(Res.string.tagsActionMore),
            items =
                listOf(
                    AzkarMenuItem(
                        label = stringResource(Res.string.tagsActionDelete),
                        onClick = onDelete,
                        icon = Icons.Filled.Delete,
                    ),
                ),
        )
    }
}

/**
 * The coloured square, and why it is that colour.
 *
 * The glyph is the one the *library* already gives this category — [CollectionPresenter.iconKeyFor]
 * over the same table the shelf's chips use — so a tag looks the same here as it does on the chip
 * that filters by it, and the two could not be maintained apart without one of them lying.
 *
 * The fill is not in the table, and is hashed from the name instead. Nothing picks a colour for a tag,
 * so a colour taken from the row's position would repaint every tag below it each time one is deleted
 * or reordered; hashing the name means a tag is the same square on every read, in every sort, and in
 * every session — which is the only property a colour can have here without becoming a second meaning.
 *
 * Both of them read `tag.name`, the stored name, rather than the name the row shows beside it. That
 * is the whole reason a tag keeps two: the stored name is the tag's identity — it is what the unique
 * index is built on and what a rename collides against — so a mark drawn from the reader's language
 * would repaint itself the moment somebody switched language, and a tag with two names would become
 * two colours. The row next to it is for reading; this one is for recognition.
 */
@Composable
private fun TagAvatar(
    tag: TagSummary,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    Box(
        modifier =
            modifier
                .size(AVATAR_SIZE)
                .clip(RoundedCornerShape(AVATAR_SHAPE))
                .background(tagAvatarBrush(tag.name)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = iconForCollection(CollectionPresenter.iconKeyFor(tag.name)),
            contentDescription = null,
            tint = colors.primaryStrong.copy(alpha = AVATAR_GLYPH_ALPHA),
            modifier = Modifier.size(AVATAR_GLYPH),
        )
    }
}

/**
 * The wash behind a tag's avatar tile, dealt out of the theme's own surfaces by the tag's slot.
 *
 * Built rather than declared for the same reason [mosaicBrush] is built rather than a table of eight
 * hexes: every fill on this tile is a colour the palette already decided, which is what lets it change
 * with the theme instead of sitting on it as a stain. [mosaicBrush] is the rule being followed — the
 * same material at several tints, keyed on an id so the choice is stable, and never a hue the theme
 * has not already chosen for something else.
 *
 * `internal` rather than private because the tag dialogs and the usage panel put the same tile beside
 * their titles. A mark drawn from a different rule would be the one place on the screen that did not
 * look like the tag it was about.
 */
@Composable
internal fun tagAvatarBrush(name: String): Brush {
    val colors = AzkarTheme.colors
    return when (tagAvatarSlot(name, AVATAR_SLOTS)) {
        0 -> Brush.linearGradient(listOf(colors.azkarCardStart, colors.azkarCardEnd))
        1 -> Brush.linearGradient(listOf(colors.favoriteCardStart, colors.favoriteCardEnd))
        2 -> Brush.linearGradient(listOf(colors.brandTileStart, colors.brandTileEnd))
        3 -> Brush.linearGradient(listOf(colors.nextPrayerSurface, colors.azkarCardEnd))
        4 -> Brush.linearGradient(listOf(colors.toastMediumStart, colors.toastMediumEnd))
        5 -> Brush.linearGradient(listOf(colors.toastLowStart, colors.toastLowEnd))
        6 -> Brush.linearGradient(listOf(colors.reminderSection, colors.cardBackground))
        else -> Brush.linearGradient(listOf(colors.artPillSurface, colors.artBadgeSurface))
    }
}

/** How many washes [tagAvatarBrush] deals between tags. */
private const val AVATAR_SLOTS = 8

/**
 * What a tag is attached to, in the user's language, and in words when there is nothing.
 *
 * The whole phrase is painted in the accent rather than only the figures, and that is forced by the
 * translation: "2 collections · 40 remembrances" is two words the reader scans for, but splitting the
 * count from its noun to colour just the number would pin one word order into the layout and read
 * wrong the moment the user's language puts anything else in between.
 *
 * Joined with a middot rather than the word "and" because the user's language may lay the line out
 * right to left, and a separator that is a symbol is one nothing has to reorder.
 */
@Composable
private fun UsageLine(
    tag: TagSummary,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    if (tag.usageCount == 0) {
        Text(
            text = stringResource(Res.string.tagsUnusedLabel),
            color = colors.textSecondary,
            fontSize = ROW_DETAIL_SP,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
        return
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text =
                pluralStringResource(
                    Res.plurals.tagsUsageCollections,
                    tag.collectionCount,
                    tag.collectionCount,
                ),
            color = colors.primary,
            fontSize = ROW_DETAIL_SP,
            maxLines = 1,
        )
        Text(text = MIDDOT, color = colors.textSecondary, fontSize = ROW_DETAIL_SP)
        Text(
            text =
                pluralStringResource(
                    Res.plurals.tagsUsageRemembrances,
                    tag.remembranceCount,
                    tag.remembranceCount,
                ),
            color = colors.primary,
            fontSize = ROW_DETAIL_SP,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * When the tag arrived, or nothing at all when it has no date.
 *
 * A tag written before the column existed has no creation time, and printing "Added —" for it would
 * be a fact the database does not hold. The column is omitted instead, so the row is one line short
 * rather than one line wrong.
 *
 * The date is formatted against the shell's locale rather than the machine's, so a UI in Arabic reads
 * its dates in Arabic, and against the current zone rather than UTC — the tag was added *here*.
 */
@Composable
private fun AddedColumn(
    tag: TagSummary,
    modifier: Modifier = Modifier,
) {
    val added = tag.createdAt ?: return
    val colors = AzkarTheme.colors
    Row(
        modifier = modifier.widthIn(max = ADDED_WIDTH),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = AzkarIcons.Calendar,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(ADDED_GLYPH),
        )
        Spacer(Modifier.width(SMALL_GAP))
        Text(
            text =
                stringResource(
                    Res.string.tagsRowAdded,
                    addedFormat().format(added.atZone(ZoneId.systemDefault())),
                ),
            color = colors.textSecondary,
            fontSize = ROW_DETAIL_SP,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The soft-deleted rows.
 *
 * Drawn by the same loop as the live ones so they share the card's rules, and held apart by the
 * label rather than by a second card: these are the same rows in a different state, and a user
 * looking at both at once is looking at one list. They carry no counts and no *View usage* button
 * because a deleted tag is on nothing by definition — [TagSummary.isDeleted] is what the shelves read
 * to decide that, and a figure here would contradict them.
 *
 * The avatar is still here, though. A name with no mark reads as an orphan, and the mark is the one
 * thing that says which label this is — on a shelf's chip, in the editor's picker, and here. Losing it
 * in the deleted section would mean the row you are deciding whether to put back is the only row on the
 * page you cannot recognise at a glance.
 */
@Composable
private fun DeletedTagRow(
    tag: TagSummary,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    val locale = AzkarTheme.locale
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = ROW_PADDING, vertical = ROW_PADDING_VERTICAL),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TagAvatar(tag)
        Spacer(Modifier.width(SMALL_GAP))
        Text(
            text = tag.displayName(locale),
            color = colors.textSecondary,
            fontSize = ROW_NAME_SP,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        AzkarOverflowMenu(
            icon = Icons.Filled.MoreVert,
            contentDescription = stringResource(Res.string.tagsActionRestore),
            items =
                listOf(
                    AzkarMenuItem(
                        label = stringResource(Res.string.tagsActionRestore),
                        onClick = onRestore,
                        icon = Icons.Filled.RestoreFromTrash,
                    ),
                ),
        )
    }
}

/** The label that introduces the deleted rows inside the card. */
@Composable
private fun TagSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = AzkarTheme.colors.textSecondary,
        fontSize = ROW_SECTION_SP,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(horizontal = ROW_PADDING, vertical = ROW_SECTION_PADDING),
    )
}

/**
 * The numbers that say how much of the list there is, and which page of it is on show.
 *
 * This row used to carry the case-insensitivity note too, and that was the wrong place for it. A rule
 * about every name on the page is not a fact about the page, so it read as furniture and got skimmed
 * — and the one user who needs it is the one about to type a name they are not sure of. It now sits
 * under the field in [TagNameDialog], where it can be read at the moment it applies. See
 * [tagsDialogRenameNote].
 *
 * The pagination says which page rather than a range, because a range would have to be translated
 * into prose ("tags 13–24 of 31") and every one of those words is another thing to get wrong. The
 * arrows dim at the ends rather than disappearing, so the row does not change width as the user pages
 * through it.
 */
@Composable
private fun TagsFooter(
    state: TagsState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = pluralStringResource(Res.plurals.tagsFooterShowing, state.rows.size, state.rows.size),
            color = AzkarTheme.colors.textSecondary,
            fontSize = FOOTER_SP,
        )
        Spacer(Modifier.width(SMALL_GAP))
        PageArrow(
            icon = AzkarIcons.ChevronStart,
            description = stringResource(Res.string.tagsPagePrevious),
            enabled = state.currentPage > 0,
            onClick = state::previousPage,
        )
        Text(
            text = stringResource(Res.string.tagsPageLabel, state.currentPage + 1, state.pageCount),
            color = AzkarTheme.colors.textSecondary,
            fontSize = FOOTER_SP,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.widthIn(min = PAGE_LABEL_WIDTH),
            textAlign = TextAlign.Center,
        )
        PageArrow(
            icon = AzkarIcons.ChevronRight,
            description = stringResource(Res.string.tagsPageNext),
            enabled = state.currentPage < state.pageCount - 1,
            onClick = state::nextPage,
        )
    }
}

/**
 * One end of the page range.
 *
 * Dimmed rather than removed at the ends, and [IconActionButton] has no `enabled` of its own, so the
 * state is carried by the tint. The press is left wired at the ends because
 * [TagsState.nextPage] and [TagsState.previousPage] already clamp to the list they have — a second
 * guard here would be a second answer to a question one place is responsible for.
 */
@Composable
private fun PageArrow(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconActionButton(
        icon = icon,
        contentDescription = description,
        onClick = onClick,
        iconSize = FOOTER_GLYPH,
        tint =
            if (enabled) {
                AzkarTheme.colors.textSecondary
            } else {
                AzkarTheme.colors.textSecondary.copy(alpha = FOOTER_DISABLED_ALPHA)
            },
        modifier = modifier,
    )
}

/** A title, a sentence, and no action — there is nothing here to act on. */
@Composable
private fun TagEmptyState(
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            color = colors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = hint,
            color = colors.textSecondary,
            fontSize = 12.sp,
        )
    }
}

// ---- previews ------------------------------------------------------------------------------------

@Preview
@Composable
private fun TagRowsPreview() {
    AzkarThemePreview(width = 1040.dp, height = 320.dp) {
        Column(
            modifier = Modifier.padding(AzkarSpacing.screenPaddingHorizontal),
            verticalArrangement = Arrangement.spacedBy(SMALL_GAP),
        ) {
            TagRow(
                tag = previewTagSummary("morning", collections = 1, remembrances = 52, added = true),
                onRename = {},
                onDelete = {},
                onUsage = {},
            )
            TagRow(
                tag = previewTagSummary("evening", collections = 3, remembrances = 9, added = true),
                onRename = {},
                onDelete = {},
                onUsage = {},
            )
            TagRow(
                tag =
                    previewTagSummary(
                        "a name long enough to have to be cut off rather than wrapped",
                        collections = 0,
                        remembrances = 0,
                    ),
                onRename = {},
                onDelete = {},
                onUsage = {},
            )
            DeletedTagRow(
                tag = previewTagSummary("travel", collections = 1, remembrances = 3, deleted = true),
                onRestore = {},
            )
        }
    }
}

@Preview
@Composable
private fun TagsFooterPreview() {
    AzkarThemePreview(width = 1040.dp) {
        Row(
            modifier = Modifier.padding(AzkarSpacing.screenPaddingHorizontal),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = pluralStringResource(Res.plurals.tagsFooterShowing, 8, 8),
                color = AzkarTheme.colors.textSecondary,
                fontSize = FOOTER_SP,
            )
            PageArrow(
                icon = AzkarIcons.ChevronStart,
                description = stringResource(Res.string.tagsPagePrevious),
                enabled = false,
                onClick = {},
            )
            Text(
                text = stringResource(Res.string.tagsPageLabel, 1, 3),
                color = AzkarTheme.colors.textSecondary,
                fontSize = FOOTER_SP,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.widthIn(min = PAGE_LABEL_WIDTH),
            )
            PageArrow(
                icon = AzkarIcons.ChevronRight,
                description = stringResource(Res.string.tagsPageNext),
                enabled = true,
                onClick = {},
            )
        }
    }
}

@Preview
@Composable
private fun TagEmptyStatePreview() {
    AzkarThemePreview(width = 620.dp) {
        TagEmptyState(
            title = stringResource(Res.string.tagsEmptyTitle),
            hint = stringResource(Res.string.tagsEmptyHint),
            modifier = Modifier.padding(AzkarSpacing.screenPaddingHorizontal),
        )
    }
}

/** One tag for the previews and nothing more — the counts and the date are the only fields that vary. */
internal fun previewTagSummary(
    name: String,
    collections: Int,
    remembrances: Int,
    added: Boolean = false,
    deleted: Boolean = false,
): TagSummary =
    TagSummary(
        id = name.hashCode().toLong(),
        name = name,
        createdAt = if (added) ADDED_AT else null,
        updatedAt = null,
        collectionCount = collections,
        remembranceCount = remembrances,
        deletedAt = if (deleted) DELETED_AT else null,
    )

private val DELETED_AT = Instant.parse("2024-01-01T00:00:00Z")
private val ADDED_AT = Instant.parse("2026-10-01T09:00:00Z")

/**
 * The date pattern, rebuilt only when the shell's language changes.
 *
 * A top-level `val` cannot hold a formatter built from `AzkarTheme.locale`, because that is a
 * composable read: the locale is not known until the theme is composed, so a formatter built at
 * class-load time would be bound to the machine's language and would keep it through a language
 * change in the app. Remembering it against the locale is what keeps the two agreeing.
 */
@Composable
private fun addedFormat(): DateTimeFormatter {
    val locale = AzkarTheme.locale
    return remember(locale) { DateTimeFormatter.ofPattern(ADDED_PATTERN, locale) }
}

private const val ADDED_PATTERN = "dd MMM yyyy"

/**
 * The page inset, and why it is not the theme's own [AzkarSpacing.screenPaddingHorizontal].
 *
 * The theme's inset is written for text set against the window edge. The hero band here is a filled
 * panel, and a panel that stops a gutter short of the card below it reads as two things rather than
 * one, so the band and the card take the shelf's own [LibraryHub]'s padding and the whole page
 * lines up with the screen it was opened from.
 */
private val PAGE_PADDING = 16.dp

private val SMALL_GAP = 8.dp

/** The same height the shelf's loading pane reserves, so the two do not jump on read. */
private val STATE_BOX_HEIGHT = 300.dp

// The toolbar. Fixed so a long translated sort name cannot eat the search field beside it.
// The rows.
private val ROW_PADDING = 16.dp
private val ROW_PADDING_VERTICAL = 12.dp
private val ROW_GAP = 12.dp
private val ROW_DETAIL_GAP = 3.dp
private val ROW_NAME_SP = 14.sp
private val ROW_DETAIL_SP = 11.sp
private val ROW_SECTION_SP = 11.sp
private val ROW_SECTION_PADDING = 10.dp
private val AVATAR_SIZE = 38.dp
private val AVATAR_SHAPE = 12.dp
private val AVATAR_GLYPH = 19.dp

/**
 * How opaque the avatar's glyph is drawn, matched to the wash behind it rather than to the page.
 *
 * The same value the collection mosaic gives its icon: the tile is already the loudest thing on the
 * row, and a glyph at full strength on top of it would read as a second label competing with the
 * tag's name. Faded to roughly a third, it stays recognisable as the category and leaves the name
 * the only text on the row worth reading.
 */
private const val AVATAR_GLYPH_ALPHA = 0.35f
private val ADDED_WIDTH = 150.dp
private val ADDED_GLYPH = 13.dp

// The footer.
private val FOOTER_GLYPH = 15.dp
private val FOOTER_SP = 11.sp
private const val FOOTER_DISABLED_ALPHA = 0.35f
private val PAGE_LABEL_WIDTH = 48.dp

/** The separator between the two halves of a usage line: a symbol, so nothing has to reorder it. */
private const val MIDDOT = " · "
