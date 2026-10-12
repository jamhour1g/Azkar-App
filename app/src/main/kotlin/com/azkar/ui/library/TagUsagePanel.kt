package com.azkar.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.azkar.domain.model.CollectionSummary
import com.azkar.domain.model.TagSummary
import com.azkar.domain.model.TagUsage
import com.azkar.domain.model.TagUsageRemembrance
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.collectionsMemberCount
import com.azkar.generated.resources.tagsUsageCollectionsTitle
import com.azkar.generated.resources.tagsUsageEmpty
import com.azkar.generated.resources.tagsUsageLoading
import com.azkar.generated.resources.tagsUsageNoText
import com.azkar.generated.resources.tagsUsageOpenHint
import com.azkar.generated.resources.tagsUsageRemembrancesTitle
import com.azkar.generated.resources.tagsUsageTitle
import com.azkar.ui.components.AzkarDialogSurface
import com.azkar.ui.components.AzkarIcons
import com.azkar.ui.components.pressableClick
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.AzkarThemePreview
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import java.time.Instant
import java.util.Locale

/**
 * `ui/library/TagUsagePanel.kt` — what a tag is actually attached to.
 *
 * The row already carries the counts, so what this panel adds is not a number but a set of names: a
 * tag on fifty passages is a real fact and a list of one line is not, and the question a delete
 * raises is never "how many" but "which". Reading fifty names before agreeing to a delete is also
 * what stops the delete being a reflex — someone who sees the collection they had forgotten is using
 * a label has a chance to notice before it comes off.
 *
 * It is a dialog rather than a destination because it is a question about one row, answered and
 * dismissed; pushing a page for it would put a screen on the stack whose back button means "put the
 * list back the way it was", and there is nothing on it worth remembering. Every row is *live*
 * though — opening a collection pushes the collection, and [TagsState.closeUsage] is called on the
 * way so the panel is not still waiting behind it when the user comes back.
 *
 * @param onOpenCollection pushes a collection by id.
 * @param onOpenRemembrance pushes a passage by id.
 */
@Composable
internal fun TagUsagePanel(
    state: TagsState,
    onOpenCollection: (Long) -> Unit,
    onOpenRemembrance: (Long) -> Unit,
) {
    // The panel appears while the read is in flight and again if it answered; it disappears on its own
    // when a read for a tag that is gone came back empty, which is the only answer that means there is
    // nothing here to show.
    if (state.usage == null && !state.usageLoading) {
        return
    }
    TagUsageDialog(
        usage = state.usage,
        onDismiss = state::closeUsage,
        onOpenCollection = onOpenCollection,
        onOpenRemembrance = onOpenRemembrance,
    )
}

/**
 * The panel itself, over whatever the read found.
 *
 * Split from [TagUsagePanel] so a preview can draw it with a fixed [TagUsage] and no holder behind
 * it: every figure on this page is read from the usage rather than from the vocabulary, and a
 * preview that had to build a [TagsState] could not fill the one thing it is here to show. This is
 * the same split `DeletedTagRows` makes for the same reason.
 *
 * There is no separate "is it still loading" parameter, because there is no separate state to ask
 * about: a null [usage] *is* the loading answer. The read is only ever left waiting when it has not
 * come back, and a read that came back empty closed the panel rather than opening it empty.
 */
@Composable
private fun TagUsageDialog(
    usage: TagUsage?,
    onDismiss: () -> Unit,
    onOpenCollection: (Long) -> Unit,
    onOpenRemembrance: (Long) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        AzkarDialogSurface(modifier = Modifier.widthIn(min = PANEL_MIN_WIDTH, max = PANEL_MAX_WIDTH)) {
            Column {
                Text(
                    text =
                        if (usage != null) {
                            stringResource(Res.string.tagsUsageTitle, usage.tag.name)
                        } else {
                            stringResource(Res.string.tagsUsageLoading)
                        },
                    color = AzkarTheme.colors.textPrimary,
                    fontSize = PANEL_TITLE_SP,
                    fontWeight = FontWeight.Bold,
                )
                if (usage != null) {
                    UsageBody(
                        usage = usage,
                        onOpenCollection = onOpenCollection,
                        onOpenRemembrance = onOpenRemembrance,
                    )
                }
            }
        }
    }
}

/**
 * The two lists, or the one sentence saying there is nothing.
 *
 * Scrolling inside the dialog rather than growing it: a tag on two hundred passages would otherwise
 * be a panel taller than the window, and the collections at the top — usually the few rows a user
 * actually recognises — would scroll out of reach before the passages did. The height is capped
 * instead of unbounded so a short usage still sits in a compact dialog.
 */
@Composable
private fun UsageBody(
    usage: TagUsage,
    onOpenCollection: (Long) -> Unit,
    onOpenRemembrance: (Long) -> Unit,
) {
    if (usage.collections.isEmpty() && usage.remembrances.isEmpty()) {
        Text(
            text = stringResource(Res.string.tagsUsageEmpty),
            color = AzkarTheme.colors.textSecondary,
            fontSize = PANEL_BODY_SP,
            modifier = Modifier.padding(top = ROW_GAP),
        )
        return
    }
    Column(
        modifier =
            Modifier
                .padding(top = ROW_GAP)
                .heightIn(max = PANEL_LIST_HEIGHT)
                .verticalScroll(rememberScrollState()),
    ) {
        UsageSection(title = stringResource(Res.string.tagsUsageCollectionsTitle)) {
            usage.collections.forEach { summary ->
                CollectionUsageRow(summary = summary, onOpen = { onOpenCollection(summary.id) })
            }
        }
        UsageSection(title = stringResource(Res.string.tagsUsageRemembrancesTitle)) {
            usage.remembrances.forEach { row ->
                RemembranceUsageRow(row = row, onOpen = { onOpenRemembrance(row.id) })
            }
        }
        Text(
            text = stringResource(Res.string.tagsUsageOpenHint),
            color = AzkarTheme.colors.textSecondary,
            fontSize = PANEL_HINT_SP,
            modifier = Modifier.padding(top = HINT_PADDING),
        )
    }
}

/**
 * One titled group of rows.
 *
 * The rule above the second group rather than between every row: the group titles are what make the
 * two kinds separable at a glance, and a hairline per row inside a list of fifty passages would be
 * fifty more lines to read past.
 */
@Composable
private fun UsageSection(
    title: String,
    content: @Composable () -> Unit,
) {
    val colors = AzkarTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = colors.border)
        Text(
            text = title,
            color = colors.textSecondary,
            fontSize = PANEL_HINT_SP,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = SECTION_PADDING, bottom = SECTION_PADDING / 2),
        )
        content()
    }
}

/**
 * One collection, with the number of passages it holds.
 *
 * The count beside the name because it is what tells the two apart at a glance — two collections
 * named the same way are rare, and where they happen the number is the difference.
 */
@Composable
private fun CollectionUsageRow(
    summary: CollectionSummary,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .pressableClick(onClick = onOpen)
                .padding(vertical = ROW_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = iconForCollection(summary.iconKey),
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(ROW_GLYPH),
        )
        Spacer(Modifier.width(ROW_GAP))
        Text(
            text = summary.name,
            color = colors.textPrimary,
            fontSize = PANEL_BODY_SP,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(ROW_GAP))
        Text(
            text = pluralStringResource(Res.plurals.collectionsMemberCount, summary.memberCount, summary.memberCount),
            color = colors.textSecondary,
            fontSize = PANEL_HINT_SP,
            maxLines = 1,
        )
        Spacer(Modifier.width(SMALL_GAP))
        Icon(
            imageVector = AzkarIcons.ChevronRight,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(ROW_GLYPH),
        )
    }
}

/**
 * One passage, by whichever of its texts the reader's language can use.
 *
 * [TagUsageRemembrance.displayText] does the choosing rather than this row, so the same preference
 * order applies here as everywhere else the app shows a passage; the only thing decided here is what
 * to print when it found nothing at all, which is a sentence about the row rather than about the
 * text and so belongs with the rest of the panel's words.
 */
@Composable
private fun RemembranceUsageRow(
    row: TagUsageRemembrance,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    val text = row.displayText(AzkarTheme.locale) ?: stringResource(Res.string.tagsUsageNoText)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .pressableClick(onClick = onOpen)
                .padding(vertical = ROW_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = colors.textPrimary,
            fontSize = PANEL_BODY_SP,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(SMALL_GAP))
        Icon(
            imageVector = AzkarIcons.ChevronRight,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(ROW_GLYPH),
        )
    }
}

// ---- previews ------------------------------------------------------------------------------------

/**
 * The panel with real content in it, rather than the empty sentence.
 *
 * The empty state is one `Text` and cannot be laid out wrong; this one is a two-column arrangement
 * with a cap on its height, which is exactly the kind of thing a preview earns its place on.
 */
@Preview
@Composable
private fun TagUsagePanelPreview() {
    AzkarThemePreview(width = 620.dp, height = 640.dp) {
        TagUsageDialog(
            usage = previewUsage(),
            onDismiss = {},
            onOpenCollection = {},
            onOpenRemembrance = {},
        )
    }
}

/**
 * One tag, two collections and three passages carrying it, with no holder at all.
 *
 * Every word the panel draws comes from the [TagUsage] rather than from the vocabulary, so a fixed
 * one describes this page completely.
 */
private fun previewUsage(): TagUsage =
    TagUsage(
        tag =
            TagSummary(
                id = 1L,
                name = "morning",
                createdAt = Instant.parse("2026-10-01T09:00:00Z"),
                updatedAt = null,
                collectionCount = 2,
                remembranceCount = 3,
                deletedAt = null,
            ),
        collections =
            listOf(
                CollectionSummary(
                    id = 11L,
                    name = "Morning azkar",
                    description = "",
                    iconKey = "sunrise",
                    coverImagePath = null,
                    memberCount = 12,
                    homePinnedAt = null,
                ),
                CollectionSummary(
                    id = 12L,
                    name = "Quiet hours",
                    description = "",
                    iconKey = "moon",
                    coverImagePath = null,
                    memberCount = 4,
                    homePinnedAt = null,
                ),
            ),
        remembrances =
            listOf(
                TagUsageRemembrance(
                    id = 21L,
                    texts = mapOf(Locale.ENGLISH to "Remember, the reward of the morning deeds is great."),
                    source = "أذكار الصباح",
                ),
                TagUsageRemembrance(
                    id = 22L,
                    texts = mapOf(Locale.forLanguageTag("ar") to "سبحان الله وبحمده"),
                    source = null,
                ),
                TagUsageRemembrance(id = 23L, texts = emptyMap(), source = null),
            ),
    )

private val ROW_GAP = 12.dp
private val ROW_GLYPH = 16.dp
private val ROW_PADDING = 8.dp
private val SMALL_GAP = 8.dp
private val SECTION_PADDING = 12.dp
private val HINT_PADDING = 10.dp

private val PANEL_MIN_WIDTH = 420.dp
private val PANEL_MAX_WIDTH = 560.dp
private val PANEL_LIST_HEIGHT = 380.dp
private val PANEL_TITLE_SP = 16.sp
private val PANEL_BODY_SP = 13.sp
private val PANEL_HINT_SP = 11.sp
