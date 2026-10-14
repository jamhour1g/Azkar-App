package com.azkar.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.collectionsActionAddTag
import com.azkar.generated.resources.collectionsDialogTagsClearAll
import com.azkar.generated.resources.collectionsDialogTagsCollapse
import com.azkar.generated.resources.collectionsDialogTagsCreate
import com.azkar.generated.resources.collectionsDialogTagsExpand
import com.azkar.generated.resources.collectionsDialogTagsHint
import com.azkar.generated.resources.collectionsDialogTagsLabel
import com.azkar.generated.resources.collectionsDialogTagsNewLabel
import com.azkar.generated.resources.collectionsDialogTagsNewPlaceholder
import com.azkar.generated.resources.collectionsDialogTagsNone
import com.azkar.generated.resources.collectionsDialogTagsSearchPlaceholder
import com.azkar.generated.resources.collectionsSelectionCount
import com.azkar.i18n.textDirectionOf
import com.azkar.ui.components.AzkarFilterChip
import com.azkar.ui.components.AzkarOutlinedField
import com.azkar.ui.components.BorderedStateBox
import com.azkar.ui.components.IconActionButton
import com.azkar.ui.components.PrimaryButton
import com.azkar.ui.components.pressableClick
import com.azkar.ui.library.model.TagOption
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.AzkarThemePreview
import com.azkar.ui.theme.isRtlLayout
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The tag picker: every tag in the library as a chip, and a field for the ones that are not.
 *
 * This replaces a single comma-separated text field, and the reason it is a picker rather than a
 * parser bolted onto that field is that both of the things a tag list does are *choices*, and a text
 * field can only ask for text. "Sleep" has to be the same tag as "sleep" everywhere in the library,
 * and only a list can say so by showing the chip already lit; and someone with forty remembrances under
 * "after prayer" should not have to remember the spelling to reach them again. The field it replaced
 * made both a matter of memory, and its own placeholder said as much: "morning, sleep".
 *
 * The two states are separate on purpose. [expanded] folds the whole chip list away so the dialog is
 * short again once the tags are chosen, and the dashed chip is what says whether the new-tag row is
 * out — folded away by itself, it would hide the only control that can add a tag the library has never
 * heard of, which is the entire purpose of this panel on an empty library.
 *
 * @param availableTags every tag the library uses. Sorted and de-duplicated by the catalogue, so
 * this panel never sorts and never reorders a chip under the pointer while someone is reaching for it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagPickerPanel(
    draft: TagDraftView,
    availableTags: List<TagOption>,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    var creating by rememberSaveable { mutableStateOf(true) }
    FieldLabel(text = stringResource(Res.string.collectionsDialogTagsLabel))
    Text(
        text = stringResource(Res.string.collectionsDialogTagsHint),
        color = AzkarTheme.colors.textSecondary,
        fontSize = TAG_HINT_SP,
        modifier = Modifier.padding(top = TAG_HINT_GAP, bottom = TAG_PANEL_GAP),
    )
    BorderedStateBox(modifier = modifier.fillMaxWidth()) {
        Column {
            TagSearchRow(
                draft = draft,
                expanded = expanded,
                onToggleExpanded = { expanded = !expanded },
            )
            if (expanded) {
                Spacer(Modifier.height(TAG_GROUP_GAP))
                TagChips(
                    draft = draft,
                    options = draft.tagOptions(availableTags),
                    onToggleCreating = { creating = !creating },
                )
                if (creating) {
                    Spacer(Modifier.height(TAG_GROUP_GAP))
                    NewTagRow(draft = draft)
                }
            }
        }
    }
}

/**
 * The search box, how many are chosen, and the fold.
 *
 * The count rides in the field's trailing slot rather than beside it, because it answers the question
 * the panel was opened for and the field is where the question gets asked; it is also the only place
 * left that does not push the fold off the edge of the panel.
 */
@Composable
private fun TagSearchRow(
    draft: TagDraftView,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
    val colors = AzkarTheme.colors
    val ambient = if (isRtlLayout()) LayoutDirection.Rtl else LayoutDirection.Ltr
    Row(verticalAlignment = Alignment.CenterVertically) {
        AzkarOutlinedField(
            value = draft.tagQuery,
            onValueChange = draft::updateTagQuery,
            placeholder = stringResource(Res.string.collectionsDialogTagsSearchPlaceholder),
            leadingIcon = Icons.Outlined.Search,
            modifier = Modifier.weight(1f),
            textDirection = textDirectionOf(draft.tagQuery, ambient),
            trailingContent = {
                Text(
                    text =
                        pluralStringResource(
                            Res.plurals.collectionsSelectionCount,
                            draft.tags.size,
                            draft.tags.size,
                        ),
                    color = colors.textSecondary,
                    fontSize = TAG_COUNT_SP,
                    maxLines = 1,
                )
            },
        )
        Spacer(Modifier.width(TAG_ROW_GAP))
        IconActionButton(
            icon = if (expanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
            contentDescription =
                stringResource(
                    if (expanded) {
                        Res.string.collectionsDialogTagsCollapse
                    } else {
                        Res.string.collectionsDialogTagsExpand
                    },
                ),
            onClick = onToggleExpanded,
            iconSize = TAG_FOLD_GLYPH,
            tint = colors.textSecondary,
        )
    }
}

/**
 * The chips, and the dashed one that opens the new-tag row.
 *
 * Wrapped rather than scrolled, because a horizontal strip of tags hides the fact that the tag someone
 * wanted is two rows down, and a picker that cannot show all of its answers is not a picker. The dashed
 * chip is last so that the row it opens sits directly under the row it controls.
 *
 * A chip says the tag's name in the reader's language and means the tag by its stored one — see
 * [TagOption]. That split is why this takes options rather than strings: a list of names could only
 * ever have meant one of the two, and the wrong one of those silently creates a duplicate tag.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagChips(
    draft: TagDraftView,
    options: List<TagOption>,
    onToggleCreating: () -> Unit,
) {
    val colors = AzkarTheme.colors
    if (options.isEmpty()) {
        Text(
            text = stringResource(Res.string.collectionsDialogTagsNone),
            color = colors.textSecondary,
            fontSize = TAG_HINT_SP,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TAG_CHIP_GAP),
        verticalArrangement = Arrangement.spacedBy(TAG_CHIP_GAP),
    ) {
        options.forEach { option ->
            // Selection and the saved value are both the stored name. The chip's text is what the
            // reader can read instead, and the two are only the same string when the tag has no
            // translation of its own.
            val selected = draft.isTagSelected(option.value)
            AzkarFilterChip(
                text = option.label,
                selected = selected,
                onClick = { draft.toggleTag(option.value) },
                leadingIcon = Icons.Filled.Check.takeIf { selected },
            )
        }
        if (draft.tags.isNotEmpty()) {
            AzkarFilterChip(
                text = stringResource(Res.string.collectionsDialogTagsClearAll),
                selected = false,
                onClick = draft::clearTags,
                leadingIcon = Icons.Filled.Close,
            )
        }
        CreateTagChip(onClick = onToggleCreating)
    }
}

/**
 * The dashed "Create tag" chip, which opens and closes the row beneath it.
 *
 * Dashed rather than solid because it is not a tag and must never be mistaken for one: a filled chip in
 * that row would be a chip that selected something, and a solid outline would be a chip that selected
 * nothing. Nothing in the platform draws a dashed border, so it is drawn here — see
 * [Modifier.dashedOutline] — at the same one-pixel weight as every other chip outline, so that a row of
 * chips with this one at the end does not change weight.
 */
@Composable
private fun CreateTagChip(onClick: () -> Unit) {
    val colors = AzkarTheme.colors
    Row(
        modifier =
            Modifier
                .dashedOutline(color = colors.primary, radius = CHIP_CORNER_RADIUS)
                .pressableClick(onClick = onClick)
                .padding(horizontal = TAG_CHIP_PADDING_X, vertical = TAG_CHIP_PADDING_Y),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(TAG_CREATE_GLYPH),
        )
        Spacer(Modifier.width(TAG_CREATE_GAP))
        Text(
            text = stringResource(Res.string.collectionsDialogTagsCreate),
            color = colors.primary,
            fontSize = TAG_LABEL_SP,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/**
 * Name a tag that does not exist yet, and add it.
 *
 * The caption sits beside the field rather than above it because this row is one action — type a name,
 * add it — and stacking the three parts would make the dialog taller for no gain. It is capped and
 * allowed to wrap rather than pushing the field off the panel, because the field is the part that
 * matters.
 *
 * Deliberately one field with no translations attached, and the reason is worth stating because it is
 * the only authoring surface in the app that has none: this row does not create a tag. It buffers a
 * name onto [TagDraftView.tags], and the row itself appears later as a side effect of saving
 * the collection, through `ExposedTagRepository.findOrCreateId`. Carrying translations would mean
 * turning that list into something with a translation per entry and threading a second concept through
 * `CollectionService.setTags` and `ExposedCollectionRepository.replaceTags` for one shortcut field.
 * A tag's translations are edited in one place — the tag name dialog on the tags screen — and a tag
 * made here can be translated there.
 */
@Composable
private fun NewTagRow(draft: TagDraftView) {
    val colors = AzkarTheme.colors
    val ambient = if (isRtlLayout()) LayoutDirection.Rtl else LayoutDirection.Ltr
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier.widthIn(max = NEW_TAG_LABEL_MAX_WIDTH),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.AddCircle,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(TAG_CREATE_GLYPH),
            )
            Spacer(Modifier.width(TAG_CREATE_GAP))
            Text(
                text = stringResource(Res.string.collectionsDialogTagsNewLabel),
                color = colors.textSecondary,
                fontSize = TAG_HINT_SP,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(TAG_ROW_GAP))
        AzkarOutlinedField(
            value = draft.newTagName,
            onValueChange = draft::updateNewTagName,
            placeholder = stringResource(Res.string.collectionsDialogTagsNewPlaceholder),
            modifier = Modifier.weight(1f),
            textDirection = textDirectionOf(draft.newTagName, ambient),
        )
        Spacer(Modifier.width(TAG_ROW_GAP))
        PrimaryButton(
            text = stringResource(Res.string.collectionsActionAddTag),
            onClick = { draft.addTag(draft.newTagName) },
            enabled = draft.newTagAcceptable,
        )
    }
}

/**
 * A dashed outline with [radius] corners, drawn rather than composed.
 *
 * Compose's `border` has no dash pattern to hand it, and the alternative — a solid border — is exactly
 * the ambiguity this chip exists to avoid. The path is built from the size the layout has already
 * resolved rather than from a second guess at the chip's measurements, so the dashes land on whole
 * units and the row of chips does not shimmer when one of them changes width.
 *
 * A radius is passed instead of a `Shape` because a dash is a straight run of ink: rounding it to the
 * chip's own corner radius would need a per-corner dash phase, and a chip this small has no corner that
 * is more than a dash or two long.
 */
private fun Modifier.dashedOutline(
    color: Color,
    radius: Dp,
): Modifier =
    drawBehind {
        val outline =
            Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(Offset.Zero, size),
                        cornerRadius = CornerRadius(radius.toPx()),
                    ),
                )
            }
        drawPath(
            path = outline,
            color = color,
            style =
                Stroke(
                    width = DASH_WIDTH.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH_LENGTH.toPx(), DASH_GAP.toPx())),
                ),
        )
    }

@Preview
@Composable
private fun TagPickerPanelPreview() {
    AzkarThemePreview(width = 560.dp, height = 520.dp) {
        val draft = CollectionEditorDraft.create()
        draft.toggleTag("morning")
        draft.toggleTag("sleep")
        TagPickerPanel(
            draft = draft,
            availableTags = listOf("morning", "sleep", "travel", "after prayer", "commute").map(TagOption::of),
            modifier = Modifier.width(520.dp),
        )
    }
}

// ---- plumbing -------------------------------------------------------------------------------------

/**
 * Dashes are as long as the gaps they leave behind.
 *
 * The equal phase is what makes a dashed line read as a border rather than as a row of dots: a dash
 * longer than its gap stops looking interrupted and starts looking like a solid line with gaps in it,
 * which is not the same signal.
 */
private val DASH_LENGTH = 3.dp

private val DASH_GAP = 3.dp

/** One pixel, the weight of every other chip outline in the app. */
private val DASH_WIDTH = 1.dp

/**
 * Half a Material filter chip's corner, so the one chip in the row that is not a chip still looks like it
 * was laid out by the same theme as the rest of them.
 */
private val CHIP_CORNER_RADIUS = 8.dp

/** The dashed chip is the only rectangular chip here, which is most of what makes it read as "not a tag". */
private val TAG_LABEL_SP = 12.sp

private val TAG_HINT_SP = 11.sp

private val TAG_COUNT_SP = 10.sp

private val TAG_HINT_GAP = 3.dp

private val TAG_PANEL_GAP = 8.dp

/** Between the search row and the chips, and between the chips and the new-tag row. */
private val TAG_GROUP_GAP = 10.dp

private val TAG_CHIP_GAP = 6.dp

private val TAG_ROW_GAP = 8.dp

private val TAG_CHIP_PADDING_X = 10.dp

/**
 * Half a Material filter chip's 32dp, so the dashed chip lands on the same height as the chips it
 * stands among rather than sitting two pixels proud of a row it is pretending to belong to.
 */
private val TAG_CHIP_PADDING_Y = 8.dp

private val TAG_CREATE_GLYPH = 12.dp

private val TAG_CREATE_GAP = 4.dp

private val TAG_FOLD_GLYPH = 18.dp

/**
 * How much room the "Create new tag" caption may take before it wraps and the field wins.
 *
 * The caption is the one thing in this row that is long in some languages and the one thing in the row
 * that is not an input, so it is the one that should be squeezed first.
 */
private val NEW_TAG_LABEL_MAX_WIDTH = 130.dp
