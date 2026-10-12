package com.azkar.ui.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.azkar.domain.model.Remembrance
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.collectionDetailEmptyHint
import com.azkar.generated.resources.collectionDetailEmptyTitle
import com.azkar.generated.resources.collectionSessionActionNext
import com.azkar.generated.resources.collectionSessionActionPrevious
import com.azkar.generated.resources.collectionSessionActionRestart
import com.azkar.generated.resources.collectionSessionActionResume
import com.azkar.generated.resources.collectionSessionFinishedBody
import com.azkar.generated.resources.collectionSessionFinishedTitle
import com.azkar.generated.resources.collectionSessionMissingHint
import com.azkar.generated.resources.collectionSessionMissingTitle
import com.azkar.generated.resources.collectionsActionBack
import com.azkar.generated.resources.collectionsToastTitle
import com.azkar.generated.resources.libraryActionCopyCopied
import com.azkar.generated.resources.libraryActionRead
import com.azkar.ui.components.ActionIcons
import com.azkar.ui.components.AzkarMenuItem
import com.azkar.ui.components.PrimaryButton
import com.azkar.ui.components.SecondaryButton
import com.azkar.ui.library.CollectionSessionState
import com.azkar.ui.library.SessionDialogState
import com.azkar.ui.preview.PREVIEW_COLLECTION_ID
import com.azkar.ui.preview.previewReminders
import com.azkar.ui.preview.rememberPreviewCatalogue
import com.azkar.ui.shell.NotificationPriority
import com.azkar.ui.shell.ShowToast
import com.azkar.ui.shell.stepTransition
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.AzkarThemePreview
import com.azkar.ui.theme.isRtlLayout
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * One passage at a time, with the rest of the collection behind it.
 *
 * The detail page answers "what is in here"; this answers "where am I". It holds nothing but the
 * session's own state — the collection, its progress and the shelf's catalogue all come from the
 * shared [CollectionsCatalogue], exactly as they do on the page behind it, so the two can never
 * disagree about what has been recited.
 *
 * The page is drawn as one band, one strip, one passage and one row of controls, and nothing else.
 * That is a deliberate narrowing: the older version spent its top third on a title bar that named the
 * collection and a progress line that repeated what the collection page already said, so the passage —
 * the only reason to be here — started a third of the way down the window. The band above now carries
 * the same facts the detail page's band does, because it is the same band with the same controls on
 * it, and the user who opened the session from there should not have to go back to remember where they
 * are.
 *
 * There is no separate finish action. The session is finished when every member has been counted, and
 * the screen says so rather than asking the user to confirm a state the data already knows.
 */
@Composable
fun CollectionSessionScreen(
    session: CollectionSessionState,
    onBack: () -> Unit,
    onOpenRemembrance: (Long) -> Unit,
    showToast: ShowToast,
    modifier: Modifier = Modifier,
) {
    val catalogue = session.catalogue
    val dialogs = remember { SessionDialogState() }

    LaunchedEffect(Unit) { catalogue.loadLabels() }
    LaunchedEffect(catalogue) { catalogue.loadData() }
    LaunchedEffect(session.collectionId) { session.loadProgress() }
    // Asked for here for the same reason the detail page asks: the band's bell reads the schedule, and
    // a schedule nobody has loaded reads as "you have not set one".
    LaunchedEffect(Unit) { session.reminders.load() }

    // Resolved here rather than inside the effect: a `LaunchedEffect` body is not a composable
    // context, and the message itself is only known once the notice has fired.
    val toastTitle = stringResource(Res.string.collectionsToastTitle)
    LaunchedEffect(catalogue.onNotice) {
        val key = (catalogue.onNotice as? CollectionNotice.Message)?.key ?: return@LaunchedEffect
        catalogue.onNotice = null
        showToast(toastTitle, getString(key.messageResource), key.priority)
    }

    SessionDialogs(session = session, dialogs = dialogs)

    CollectionSessionContent(
        session = session,
        dialogs = dialogs,
        onBack = onBack,
        onOpenRemembrance = onOpenRemembrance,
        showToast = showToast,
        modifier = modifier,
    )
}

/**
 * The session's holder, alive for exactly as long as the composition that asked for it.
 *
 * Handed to [CollectionSessionScreen] rather than built inside it for the same reason the detail
 * page's is: the entry that pushed the session owns the holder's lifetime, and that entry is what
 * knows which collection is being read.
 */
@Composable
fun rememberCollectionSessionState(
    catalogue: CollectionsCatalogue,
    collectionId: Long,
    reminders: CollectionReminderState,
): CollectionSessionState =
    remember(catalogue, collectionId, reminders) {
        CollectionSessionState(catalogue, collectionId, reminders)
    }

/**
 * Which of the session's two dialogs is open.
 *
 * A holder rather than two loose locals for the same reason the detail page keeps its dialog state in
 * one place: both are raised from the band's controls — which sit at the far end of the screen from the
 * read dialog that opens from the passage — and answered in one block, and a flag left behind in the
 * wrong place is a dialog that reappears on the next passage.
 *
 * `internal` rather than `private` because [CollectionSessionContent] takes one, and the preview
 * that renders that pane has to be able to hand it over.
 */
@Stable
internal class SessionDialogState {
    /** The schedule being written, or `null` when the reminder editor is closed. */
    var reminderDraft by mutableStateOf<ReminderDraft?>(null)

    /** Whether dropping the schedule is waiting to be confirmed. */
    var pendingReminderRemoval by mutableStateOf(false)
}

/**
 * The reminder editor and its removal confirmation, both raised from the band's bell.
 *
 * The bell is the only mention of reminders on this page, so the editor it opens is the only place a
 * schedule can be written, paused or dropped from here — which is exactly why the pause switch moved
 * into it rather than onto the card the band replaced.
 */
@Composable
private fun SessionDialogs(
    session: CollectionSessionState,
    dialogs: SessionDialogState,
) {
    val collection = session.collection ?: return
    val reminders = session.reminders
    val stored = session.reminder?.reminder

    dialogs.reminderDraft?.let { draft ->
        CollectionReminderDialog(
            draft = draft,
            collectionName = collection.name,
            isExisting = stored != null,
            isEnabled = stored?.enabled != false,
            callbacks = reminderEditorCallbacks(session, dialogs, reminders, collection.name, collection.members.size),
        )
    }

    if (dialogs.pendingReminderRemoval && stored != null) {
        ConfirmReminderRemovalDialog(
            onDismiss = { dialogs.pendingReminderRemoval = false },
            onConfirm = {
                dialogs.pendingReminderRemoval = false
                reminders.remove(
                    collectionId = session.collectionId,
                    onSuccess = {
                        session.catalogue.onNotice = CollectionNotice.Message(CollectionNoticeKey.REMINDER_REMOVED)
                    },
                    onFailure = { session.catalogue.onNotice = CollectionNotice.Message(CollectionNoticeKey.FAILED) },
                )
            },
        )
    }
}

/**
 * What the editor's four controls do on this session.
 *
 * Its own function because it is four callbacks written out in full, each of them the same shape:
 * change the draft, do the write, report what happened. Read side by side in the dialog's own
 * signature they pushed [SessionDialogs] past the point where the *second* dialog could be found in
 * it at all.
 */
private fun reminderEditorCallbacks(
    session: CollectionSessionState,
    dialogs: SessionDialogState,
    reminders: CollectionReminderState,
    collectionName: String,
    memberCount: Int,
): ReminderEditorCallbacks =
    ReminderEditorCallbacks(
        onSave = { cadence, minuteOfDay, dayOfWeek ->
            dialogs.reminderDraft = null
            reminders.save(
                collectionId = session.collectionId,
                cadence = cadence,
                minuteOfDay = minuteOfDay,
                dayOfWeek = dayOfWeek,
                collectionName = collectionName,
                memberCount = memberCount,
                onSuccess = {
                    session.catalogue.onNotice = CollectionNotice.Message(CollectionNoticeKey.REMINDER_SAVED)
                },
                onFailure = {
                    session.catalogue.onNotice = CollectionNotice.Message(CollectionNoticeKey.FAILED)
                },
            )
        },
        onRemove = {
            dialogs.reminderDraft = null
            dialogs.pendingReminderRemoval = true
        },
        onDismiss = { dialogs.reminderDraft = null },
        // The dialog stays open: the switch has already moved and the state has already
        // said what it thinks happened, and a dialog that shut itself the moment a toggle
        // was touched would hide the one thing worth checking, which is whether it stuck.
        onToggleEnabled = { enabled ->
            reminders.setEnabled(
                collectionId = session.collectionId,
                enabled = enabled,
                onFailure = {
                    session.catalogue.onNotice = CollectionNotice.Message(CollectionNoticeKey.FAILED)
                },
            )
        },
    )

/**
 * `internal` rather than `private` so a `@Preview` can render the session pane directly, the way
 * the screenshot harness does, without the shell's navigation around it.
 */
@Composable
internal fun CollectionSessionContent(
    session: CollectionSessionState,
    dialogs: SessionDialogState,
    onBack: () -> Unit,
    onOpenRemembrance: (Long) -> Unit,
    showToast: ShowToast,
    modifier: Modifier = Modifier,
) {
    // Painted here rather than left to the shell, for the same reason the shelf hub paints its own:
    // this pane has to look right on its own, and a screenshot harness renders it standalone.
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(AzkarTheme.colors.background)
                .padding(SESSION_PAGE_PADDING),
    ) {
        when {
            session.collection == null ->
                CollectionsEmptyState(
                    title = stringResource(Res.string.collectionSessionMissingTitle),
                    hint = stringResource(Res.string.collectionSessionMissingHint),
                    action = {
                        EmptyStateAction(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            description = stringResource(Res.string.collectionsActionBack),
                            onClick = onBack,
                        )
                    },
                )

            !session.hasMembers ->
                CollectionsEmptyState(
                    title = stringResource(Res.string.collectionDetailEmptyTitle),
                    hint = stringResource(Res.string.collectionDetailEmptyHint),
                )

            session.isFinished ->
                CollectionsEmptyState(
                    title = stringResource(Res.string.collectionSessionFinishedTitle),
                    hint = stringResource(Res.string.collectionSessionFinishedBody),
                    action = {
                        EmptyStateAction(
                            icon = Icons.Filled.RestartAlt,
                            description = stringResource(Res.string.collectionSessionActionRestart),
                            onClick = session::restart,
                        )
                    },
                )

            else ->
                SessionReading(
                    session = session,
                    dialogs = dialogs,
                    onBack = onBack,
                    onOpenRemembrance = onOpenRemembrance,
                    showToast = showToast,
                )
        }
    }
}

@Preview
@Composable
private fun CollectionSessionContentPreview() {
    AzkarThemePreview {
        val catalogue = rememberPreviewCatalogue()
        CollectionSessionContent(
            session = CollectionSessionState(catalogue, PREVIEW_COLLECTION_ID, previewReminders()),
            dialogs = remember { SessionDialogState() },
            onBack = {},
            onOpenRemembrance = {},
            showToast = { _, _, _ -> },
        )
    }
}

/**
 * The whole reading experience: what collection this is, how far through it the user is, the passage,
 * and the two ways to move.
 *
 * The passage scrolls and the rest does not, rather than the page scrolling as one. That is the one
 * place this screen differs from the detail page, and for a reason that only applies here: the band
 * and the strip are the same on every passage, so letting them scroll away would throw away the only
 * context that stays true while the passage underneath changes three times a minute. The controls are
 * pinned for the same reason the passage is not centred — the same button has to be in the same place
 * for the whole session.
 */
@Composable
private fun SessionReading(
    session: CollectionSessionState,
    dialogs: SessionDialogState,
    onBack: () -> Unit,
    onOpenRemembrance: (Long) -> Unit,
    showToast: ShowToast,
) {
    val collection = session.collection ?: return
    val current = session.current ?: return
    val forwards = session.advancesForwards()
    val isRtl = isRtlLayout()
    val clipboard = LocalClipboardManager.current
    val toastTitle = stringResource(Res.string.collectionsToastTitle)
    val copiedLabel = stringResource(Res.string.libraryActionCopyCopied)

    Column(Modifier.fillMaxSize()) {
        CollectionSessionHero(
            collection = collection,
            publisher = session.publisherLabel(),
            stats = session.stats,
            reminder = sessionHeroReminder(session),
            actions =
                SessionHeroActions(
                    onBack = onBack,
                    onNext = session::complete,
                    onToggleFavorite = { session.setFavorite(!collection.isFavorite) },
                    onEditReminder = { dialogs.reminderDraft = sessionReminderDraft(session) },
                    menuItems = sessionOverflowItems(session, onOpenRemembrance),
                ),
        )
        Spacer(Modifier.height(SESSION_REGION_GAP))
        CollectionSessionProgressStrip(
            stats = session.stats,
            memberCount = session.totalMembers,
        )
        Spacer(Modifier.height(SESSION_REGION_GAP))
        SessionPassagePane(
            session = session,
            passage = current,
            forwards = forwards,
            isRtl = isRtl,
            onCopy = { text ->
                clipboard.setText(AnnotatedString(text))
                showToast(toastTitle, copiedLabel, NotificationPriority.LOW)
            },
        )
        Spacer(Modifier.height(SESSION_REGION_GAP))
        SessionFooter(session = session)
    }
}

/**
 * The scrolling middle of the session: the one passage, and the card it is drawn on.
 *
 * Only the pane scrolls. The band and the strip above it do not, because they are the same on every
 * passage, and letting them scroll away would throw away the only context that stays true while the
 * passage underneath changes three times a minute.
 *
 * The swap is an animation over the passage rather than a `when`, so the direction of the movement is
 * something the transition can be told about: which way it went is read off the position, not off
 * which control was pressed, because the controls do not agree with the movement — "Again" counts the
 * passage and stays on it, and "Skip" can land on the same passage again at the end of a collection.
 */
@Composable
private fun ColumnScope.SessionPassagePane(
    session: CollectionSessionState,
    passage: Remembrance,
    forwards: Boolean,
    isRtl: Boolean,
    onCopy: (String) -> Unit,
) {
    Column(
        Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState()),
    ) {
        AnimatedContent(
            targetState = passage,
            contentKey = { it.id ?: UNPINNED_ID },
            transitionSpec = { stepTransition(isRtl, forwards) },
            modifier = Modifier.fillMaxWidth(),
            label = "sessionPassage",
        ) { shown ->
            SessionPassageCard(
                remembrance = shown,
                presenter = session.presenter,
                position = session.position,
                memberCount = session.totalMembers,
                actions =
                    PassageActions(
                        onCopy = onCopy,
                        onReciteAgain = session::reciteAgain,
                        onSkip = session::skip,
                    ),
            )
        }
    }
}

/**
 * The two ways to move, at the bottom, in the order they are used.
 *
 * "Previous" leads and is drawn as a secondary button because stepping back is a correction — it is
 * pressed when the user has just realised they went too far, which is a rare and slightly embarrassed
 * thing to do. "Next" trails as the primary because it is what a session is: one press per passage,
 * thirty times, and the whole reason the screen exists.
 *
 * The old row said "Done" for the counting action, which was accurate and read as an exit. It is not
 * an exit — the session ends on its own when the last passage is counted — so it now says "Next",
 * which is what it does.
 *
 * There are no page dots in the middle. They were in the design this was built from, and they were
 * dropped because they cannot be honest here: a dot strip claims to show where you are in the
 * collection, but this screen walks *progress*, not the collection's order, so a user who recited three
 * passages out of order and skipped two would be looking at dots that describe neither thing. The
 * strip above already carries the one number that cannot mislead — how many are counted.
 */
@Composable
private fun SessionFooter(session: CollectionSessionState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SecondaryButton(
            text = stringResource(Res.string.collectionSessionActionPrevious),
            onClick = session::previous,
            // Disabled rather than hidden: the control staying put and going grey teaches that there is
            // a way back, and a button that appears and disappears with the position makes the row
            // jump every time the first passage is reached.
            enabled = session.canStepBack,
        )
        Spacer(Modifier.weight(1f))
        PrimaryButton(
            text = stringResource(Res.string.collectionSessionActionNext),
            onClick = session::complete,
        )
    }
}

/**
 * Whether the passage on screen is further along the shelf than the one before it.
 *
 * Read off [CollectionSessionState.position] rather than off which control was pressed, because the
 * controls do not agree with the movement: "Again" counts the passage and stays on it, and "Skip" can
 * land on the same passage again at the end of a collection. Watching the position instead means the
 * animation only plays when the passage genuinely changed, and always plays the way it moved.
 *
 * The previous position is remembered rather than recomputed, because "did it change" is a question
 * about the transition and the state only ever knows where it is now.
 */
@Composable
private fun CollectionSessionState.advancesForwards(): Boolean {
    var previous by remember { mutableIntStateOf(position) }
    val forwards = position >= previous
    SideEffect { previous = position }
    return forwards
}

/**
 * The key a passage with no id is animated under. A member without an id cannot be counted, so the
 * session will not offer one; the fallback exists only so [AnimatedContent] always has something to
 * compare, and never matches a real id.
 */
private const val UNPINNED_ID = Long.MIN_VALUE

/**
 * The publisher badge on the band: who put this collection together.
 *
 * Taken from the first member rather than from the collection, because the seed data records an author
 * per remembrance and the collection itself has no such field — and the first member's is as good a
 * answer as any, since a collection is its members. Clamped through the same [remembranceSourceLabel]
 * the member cards use, because the raw source carries a number and a title that would make the badge
 * two facts instead of one.
 */
private fun CollectionSessionState.publisherLabel(): String =
    collection
        ?.members
        ?.firstOrNull()
        ?.source
        ?.let(::remembranceSourceLabel)
        .orEmpty()

/**
 * What the band's bell says about this collection's schedule, in the same three words the detail
 * page's bell uses.
 *
 * Asked for rather than passed in because the answer needs three separate reads, and the paused case
 * has to be settled before the next firing is asked for at all: a schedule that is switched off has no
 * next firing, so the same `null` would otherwise report a reminder the user just paused as one that
 * was never set.
 */
@Composable
private fun sessionHeroReminder(session: CollectionSessionState): CollectionHeroReminder {
    val reminder = session.reminder?.reminder
    val zone = ZoneId.systemDefault()
    val now = remember(zone) { LocalDateTime.now(zone) }
    return when {
        reminder == null -> CollectionHeroReminder.None
        !reminder.enabled -> CollectionHeroReminder.Paused
        else -> sessionHeroReminderAt(session, now)
    }
}

/**
 * The one case left after [sessionHeroReminder] has ruled out "never set" and "switched off": a
 * schedule that is set, is on, and has a moment coming round.
 *
 * Split out so the next firing is asked for only once the two states that cannot have one are already
 * behind us, which is the whole reason the branch order matters.
 */
@Composable
private fun sessionHeroReminderAt(
    session: CollectionSessionState,
    now: LocalDateTime,
): CollectionHeroReminder {
    val next = session.nextReminderAt(now)
    return next
        ?.let { CollectionHeroReminder.At(formatReminderTime(it.hour * 60 + it.minute, AzkarTheme.locale)) }
        ?: CollectionHeroReminder.None
}

/**
 * The schedule the editor opens on: the stored one, or a fresh daily slot when there is none.
 *
 * A function rather than a call at each of the two places the bell can be pressed from, so "edit" and
 * "set" cannot disagree about what an absent schedule should start as.
 */
private fun sessionReminderDraft(session: CollectionSessionState): ReminderDraft =
    session.reminder?.reminder?.let(ReminderDraft::of) ?: ReminderDraft.create()

/**
 * The band's overflow: the things about this session that are not about the collection.
 *
 * Only here, not on the passage. Every item is a whole-session action — read the passage in full,
 * go back to where the progress says the user should be, throw the progress away — and none of them
 * has anything to do with the passage on screen beyond being reachable from it. "Read the passage in
 * full" is the one that looks like it belongs on the card, and it is here because it is the answer to
 * "how long is this one?": the card shows what it can fit, and this is the rest.
 */
@Composable
private fun sessionOverflowItems(
    session: CollectionSessionState,
    onOpenRemembrance: (Long) -> Unit,
): List<AzkarMenuItem> =
    buildList {
        // Asked for once here rather than read inside the item's `onClick`, so the item is not offered
        // at all on a finished session — an entry that leads to a page which will say "this
        // remembrance is gone" is worse than a missing entry.
        session.readId()?.let { id ->
            add(
                AzkarMenuItem(
                    label = stringResource(Res.string.libraryActionRead),
                    icon = ActionIcons.read,
                    onClick = { onOpenRemembrance(id) },
                ),
            )
        }
        // Only offered once the user has actually left the path progress would have chosen. On a
        // session that is walking straight through the collection there is nowhere to go back to, and
        // an item that offers to return you to where you already are is noise.
        if (session.isPinned) {
            add(
                AzkarMenuItem(
                    label = stringResource(Res.string.collectionSessionActionResume),
                    icon = ActionIcons.resume,
                    onClick = session::resume,
                ),
            )
        }
        add(
            AzkarMenuItem(
                label = stringResource(Res.string.collectionSessionActionRestart),
                icon = ActionIcons.restart,
                onClick = session::restart,
            ),
        )
    }

/** The page's own padding, the same inset the detail page and the shelf use. */
private val SESSION_PAGE_PADDING = 16.dp

/**
 * The air between the band's regions.
 *
 * One value for every gap because the band, the strip, the passage and the controls are four answers to
 * one question and should read as one block; a wider gap under the band than under the passage would
 * make the passage look like the passage's neighbour rather than like the middle of the page.
 */
private val SESSION_REGION_GAP = 14.dp
