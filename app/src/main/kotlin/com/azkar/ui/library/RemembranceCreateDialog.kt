package com.azkar.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.azkar.domain.model.HadithGrade
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.collectionsActionCancel
import com.azkar.generated.resources.collectionsActionNext
import com.azkar.generated.resources.collectionsActionStepBack
import com.azkar.generated.resources.collectionsDialogStepProgress
import com.azkar.generated.resources.remembranceCreateActionConfirm
import com.azkar.generated.resources.remembranceCreateAdditionalInfo
import com.azkar.generated.resources.remembranceCreateExplanationLabel
import com.azkar.generated.resources.remembranceCreateExplanationPlaceholder
import com.azkar.generated.resources.remembranceCreateGradeLabel
import com.azkar.generated.resources.remembranceCreateLanguagePlaceholder
import com.azkar.generated.resources.remembranceCreateLanguageUnavailable
import com.azkar.generated.resources.remembranceCreateSourceLabel
import com.azkar.generated.resources.remembranceCreateSourcePlaceholder
import com.azkar.generated.resources.remembranceCreateTextLabel
import com.azkar.generated.resources.remembranceCreateTextPlaceholder
import com.azkar.generated.resources.remembranceCreateTextRequired
import com.azkar.generated.resources.remembranceCreateTitle
import com.azkar.generated.resources.remembranceCreateTranslationAdd
import com.azkar.generated.resources.remembranceCreateTranslationHint
import com.azkar.generated.resources.remembranceCreateTranslationLabel
import com.azkar.generated.resources.remembranceCreateTranslationPlaceholder
import com.azkar.generated.resources.remembranceCreateTranslationRemove
import com.azkar.i18n.displayNameIn
import com.azkar.i18n.textDirectionOf
import com.azkar.ui.components.AzkarDialogSurface
import com.azkar.ui.components.AzkarMenuItem
import com.azkar.ui.components.AzkarOutlinedField
import com.azkar.ui.components.AzkarSelect
import com.azkar.ui.components.IconActionButton
import com.azkar.ui.components.PrimaryButton
import com.azkar.ui.components.SecondaryButton
import com.azkar.ui.i18n.hadithGradeLabel
import com.azkar.ui.library.model.RemembranceTranslationEntry
import com.azkar.ui.library.model.TagOption
import com.azkar.ui.library.model.missingRemembranceLanguages
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.AzkarThemePreview
import com.azkar.ui.theme.AzkarVariantsPreview
import com.azkar.ui.theme.isRtlLayout
import org.jetbrains.compose.resources.stringResource
import java.util.Locale

/**
 * The three-page form for a remembrance made from inside a collection.
 *
 * The pages are text, translations and tags, in that order. The first is the one required thing, so
 * it is the only page that can reject Next; the other two are optional adornments, not hurdles. Grade
 * and source sit under a collapsed "Additional information" row on the first page, which keeps the
 * passage itself the first thing the form asks for without losing the fields in the supplied mockup.
 *
 * The page index lives here rather than in [RemembranceCreateDraft], because it is one dialog's
 * navigation state, not part of the passage. A newly opened form always starts on its first page.
 */
@Composable
internal fun RemembranceCreateDialog(
    draft: RemembranceCreateDraft,
    availableTags: List<TagOption>,
    onDismiss: () -> Unit,
    onConfirm: (RemembranceCreateDraft) -> Unit,
) {
    val steps = RemembranceCreateStep.entries
    var page by rememberSaveable { mutableStateOf(0) }
    val step = steps[page.coerceIn(steps.indices)]
    val colors = AzkarTheme.colors

    Dialog(onDismissRequest = onDismiss) {
        AzkarDialogSurface(
            modifier = Modifier.widthIn(min = CREATE_MIN_WIDTH, max = CREATE_MAX_WIDTH),
        ) {
            Column {
                RemembranceCreateHeader(step = step, onDismiss = onDismiss)
                Spacer(Modifier.height(CREATE_HEADER_STEP_GAP))
                RemembranceCreateStepBar(current = page, stepCount = steps.size)
                Spacer(Modifier.height(CREATE_BLOCK_GAP))
                HorizontalDivider(color = colors.border)
                Spacer(Modifier.height(CREATE_BLOCK_GAP))
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = CREATE_BODY_MAX_HEIGHT)
                            .verticalScroll(rememberScrollState()),
                ) {
                    when (step) {
                        RemembranceCreateStep.TEXT -> RemembranceTextStep(draft)
                        RemembranceCreateStep.TRANSLATIONS -> RemembranceTranslationsStep(draft)
                        RemembranceCreateStep.TAGS -> TagPickerPanel(draft, availableTags)
                    }
                }
                Spacer(Modifier.height(CREATE_BLOCK_GAP))
                HorizontalDivider(color = colors.border)
                Spacer(Modifier.height(CREATE_BLOCK_GAP))
                RemembranceCreateActions(
                    step = step,
                    lastStep = step == steps.last(),
                    navigation =
                        RemembranceCreateNavigation(
                            onDismiss = onDismiss,
                            onBack = { page = (page - 1).coerceAtLeast(0) },
                            onNext = { if (draft.submit()) page = (page + 1).coerceAtMost(steps.lastIndex) },
                            onConfirm = { if (draft.submit()) onConfirm(draft) },
                        ),
                )
            }
        }
    }
}

@Composable
private fun RemembranceTextStep(draft: RemembranceCreateDraft) {
    val colors = AzkarTheme.colors
    Column {
        FieldLabel(text = stringResource(Res.string.remembranceCreateTextLabel), required = true)
        AzkarOutlinedField(
            value = draft.text,
            onValueChange = draft::updateText,
            placeholder = stringResource(Res.string.remembranceCreateTextPlaceholder),
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            minHeight = CREATE_TEXT_FIELD_HEIGHT,
            textDirection = LayoutDirection.Rtl,
        )
        if (draft.textRejected) {
            Text(
                text = stringResource(Res.string.remembranceCreateTextRequired),
                color = colors.dialogValidation,
                fontSize = CREATE_HINT_SP,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = CREATE_VALIDATION_GAP),
            )
        }
        Spacer(Modifier.height(CREATE_FIELD_GAP))
        RemembranceAdditionalInfo(draft)
    }
}

@Composable
private fun RemembranceAdditionalInfo(draft: RemembranceCreateDraft) {
    val colors = AzkarTheme.colors
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { expanded = !expanded }
                    .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.remembranceCreateAdditionalInfo),
                color = colors.textPrimary,
                fontSize = CREATE_LABEL_SP,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
        if (expanded) {
            Column {
                Spacer(Modifier.height(CREATE_LABEL_GAP))
                FieldLabel(text = stringResource(Res.string.remembranceCreateGradeLabel))
                AzkarSelect(
                    label = gradeLabel(draft.grade),
                    items = gradeOptions(draft),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(CREATE_FIELD_GAP))
                FieldLabel(text = stringResource(Res.string.remembranceCreateSourceLabel))
                AzkarOutlinedField(
                    value = draft.source,
                    onValueChange = draft::updateSource,
                    placeholder = stringResource(Res.string.remembranceCreateSourcePlaceholder),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        }
    }
}

@Composable
private fun gradeLabel(grade: HadithGrade): String = hadithGradeLabel(grade)

@Composable
private fun gradeOptions(draft: RemembranceCreateDraft): List<AzkarMenuItem> =
    HadithGrade.entries.map { grade ->
        AzkarMenuItem(
            label = gradeLabel(grade),
            onClick = { draft.updateGrade(grade) },
            icon = if (grade == draft.grade) Icons.Filled.Check else Icons.AutoMirrored.Outlined.Label,
        )
    }

@Composable
private fun RemembranceTranslationsStep(draft: RemembranceCreateDraft) {
    val uiLocale = AzkarTheme.locale
    val offered = missingRemembranceLanguages(draft.translations, draft.textLocale, uiLocale)
    var selectedLanguageTag by rememberSaveable { mutableStateOf("") }
    val selectedLanguage =
        offered.firstOrNull { it.toLanguageTag() == selectedLanguageTag }
            ?: offered.firstOrNull()

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.remembranceCreateTranslationHint),
            color = AzkarTheme.colors.textSecondary,
            fontSize = CREATE_HINT_SP,
        )
        Spacer(Modifier.height(CREATE_FIELD_GAP))
        if (offered.isEmpty()) {
            Text(
                text = stringResource(Res.string.remembranceCreateLanguageUnavailable),
                color = AzkarTheme.colors.textSecondary,
                fontSize = CREATE_HINT_SP,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AzkarSelect(
                    label =
                        selectedLanguage?.displayNameIn(uiLocale)
                            ?: stringResource(Res.string.remembranceCreateLanguagePlaceholder),
                    items =
                        offered.map { candidate ->
                            AzkarMenuItem(
                                label = candidate.displayNameIn(uiLocale),
                                onClick = { selectedLanguageTag = candidate.toLanguageTag() },
                                icon = Icons.Filled.Add,
                            )
                        },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(CREATE_FIELD_GAP))
                PrimaryButton(
                    text = stringResource(Res.string.remembranceCreateTranslationAdd),
                    onClick = {
                        selectedLanguage?.let(draft::addTranslation)
                        selectedLanguageTag = ""
                    },
                    enabled = selectedLanguage != null,
                    leadingIcon = Icons.Filled.Add,
                )
            }
        }
        draft.translations.forEach { entry ->
            Spacer(Modifier.height(CREATE_TRANSLATION_BLOCK_GAP))
            RemembranceTranslationBlock(draft = draft, entry = entry, uiLocale = uiLocale)
        }
    }
}

@Composable
private fun RemembranceTranslationBlock(
    draft: RemembranceCreateDraft,
    entry: RemembranceTranslationEntry,
    uiLocale: Locale,
) {
    val ambient = if (isRtlLayout()) LayoutDirection.Rtl else LayoutDirection.Ltr
    val language = entry.locale.displayNameIn(uiLocale)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = language,
                color = AzkarTheme.colors.textPrimary,
                fontSize = CREATE_LABEL_SP,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            IconActionButton(
                icon = Icons.Filled.Close,
                contentDescription = stringResource(Res.string.remembranceCreateTranslationRemove, language),
                onClick = { draft.removeTranslation(entry.locale) },
                iconSize = 14.dp,
                tint = AzkarTheme.colors.textSecondary,
            )
        }
        Spacer(Modifier.height(CREATE_LABEL_GAP))
        FieldLabel(text = stringResource(Res.string.remembranceCreateTranslationLabel))
        AzkarOutlinedField(
            value = entry.text,
            onValueChange = { draft.updateTranslationText(entry.locale, it) },
            placeholder = stringResource(Res.string.remembranceCreateTranslationPlaceholder),
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            minHeight = CREATE_TRANSLATION_FIELD_HEIGHT,
            textDirection = textDirectionOf(entry.text, ambient),
        )
        Spacer(Modifier.height(CREATE_FIELD_GAP))
        FieldLabel(text = stringResource(Res.string.remembranceCreateExplanationLabel))
        AzkarOutlinedField(
            value = entry.explanation.orEmpty(),
            onValueChange = { draft.updateExplanation(entry.locale, it) },
            placeholder = stringResource(Res.string.remembranceCreateExplanationPlaceholder),
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            minHeight = CREATE_EXPLANATION_FIELD_HEIGHT,
            textDirection = textDirectionOf(entry.explanation, ambient),
        )
    }
}

@Composable
private fun RemembranceCreateHeader(
    step: RemembranceCreateStep,
    onDismiss: () -> Unit,
) {
    val colors = AzkarTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier
                    .size(CREATE_HEADER_BADGE)
                    .clip(RoundedCornerShape(CREATE_HEADER_BADGE_RADIUS))
                    .background(Brush.linearGradient(listOf(colors.brandTileStart, colors.brandTileEnd))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(CREATE_HEADER_BADGE_GLYPH),
            )
        }
        Spacer(Modifier.width(CREATE_HEADER_GAP))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.remembranceCreateTitle),
                color = colors.textPrimary,
                fontSize = CREATE_HEADER_TITLE_SP,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(step.subtitleResource),
                color = colors.textSecondary,
                fontSize = CREATE_HEADER_SUBTITLE_SP,
                modifier = Modifier.padding(top = 2.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconActionButton(
            icon = Icons.Filled.Close,
            contentDescription = stringResource(Res.string.collectionsActionCancel),
            onClick = onDismiss,
            iconSize = 14.dp,
            tint = colors.textSecondary,
        )
    }
}

@Composable
private fun RemembranceCreateStepBar(
    current: Int,
    stepCount: Int,
) {
    val colors = AzkarTheme.colors
    val progress = stringResource(Res.string.collectionsDialogStepProgress, current + 1, stepCount)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) { contentDescription = progress },
        horizontalArrangement = Arrangement.spacedBy(CREATE_STEP_BAR_GAP),
    ) {
        repeat(stepCount) { index ->
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(CREATE_STEP_BAR_HEIGHT)
                        .clip(RoundedCornerShape(CREATE_STEP_BAR_RADIUS))
                        .background(if (index <= current) colors.primary else colors.border),
            )
        }
    }
}

@Composable
private fun RemembranceCreateActions(
    step: RemembranceCreateStep,
    lastStep: Boolean,
    navigation: RemembranceCreateNavigation,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SecondaryButton(text = stringResource(Res.string.collectionsActionCancel), onClick = navigation.onDismiss)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (step != RemembranceCreateStep.entries.first()) {
                SecondaryButton(
                    text = stringResource(Res.string.collectionsActionStepBack),
                    onClick = navigation.onBack,
                )
                Spacer(Modifier.width(CREATE_FIELD_GAP))
            }
            PrimaryButton(
                text =
                    if (lastStep) {
                        stringResource(Res.string.remembranceCreateActionConfirm)
                    } else {
                        stringResource(Res.string.collectionsActionNext)
                    },
                onClick = if (lastStep) navigation.onConfirm else navigation.onNext,
                leadingIcon = if (lastStep) Icons.Filled.Check else Icons.AutoMirrored.Filled.ArrowForward,
            )
        }
    }
}

private data class RemembranceCreateNavigation(
    val onDismiss: () -> Unit,
    val onBack: () -> Unit,
    val onNext: () -> Unit,
    val onConfirm: () -> Unit,
)

@Preview
@Composable
private fun RemembranceCreateDialogPreview() {
    AzkarThemePreview(width = 640.dp, height = 680.dp) {
        RemembranceCreateDialog(
            draft = RemembranceCreateDraft(textLocale = Locale.forLanguageTag("ar")),
            availableTags = listOf("morning", "sleep", "travel").map(TagOption::of),
            onDismiss = {},
            onConfirm = {},
        )
    }
}

@AzkarVariantsPreview
@Composable
internal fun RemembranceCreateTextRequiredPreview() {
    val draft = remember { RemembranceCreateDraft(textLocale = Locale.forLanguageTag("ar")).apply { submit() } }
    Column(modifier = Modifier.width(560.dp).padding(24.dp)) {
        RemembranceTextStep(draft)
    }
}

@AzkarVariantsPreview
@Composable
internal fun RemembranceCreateLongTextPreview() {
    val draft =
        remember {
            RemembranceCreateDraft(textLocale = Locale.forLanguageTag("ar")).apply {
                updateText(
                    "اللَّهُمَّ إِنِّي أَسْأَلُكَ الْعَافِيَةَ فِي الدُّنْيَا وَالْآخِرَةِ، " +
                        "وَأَسْأَلُكَ الْعَفْوَ وَالْعَافِيَةَ فِي دِينِي وَدُنْيَايَ",
                )
            }
        }
    Column(modifier = Modifier.width(560.dp).padding(24.dp)) {
        RemembranceTextStep(draft)
    }
}

@AzkarVariantsPreview
@Composable
internal fun RemembranceCreateTranslationsPreview() {
    val draft =
        remember {
            RemembranceCreateDraft(textLocale = Locale.forLanguageTag("ar")).apply {
                updateText("اللَّهُمَّ إِنِّي أَسْأَلُكَ الْعَافِيَةَ")
                addTranslation(Locale.ENGLISH)
                updateTranslationText(Locale.ENGLISH, "O Allah, I ask You for wellbeing.")
                updateExplanation(Locale.ENGLISH, "A supplication for safety in body and faith.")
            }
        }
    Column(modifier = Modifier.width(560.dp).padding(24.dp)) {
        RemembranceTranslationsStep(draft)
    }
}

@AzkarVariantsPreview
@Composable
internal fun RemembranceCreateTagsPreview() {
    val draft =
        remember {
            RemembranceCreateDraft(textLocale = Locale.forLanguageTag("ar")).apply {
                updateText("اللَّهُمَّ إِنِّي أَسْأَلُكَ الْعَافِيَةَ")
                toggleTag("morning")
            }
        }
    Column(modifier = Modifier.width(560.dp).padding(24.dp)) {
        TagPickerPanel(
            draft = draft,
            availableTags = listOf("morning", "sleep", "travel").map(TagOption::of),
        )
    }
}

private val CREATE_MIN_WIDTH = 440.dp
private val CREATE_MAX_WIDTH = 600.dp
private val CREATE_BODY_MAX_HEIGHT = 340.dp
private val CREATE_BLOCK_GAP = 16.dp
private val CREATE_FIELD_GAP = 14.dp
private val CREATE_LABEL_GAP = 5.dp
private val CREATE_HEADER_BADGE = 40.dp
private val CREATE_HEADER_BADGE_RADIUS = 12.dp
private val CREATE_HEADER_BADGE_GLYPH = 20.dp
private val CREATE_HEADER_GAP = 12.dp
private val CREATE_HEADER_TITLE_SP = 17.sp
private val CREATE_HEADER_SUBTITLE_SP = 11.sp
private val CREATE_HEADER_STEP_GAP = 12.dp
private val CREATE_LABEL_SP = 12.sp
private val CREATE_HINT_SP = 11.sp
private val CREATE_TEXT_FIELD_HEIGHT = 116.dp
private val CREATE_TRANSLATION_FIELD_HEIGHT = 84.dp
private val CREATE_EXPLANATION_FIELD_HEIGHT = 68.dp
private val CREATE_VALIDATION_GAP = 5.dp
private val CREATE_TRANSLATION_BLOCK_GAP = 24.dp
private val CREATE_STEP_BAR_HEIGHT = 4.dp
private val CREATE_STEP_BAR_RADIUS = 2.dp
private val CREATE_STEP_BAR_GAP = 4.dp
