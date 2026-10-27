package com.azkar.ui.library

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.domain.model.RemembranceCollection
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.collectionDetailActionRemindMe
import com.azkar.generated.resources.collectionDetailBackLink
import com.azkar.generated.resources.collectionDetailDurationFormat
import com.azkar.generated.resources.collectionsActionAddMembers
import com.azkar.generated.resources.collectionsActionInReminders
import com.azkar.generated.resources.collectionsActionPin
import com.azkar.generated.resources.collectionsActionUnpin
import com.azkar.generated.resources.collectionsMemberCount
import com.azkar.generated.resources.hero_day
import com.azkar.generated.resources.hero_night
import com.azkar.generated.resources.libraryActionMore
import com.azkar.generated.resources.remembranceCreateActionOpen
import com.azkar.i18n.usesArabicTypeface
import com.azkar.ui.components.ActionIcons
import com.azkar.ui.components.AzkarIcons
import com.azkar.ui.components.AzkarMenuItem
import com.azkar.ui.components.AzkarOverflowMenu
import com.azkar.ui.components.AzkarTooltip
import com.azkar.ui.components.KhatimPattern
import com.azkar.ui.components.mirrorHorizontalGradient
import com.azkar.ui.components.pressableClick
import com.azkar.ui.library.data.CollectionStats
import com.azkar.ui.preview.previewCollection
import com.azkar.ui.preview.previewCollectionHeroActions
import com.azkar.ui.preview.previewCollectionStats
import com.azkar.ui.preview.previewCollectionStatsVariants
import com.azkar.ui.theme.AzkarPreview
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.AzkarWindowSize
import com.azkar.ui.theme.isRtlLayout
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The band at the top of a collection, which is the only place on the page that says what the
 * collection *is* rather than what is in it.
 *
 * Replaces the three things it used to be — a title bar, a cover banner, a progress card and a
 * reminder card stacked in a column — because four separately-bordered boxes above a grid of
 * remembrances read as four unrelated panels, and a user opening a collection is answering one
 * question: *where am I, and how far through am I?* A single painted band answers both in one glance
 * and hands the grid the whole page back.
 *
 * The artwork is the app's own hero illustration rather than a new asset per collection: a page of
 * twenty-nine cards cannot each carry a photograph, and one shared band with the artwork bleeding off
 * the trailing edge is what lets the copy at the leading edge be the only thing that moves.
 *
 * Everything printed here is a fact this page already had — the name, the description, the member
 * count, the estimate, the progress, when the next reminder fires — gathered into one band instead of
 * being spread across four boxes. Nothing is invented to fill the space; the band's job is to make
 * these legible at once, and a collection with no description simply has one fewer line in it.
 *
 * [inReminders] is the one thing here that is not about the collection but about whether the queue
 * draws from it. It is a plain yes or no, because the queue has exactly one question to answer about
 * a collection — "does it feed the reminders?" — and a schedule of its own no longer exists. It is
 * printed on the bell rather than in a row of its own for the same reason everything else moved up
 * here: a strip below the band that says one thing the bell already offers is a second place to look
 * for the answer to "is this in my reminders?".
 */
@Composable
internal fun CollectionDetailHero(
    collection: RemembranceCollection,
    memberCount: Int,
    stats: CollectionStats,
    inReminders: Boolean,
    actions: CollectionHeroActions,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(HERO_SHAPE)
    // Read once here rather than inside the layout, so that the height the band is given and the
    // arithmetic that justifies it are two names apart and can only disagree by being edited apart.
    val height = if (AzkarTheme.locale.usesArabicTypeface) HERO_HEIGHT_ARABIC else HERO_HEIGHT_LATIN

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(height)
                .clip(shape),
    ) {
        HeroBackdrop()

        HeroCopy(
            collection = collection,
            memberCount = memberCount,
            stats = stats,
            actions = actions,
            // No inset here: [HeroCopy] applies [HERO_INSET] itself. Applying it on both sides of the
            // call spent 40 of the band's height on air before the back link was drawn, and the copy
            // that ran off the end of that was not merely tight — a `Column` hands its last child
            // whatever height is left, so the facts row was measured at zero and the band clipped it
            // away entirely. See the arithmetic on [HERO_HEIGHT_LATIN].
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .widthIn(max = HERO_COPY_MAX_WIDTH)
                    .fillMaxSize(),
        )

        HeroActions(
            isPinned = collection.isPinned,
            inReminders = inReminders,
            actions = actions,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = HERO_INSET, end = HERO_INSET),
        )
    }
}

/**
 * The three layers the band is painted on: the artwork, the wash that makes the copy legible over it,
 * and the pattern.
 *
 * Its own composable so that the band above reads as what it decides — how big the window is and what
 * sits in it — rather than as three stacked rectangles whose layering order has to be read off the
 * source to be believed.
 */
@Composable
private fun HeroBackdrop() {
    val colors = AzkarTheme.colors
    // The illustration sits behind everything and is bled off the trailing edge so the band reads
    // as a window rather than as a picture in a frame. Cropped rather than fitted because the
    // artwork is much taller than this band and fitting it would letterbox it.
    Image(
        painter = painterResource(if (colors.isDark) Res.drawable.hero_night else Res.drawable.hero_day),
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
        alignment = Alignment.BottomEnd,
    )

    // The scrim. Mirrored rather than fixed because the copy is at the leading edge, and in
    // Arabic that is the right-hand edge: a fixed left-to-right wash would put the legible half
    // of the band on the side with nothing written on it.
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    mirrorHorizontalGradient(
                        listOf(colors.heroScrimStart, colors.heroScrimEnd),
                        isRtlLayout(),
                    ),
                ),
    )

    // The geometric texture, over the artwork rather than behind it, and clipped to the band's own
    // shape so nothing pokes out past the corners.
    KhatimPattern(
        tint = colors.artPatternTint,
        modifier = Modifier.fillMaxSize(),
    )
}

/**
 * Everything the band writes, in the order it writes it: the way back, the collection's name, whatever
 * description it has, then the facts and the progress track.
 *
 * Split out of [CollectionDetailHero] because this is the half of the band that changes when the copy
 * changes, and it is the only part that has to fit inside a height the artwork does not.
 */
@Composable
private fun HeroCopy(
    collection: RemembranceCollection,
    memberCount: Int,
    stats: CollectionStats,
    actions: CollectionHeroActions,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    // The icon key is resolved from the aggregate rather than handed in, so that the band, the shelf
    // tile and the mosaic all answer "which glyph for a collection with no key of its own" the same
    // way, and only in one place.
    val iconKey = collection.iconKey.orEmpty().ifBlank { defaultIconKeyFor(collection.kind) }
    Column(
        modifier =
            modifier
                // A plain inset, not a reservation for anything below: the band's controls all
                // live along its top edge now — the back link, the title, and the row of actions
                // at the trailing edge — so the copy is the only thing that has to fit in the
                // height, and it is the one thing that cannot be given a fixed budget. That is
                // what lets the band be as short as the tallest copy it has to hold.
                .padding(HERO_INSET),
        verticalArrangement = Arrangement.spacedBy(HERO_GAP),
    ) {
        // "All Collections" in words rather than a bare arrow, and in the band's own glass rather
        // than plain ink on the pattern. Both were once wrong in a way that only showed up against
        // the artwork: the link was the one control in the band drawn as bare `textSecondary`, so
        // the geometric pattern ran straight through its letters while the pills beside it all sat
        // on a surface — and a reader arriving from the library needs to be told where they came
        // from, because the header above still highlights *Library*. Naming the destination costs a
        // line of width and saves the decode.
        HeroPill(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            label = stringResource(Res.string.collectionDetailBackLink),
            onClick = actions.onBack,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            HeroBadge(icon = iconForCollection(iconKey))
            Spacer(Modifier.width(HERO_TITLE_GAP))
            Text(
                text = collection.name,
                color = colors.textPrimary,
                fontSize = HERO_TITLE_SP,
                lineHeight = HERO_TITLE_LINE_SP,
                fontWeight = FontWeight.Bold,
                // One line, where a two-line title would grow the copy past the band and push the
                // progress row into the call to action. A collection's name is the user's own
                // naming and can be arbitrarily long, and the grid below repeats it in full on
                // every card anyway — so the band truncates and the page as a whole does not.
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        val description = collection.description
        if (!description.isNullOrBlank()) {
            Text(
                text = description,
                color = colors.textSecondary,
                fontSize = HERO_BODY_SP,
                lineHeight = HERO_BODY_LINE_SP,
                maxLines = HERO_DESCRIPTION_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }

        HeroFacts(
            collection = collection,
            memberCount = memberCount,
            stats = stats,
        )
    }
}

@AzkarPreview
@Composable
internal fun CollectionDetailHeroPreview() {
    Column(
        modifier = Modifier.width(1200.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CollectionDetailHero(
            collection = previewCollection(),
            memberCount = 54,
            stats = previewCollectionStats(),
            inReminders = true,
            actions = previewCollectionHeroActions(),
        )
        CollectionDetailHero(
            collection = previewCollection(homePinnedAt = null, iconKey = "compass"),
            memberCount = 12,
            stats = previewCollectionStatsVariants().last(),
            inReminders = false,
            actions = previewCollectionHeroActions(),
        )
        CollectionDetailHero(
            collection =
                previewCollection(
                    name = "A deliberately long collection name for truncation review",
                    description = null,
                ),
            memberCount = 54,
            stats = previewCollectionStatsVariants()[1],
            inReminders = false,
            actions = previewCollectionHeroActions(),
        )
    }
}

/**
 * What the hero's own controls do.
 *
 * The trailing pills and the two links are a list of callbacks long enough that a signature nobody
 * reads correctly is the predictable result. Gathered, and handed to the band as one value, so that
 * adding a control to the band is a change to one type rather than to three call sites.
 *
 * The count is not written down here on purpose. A KDoc that says "five callbacks" is one that has
 * to be corrected every time a pill is added, and the correction is exactly what gets forgotten.
 */
internal data class CollectionHeroActions(
    val onBack: () -> Unit,
    val onAddMembers: (() -> Unit)?,
    val onCreateRemembrance: () -> Unit,
    val onTogglePin: () -> Unit,
    val onToggleReminder: () -> Unit,
    val menuItems: List<AzkarMenuItem>,
)

/**
 * The three figures under the description: how many, how long, and who put it here.
 *
 * Icon-and-text rather than the dotted separators the old banner used, because three facts separated
 * by dots are one sentence and this is not — each of these answers a different question, and the icon
 * is what lets the eye find the one it wanted without reading all three.
 *
 * Vertical rules between them rather than dots: a dot separator disappears the moment the third fact
 * wraps onto a second line, whereas a rule spans the gap and keeps saying "these are separate".
 *
 * Shared with the recitation session's band rather than written twice. Both pages open by answering
 * "what am I in, and how much of it is there?", and a session that answered that with a different set
 * of facts — or the same set in a different order, or at a different size — would be one band redrawn
 * badly rather than a second design.
 */
@Composable
internal fun HeroFacts(
    collection: RemembranceCollection,
    memberCount: Int,
    stats: CollectionStats,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HERO_FACT_GAP),
    ) {
        HeroFact(
            icon = Icons.AutoMirrored.Outlined.FormatListBulleted,
            text = pluralStringResource(Res.plurals.collectionsMemberCount, memberCount, memberCount),
        )
        HeroFactDivider()
        HeroFact(
            icon = AzkarIcons.Clock,
            text = stringResource(Res.string.collectionDetailDurationFormat, formatDuration(stats.estimatedRemaining)),
        )
        HeroFactDivider()
        HeroFact(icon = AzkarIcons.BookOpen, text = stringResource(collection.kind.labelResource))
    }
}

@AzkarPreview
@Composable
internal fun HeroFactsPreview() {
    HeroFacts(
        collection = previewCollection(),
        memberCount = 54,
        stats = previewCollectionStats(),
        modifier = Modifier.width(900.dp),
    )
}

/**
 * The square mark that sits beside the collection's name.
 *
 * The brand gradient and the white glyph, but built here rather than borrowed from the home screen's
 * brand tile: that one is 72dp and carries a fixed book glyph, and this page needs a different size
 * and a glyph that says which collection this is. The gradient and the corner radius are what make it
 * read as the app's mark, and both come from the theme, so the two tiles cannot drift apart.
 */
@Composable
private fun HeroBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    Box(
        modifier =
            modifier
                .size(HERO_BADGE)
                .clip(RoundedCornerShape(HERO_BADGE_SHAPE))
                .background(Brush.linearGradient(listOf(colors.brandTileStart, colors.brandTileEnd))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(HERO_BADGE_GLYPH),
        )
    }
}

/**
 * One icon-and-text fact. The icon is decorative — the text beside it is the fact — so it is
 * announced as nothing rather than read out a second time.
 */
@Composable
internal fun HeroFact(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AzkarTheme.colors.textSecondary,
            modifier = Modifier.size(HERO_FACT_GLYPH),
        )
        Spacer(Modifier.width(HERO_FACT_TEXT_GAP))
        Text(
            text = text,
            color = AzkarTheme.colors.textSecondary,
            fontSize = HERO_FACT_SP,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The hairline between two facts.
 *
 * A drawn box rather than a `VerticalDivider` because that component sizes itself from the ambient
 * typography's line height, which is the page's body size and not the facts' — which put a rule that
 * was twice as tall as the text it was separating.
 */
@Composable
internal fun HeroFactDivider(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .width(HERO_DIVIDER_WIDTH)
                .height(HERO_DIVIDER_HEIGHT)
                .background(AzkarTheme.colors.border),
    )
}

@AzkarPreview
@Composable
internal fun HeroFactDividerPreview() {
    HeroFactDivider(modifier = Modifier.width(400.dp))
}

/**
 * The hero's trailing controls: continue, add members, pin, remind, and everything else.
 *
 * These are the actions this page has always offered — the first three were lifted out of its menus
 * rather than invented — so the band is a shortcut to the header rather than a second, divergent set
 * of choices. The pin pill is the one that reads most like the mockup's star: this app has no
 * per-collection favourite, and pinning is the collection-level "keep this close to hand" action the
 * star stands in for, so labelling it "Pin" is what it says when you press it.
 *
 * Adding members is here for the reason its row below is not: the toolbar underneath is about the
 * *shape* of the grid — what to show, in what order — whereas this changes which members there are.
 * Keeping the edit up here is also what let the toolbar keep a single line, since the three-dot chip
 * that used to hold it had nowhere else to go when the category chips came out.
 *
 * [inReminders] says whether the queue draws from this collection. It goes on the bell's own label
 * rather than onto a row underneath the band, so the answer to "is this in my reminders?" is on the
 * control that changes it — and the bell switches to the "on" ink when it is answered, which is the
 * same treatment the pin pill gets.
 */
@Composable
private fun HeroActions(
    isPinned: Boolean,
    inReminders: Boolean,
    actions: CollectionHeroActions,
    modifier: Modifier = Modifier,
) {
    // The words are worked out once here and handed down, because both widths need the same five and
    // reading them twice was the reason this grew a parameter list longer than it could carry. A value
    // rather than six more arguments also means a label can never be spelled one way in the compact
    // band and another way in the wide one.
    val labels = heroActionLabels(isPinned = isPinned, inReminders = inReminders)
    if (AzkarWindowSize.isCompactWidth) {
        CompactHeroActions(
            actions = actions,
            isPinned = isPinned,
            labels = labels,
            background = AzkarTheme.colors.artPillSurface,
            modifier = modifier,
        )
    } else {
        WideHeroActions(
            actions = actions,
            isPinned = isPinned,
            labels = labels,
            modifier = modifier,
        )
    }
}

/**
 * The five things the band's controls have to say, decided once for both widths.
 *
 * [pin] switches on whether the collection is pinned and [reminderLive] on whether the collection
 * feeds the queue, because those are the two answers that change a pill's ink rather than its
 * label, and a caller that passed only strings would have to derive them again.
 */
@Immutable
private data class HeroActionLabels(
    val addMembers: String,
    val createRemembrance: String,
    val pin: String,
    val reminder: String,
    val reminderLive: Boolean,
)

@Composable
private fun heroActionLabels(
    isPinned: Boolean,
    inReminders: Boolean,
): HeroActionLabels {
    val pinLabel =
        stringResource(
            if (isPinned) Res.string.collectionsActionUnpin else Res.string.collectionsActionPin,
        )
    // One question in one pill: is this collection one of the sources the queue draws from. The
    // wording is chosen per case rather than derived, because the answer is a membership question
    // and not a time — the schedule that used to live here belongs to the whole queue now.
    val reminderLabel =
        stringResource(
            if (inReminders) Res.string.collectionsActionInReminders else Res.string.collectionDetailActionRemindMe,
        )
    return HeroActionLabels(
        addMembers = stringResource(Res.string.collectionsActionAddMembers),
        createRemembrance = stringResource(Res.string.remembranceCreateActionOpen),
        pin = pinLabel,
        reminder = reminderLabel,
        reminderLive = inReminders,
    )
}

@Composable
private fun WideHeroActions(
    actions: CollectionHeroActions,
    isPinned: Boolean,
    labels: HeroActionLabels,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HERO_PILL_GAP),
    ) {
        // The one edit that lives up here rather than in the row below. Everything else the band
        // carries — pin, remind, rename, share — is about this collection as a whole; adding members
        // changes what the page is *for*, and it is the change a reader most often arrives to make,
        // so it belongs beside the other things you can do to the collection rather than buried in a
        // toolbar that is otherwise about how the members are shown.
        actions.onAddMembers?.let { onAddMembers ->
            HeroPill(
                icon = Icons.Filled.Layers,
                label = labels.addMembers,
                onClick = onAddMembers,
            )
        }
        HeroPill(
            icon = Icons.Filled.Add,
            label = labels.createRemembrance,
            onClick = actions.onCreateRemembrance,
        )
        HeroPill(
            icon = ActionIcons.pin(isPinned),
            label = labels.pin,
            onClick = actions.onTogglePin,
            selected = isPinned,
        )
        HeroPill(
            icon = AzkarIcons.Bell,
            label = labels.reminder,
            onClick = actions.onToggleReminder,
            selected = labels.reminderLive,
        )
        AzkarOverflowMenu(
            icon = Icons.Filled.MoreVert,
            contentDescription = stringResource(Res.string.libraryActionMore),
            items = actions.menuItems,
            buttonSize = HERO_PILL_HEIGHT,
            iconSize = HERO_PILL_GLYPH,
            background = AzkarTheme.colors.artPillSurface,
        )
    }
}

@Composable
private fun CompactHeroActions(
    actions: CollectionHeroActions,
    isPinned: Boolean,
    labels: HeroActionLabels,
    background: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HERO_PILL_GAP),
    ) {
        actions.onAddMembers?.let { onAddMembers ->
            CompactHeroAction(
                icon = Icons.Filled.Layers,
                label = labels.addMembers,
                onClick = onAddMembers,
            )
        }
        CompactHeroAction(
            icon = Icons.Filled.Add,
            label = labels.createRemembrance,
            onClick = actions.onCreateRemembrance,
        )
        CompactHeroAction(
            icon = ActionIcons.pin(isPinned),
            label = labels.pin,
            onClick = actions.onTogglePin,
            selected = isPinned,
        )
        CompactHeroAction(
            icon = AzkarIcons.Bell,
            label = labels.reminder,
            onClick = actions.onToggleReminder,
            selected = labels.reminderLive,
        )
        AzkarTooltip(tooltip = stringResource(Res.string.libraryActionMore)) {
            AzkarOverflowMenu(
                icon = Icons.Filled.MoreVert,
                contentDescription = stringResource(Res.string.libraryActionMore),
                items = actions.menuItems,
                buttonSize = HERO_PILL_HEIGHT,
                iconSize = HERO_PILL_GLYPH,
                background = background,
            )
        }
    }
}

@Composable
private fun CompactHeroAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    selected: Boolean = false,
) {
    AzkarTooltip(tooltip = label) {
        HeroIconAction(
            icon = icon,
            contentDescription = label,
            onClick = onClick,
            selected = selected,
        )
    }
}

/**
 * One glyph-only control laid over the artwork, at the same height as the pills beside it.
 *
 * Glyph-only because that is what was asked for and it is defensible here: a play triangle on a
 * collection page has one meaning, and it is the meaning of the primary button it replaces. It keeps
 * its [contentDescription], so it is still announced with the words "Continue Reading" rather than as
 * an unlabelled button.
 *
 * [filled] draws it in the theme's primary with the primary's own ink — the band's single filled
 * control. The other three stay glass, which is what makes this one read as the action rather than as
 * a fourth option.
 *
 * Shared with the recitation session's band rather than kept to that page, because two bands of the
 * same artwork with the same controls at the same heights are one design drawn twice, and the second
 * drawing is the one that drifts.
 */
@Composable
internal fun HeroIconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    selected: Boolean = false,
) {
    val colors = AzkarTheme.colors
    val surface = if (filled) colors.primary else colors.artPillSurface
    val ink =
        when {
            filled -> colors.onPrimary
            selected -> colors.primary
            else -> colors.artPillInk
        }
    Box(
        modifier =
            modifier
                .size(HERO_PILL_HEIGHT)
                .clip(CircleShape)
                .background(surface)
                .pressableClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = ink,
            modifier = Modifier.size(HERO_ACTION_GLYPH),
        )
    }
}

@AzkarPreview
@Composable
internal fun HeroIconActionPreview() {
    Row(
        modifier = Modifier.width(340.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HeroIconAction(icon = ActionIcons.pin(on = false), contentDescription = "Pin", onClick = {})
        HeroIconAction(icon = ActionIcons.pin(on = true), contentDescription = "Unpin", onClick = {}, filled = true)
    }
}

/**
 * A light pill laid over the artwork.
 *
 * The surface is the theme's translucent glass rather than the card background, so the illustration
 * still shows through and the three pills read as one row of glass rather than three separate boxes.
 *
 * The ink is [com.azkar.ui.theme.AzkarColors.artPillInk] and nothing else, which is a correction
 * twice over. It was the artwork ink — light, for the dark badge above — so the pills printed their
 * own labels in white on white and read as three empty lozenges; swapping in the *text* colour fixed
 * the light theme and left the dark one broken, because the dark theme's text is light too. Ink has
 * to be chosen against the surface it is printed on, which is why the pair is a pair.
 *
 * The pressed state takes the primary colour, so that "Unpin" reads as having taken — and so does a
 * bell that has a schedule, which is the same claim: this one is already on. A bell whose schedule is
 * paused is deliberately left as glass, because it is set and will not come round, and a control that
 * claims to be on when nothing is going to happen is the sort of thing that gets trusted until it is
 * the one time it mattered.
 *
 * Shared with the recitation session's band for the reason [HeroIconAction] gives.
 */
@Composable
internal fun HeroPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val colors = AzkarTheme.colors
    val ink = if (selected) colors.primary else colors.artPillInk
    Row(
        modifier =
            modifier
                .height(HERO_PILL_HEIGHT)
                .clip(RoundedCornerShape(HERO_PILL_SHAPE))
                .background(colors.artPillSurface)
                .pressableClick(onClick = onClick)
                .padding(horizontal = HERO_PILL_PAD_X),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(HERO_PILL_GLYPH),
        )
        Spacer(Modifier.width(HERO_PILL_TEXT_GAP))
        Text(
            text = label,
            color = ink,
            fontSize = HERO_PILL_SP,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@AzkarPreview
@Composable
internal fun HeroPillPreview() {
    Row(
        modifier = Modifier.width(420.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HeroPill(icon = AzkarIcons.Bell, label = "Daily, 06:30", onClick = {}, selected = true)
        HeroPill(icon = AzkarIcons.BellOff, label = "Paused", onClick = {})
        HeroPill(icon = ActionIcons.read, label = "Start session", onClick = {})
    }
}

/**
 * The band's height, and the arithmetic behind it.
 *
 * Five stacked regions and nothing pinned to the bottom, so the height is a sum of the copy alone. The
 * number that matters is that it is a sum of *line boxes* and not of font sizes, because those are
 * not the same thing here. The title and the description name a `lineHeight` ([HERO_TITLE_LINE_SP],
 * [HERO_BODY_LINE_SP]) and the back link is a [HeroPill], which is a fixed height. The facts row asks
 * for a `fontSize` and inherits the line box of whatever `LocalTextStyle` is ambient, which for this
 * app is `bodyLarge` — 24sp of leading in Inter and 32sp in Amiri. An 11sp fact therefore occupies a
 * 24sp box, and the band it is drawn in is taller in Arabic than the same collection is in English,
 * whether or not anybody chose that.
 *
 * So the sum is two sums. Latin: 20 of top inset, a 36 back link, 9 of gap, a 42 badge row, 9, a
 * two-line description at the 17dp box it actually occupies (34), 9 and a 26 fact row — 185 of copy,
 * and 20 more below it for the band's own margin, which comes to 205. Arabic is the same arrangement
 * with 32 for the fact row: 191 of copy, and 211.
 *
 * Two lines of description are reserved whether or not there is one, which is deliberate — the
 * collection's own description is the one part of this copy the app cannot size in advance, and a band
 * that grew when it read a longer blurb would move everything below it every time the collection
 * changed. A collection with no description simply has one fewer line in it, and the slack falls at
 * the bottom, which is the only place it can: the copy is anchored to the top inset because that is
 * what keeps it level with the row of action pills beside it.
 *
 * Every figure above was measured rather than counted, by laying the band out at 1584x900 at density
 * one and reading the bounds of the last line in each script. Sizing this from nominal font sizes is
 * what the first version of this constant did, and the band clipped the line below the facts row clean
 * off without a word about it. The band used to carry a progress caption and a track here as well;
 * that moved down to its own strip above the members, where it is readable rather than buried, which
 * is why the sum is shorter than the one it replaced.
 */
private val HERO_HEIGHT_LATIN = 205.dp

/**
 * The same sum in Arabic, which is taller because Amiri's leadings are.
 *
 * A second constant rather than a fudge factor on the first: the two figures differ by exactly the
 * one line whose line box the script decides — the fact row's 6 — and nothing else does. The back
 * link used to be the second such line, which is why it is now a [HeroPill]: at a fixed height it
 * cannot vary, and a control whose box the reading direction changed underneath it was never a
 * number the band could be honest about.
 */
private val HERO_HEIGHT_ARABIC = 211.dp

/** Matches the corner radius of the cards below, so the band and the grid read as one surface. */
private val HERO_SHAPE = 20.dp

/** The padding between the band and everything printed on it. */
private val HERO_INSET = 20.dp

/**
 * The widest the copy may get before it starts stretching.
 *
 * A cap rather than a fraction of the width, because this page is opened on windows from a phone's
 * width to a desktop monitor's. A fraction would leave the description one enormous line on a wide
 * window, and a band of empty artwork on a narrow one; a cap gives the same line length on both.
 */
private val HERO_COPY_MAX_WIDTH = 660.dp

/** The gap between the band's stacked regions. */
private val HERO_GAP = 9.dp

private val HERO_BADGE = 42.dp
private val HERO_BADGE_GLYPH = 23.dp
private val HERO_BADGE_SHAPE = 14.dp
private val HERO_TITLE_GAP = 12.dp
private val HERO_TITLE_SP = 24.sp
private val HERO_TITLE_LINE_SP = 29.sp
private val HERO_BODY_SP = 12.sp
private val HERO_BODY_LINE_SP = 17.sp
private const val HERO_DESCRIPTION_LINES = 2

private val HERO_FACT_GAP = 12.dp
private val HERO_FACT_TEXT_GAP = 6.dp
private val HERO_FACT_GLYPH = 14.dp
private val HERO_FACT_SP = 11.sp
private val HERO_DIVIDER_WIDTH = 1.dp
private val HERO_DIVIDER_HEIGHT = 12.dp

internal val HERO_PILL_GAP = 8.dp
internal val HERO_PILL_SHAPE = 18.dp
internal val HERO_PILL_HEIGHT = 36.dp
internal val HERO_PILL_GLYPH = 14.dp
internal val HERO_PILL_SP = 12.sp
internal val HERO_PILL_PAD_X = 14.dp
internal val HERO_PILL_TEXT_GAP = 6.dp

/**
 * The glyph inside the round primary control.
 *
 * Larger than [HERO_PILL_GLYPH] because it is the only thing inside that control: the pills carry a
 * glyph and a word, so each can be small, while this one has to read from across a 212dp band on its
 * own.
 */
private val HERO_ACTION_GLYPH = 18.dp
