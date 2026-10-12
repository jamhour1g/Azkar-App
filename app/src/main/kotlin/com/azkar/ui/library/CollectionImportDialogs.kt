package com.azkar.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.collectionImportAdd
import com.azkar.generated.resources.collectionImportAddTitle
import com.azkar.generated.resources.collectionImportCreateCount
import com.azkar.generated.resources.collectionImportEmptyHint
import com.azkar.generated.resources.collectionImportEmptyTitle
import com.azkar.generated.resources.collectionImportHint
import com.azkar.generated.resources.collectionImportMergeCount
import com.azkar.generated.resources.collectionImportPayloadLabel
import com.azkar.generated.resources.collectionImportPayloadPlaceholder
import com.azkar.generated.resources.collectionImportPlanned
import com.azkar.generated.resources.collectionImportPreview
import com.azkar.generated.resources.collectionImportReportTitle
import com.azkar.generated.resources.collectionImportResultCreated
import com.azkar.generated.resources.collectionImportResultMembers
import com.azkar.generated.resources.collectionImportResultMerged
import com.azkar.generated.resources.collectionImportSkippedLabel
import com.azkar.generated.resources.collectionImportUnmatchedCount
import com.azkar.generated.resources.collectionImportUnmatchedLabel
import com.azkar.generated.resources.collectionImportUnreadableHint
import com.azkar.generated.resources.collectionImportUnreadableTitle
import com.azkar.generated.resources.collectionsActionCancel
import com.azkar.ui.components.AzkarOutlinedField
import com.azkar.ui.components.SecondaryButton
import com.azkar.ui.library.data.ImportPlanResult
import com.azkar.ui.library.data.ImportReport
import com.azkar.ui.theme.AzkarTheme
import org.jetbrains.compose.resources.stringResource

private const val LISTED_UNMATCHED = 8
private val HINT_SP = 11.sp

/**
 * The paste-a-document half of the transfer gesture.
 *
 * Three states share one dialog because they share one question — what would this do? — and the user
 * should be able to see the answer, change the input, and see the answer change without losing
 * anything they typed. A plan that is showing can be confirmed; a payload that has just been edited
 * has no plan, so the confirm button is gone rather than stale.
 *
 * Nothing here decides anything. [CollectionImportState] holds the plan and the report, so the same
 * decision survives the tree being rebuilt on a locale switch.
 */
@Composable
internal fun ImportCollectionDialog(
    state: CollectionImportState,
    onDismiss: () -> Unit,
) {
    TransferDialogFrame(
        title = stringResource(Res.string.collectionImportAddTitle),
        onDismiss = onDismiss,
        footer = {
            ImportFooter(state = state, onDismiss = onDismiss)
        },
    ) {
        Column {
            Text(
                text = stringResource(Res.string.collectionImportHint),
                color = AzkarTheme.colors.textSecondary,
                fontSize = HINT_SP,
            )
            Spacer(Modifier.height(10.dp))
            TransferFormatChips(format = state.format, onFormatChanged = state::onFormatChanged)
            Spacer(Modifier.height(10.dp))
            TransferLabel(text = stringResource(Res.string.collectionImportPayloadLabel))
            Spacer(Modifier.height(4.dp))
            AzkarOutlinedField(
                value = state.payload,
                onValueChange = state::onPayloadChanged,
                placeholder = stringResource(Res.string.collectionImportPayloadPlaceholder),
                singleLine = false,
                fontSize = 12.sp,
                minHeight = 110.dp,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            ImportOutcome(state = state)
        }
    }
}

/**
 * Whatever the payload has produced so far: a refusal, a plan, a report, or nothing.
 *
 * The report wins over the plan because the plan is cleared the moment a confirm is accepted — a
 * dialog that went quiet the instant it was useful would be a dialog the user closes.
 */
@Composable
private fun ImportOutcome(state: CollectionImportState) {
    val report = state.report
    val plan = state.plan
    when {
        report != null -> ImportReportBody(report)
        state.isUnreadable ->
            TransferNotice(
                title = stringResource(Res.string.collectionImportUnreadableTitle),
                hint = stringResource(Res.string.collectionImportUnreadableHint),
                isProblem = true,
            )

        plan == null -> PreviewButton(state)
        plan.isEmpty ->
            TransferNotice(
                title = stringResource(Res.string.collectionImportEmptyTitle),
                hint = stringResource(Res.string.collectionImportEmptyHint),
                isProblem = false,
            )

        else -> ImportPlanBody(plan)
    }
}

/**
 * The only action before a payload has been read.
 *
 * A second button beside the import one rather than a live import button, because the whole point of
 * the encoding switch is that the same bytes mean different things in each: a document read as the
 * wrong one is a plausible-looking plan that would quietly do the wrong thing.
 */
@Composable
private fun PreviewButton(state: CollectionImportState) {
    SecondaryButton(
        text = stringResource(Res.string.collectionImportPreview),
        onClick = state::preview,
    )
}

/**
 * Cancel, then either "check it" or "do it".
 *
 * Which of the two is on the button is the whole state machine: before a read there is nothing to
 * confirm, with a plan the button applies it, and after a report the only thing left to do is read
 * the next document.
 */
@Composable
private fun ImportFooter(
    state: CollectionImportState,
    onDismiss: () -> Unit,
) {
    val settled = state.report != null
    DialogFooter(
        cancelLabel = stringResource(Res.string.collectionsActionCancel),
        confirmLabel = stringResource(Res.string.collectionImportAdd),
        onCancel = onDismiss,
        onConfirm =
            when {
                settled -> state::preview
                state.canConfirm -> state::confirm
                else -> ({})
            },
    )
}

/**
 * The counts and the names the user has to be able to disagree with.
 *
 * The unmatched passages are listed by name and not just counted, because "three remembrances were
 * not recognised" is only actionable if you can see which three. The list is capped: a payload
 * quoting a passage from a newer build should not push the import button off the dialog.
 */
@Composable
private fun ImportPlanBody(plan: ImportPlanResult) {
    Column {
        TransferLabel(text = stringResource(Res.string.collectionImportPlanned))
        Spacer(Modifier.height(6.dp))
        TransferStatLine(
            label = stringResource(Res.string.collectionImportCreateCount),
            value = plan.created.toString(),
        )
        TransferStatLine(
            label = stringResource(Res.string.collectionImportMergeCount),
            value = plan.merged.toString(),
        )
        TransferStatLine(
            label = stringResource(Res.string.collectionImportUnmatchedCount),
            value = plan.unmatched.size.toString(),
        )
        if (plan.skippedOfficial.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = AzkarTheme.colors.border)
            Spacer(Modifier.height(8.dp))
            TransferLabel(text = stringResource(Res.string.collectionImportSkippedLabel))
            TransferNameList(names = plan.skippedOfficial)
        }
        if (plan.unmatched.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            TransferLabel(text = stringResource(Res.string.collectionImportUnmatchedLabel))
            TransferNameList(names = plan.unmatched.take(LISTED_UNMATCHED).map { it.text })
        }
    }
}

@Composable
private fun ImportReportBody(report: ImportReport) {
    Column {
        TransferLabel(text = stringResource(Res.string.collectionImportReportTitle))
        Spacer(Modifier.height(6.dp))
        TransferStatLine(
            label = stringResource(Res.string.collectionImportResultCreated),
            value = report.created.toString(),
        )
        TransferStatLine(
            label = stringResource(Res.string.collectionImportResultMerged),
            value = report.merged.toString(),
        )
        TransferStatLine(
            label = stringResource(Res.string.collectionImportResultMembers),
            value = report.membersAdded.toString(),
        )
    }
}
