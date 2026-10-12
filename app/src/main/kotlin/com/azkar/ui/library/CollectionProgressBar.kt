package com.azkar.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.collectionDetailActionResetProgress
import com.azkar.generated.resources.collectionDetailResetConfirmBody
import com.azkar.generated.resources.collectionDetailResetConfirmTitle
import com.azkar.generated.resources.collectionsActionCancel
import com.azkar.ui.components.AzkarDialogSurface
import com.azkar.ui.components.PrimaryButton
import com.azkar.ui.components.SecondaryButton
import com.azkar.ui.theme.AzkarTheme
import org.jetbrains.compose.resources.stringResource
import java.time.Duration

/*
 * The progress strip on a collection page, and the confirmation that clears it.
 *
 * The strip itself is [MemberProgress] on [CollectionMemberToolbar] now. It used to live here as a
 * bordered card between the toolbar and the members, which put a box, two gaps and a second
 * Start/Resume button between the reader and the first passage — and the band above had to give up
 * the corner it printed the same count in, because a page that says "0 of 61" twice is a page that
 * has lost track of which one is the answer.
 *
 * What stayed is what the strip could not carry: the dialog. Reset is the one action on this page
 * that cannot be undone from anywhere else, and it is one tap from a row of controls, so it asks
 * first even though the data it touches is cheap to rebuild.
 */

/**
 * Renders [duration] the way a person would say it: hours and minutes when there are hours, whole
 * minutes otherwise, and a qualified "under a minute" rather than a misleading "0m".
 *
 * `internal` rather than private because the detail page prints the same figure in three places — the
 * hero's facts row, its toolbar's progress line, and the session header's own strip — and two
 * spellings of "5m" on one screen is exactly the sort of drift this file's comments keep complaining
 * about elsewhere.
 *
 * This file is named for the strip it used to contain. That strip is now [MemberProgress], on the
 * toolbar row, because a progress summary that owns a bordered card beneath the band is one more box
 * between the reader and the first passage; what stayed behind is the confirmation that clears those
 * counts and the one way the remaining time is spelled.
 */
internal fun formatDuration(duration: Duration): String {
    val totalMinutes = duration.toMinutes()
    if (totalMinutes < 1) {
        return "<1m"
    }
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

/**
 * Asks before throwing the counts away.
 *
 * Reset is the one action on this page that cannot be undone from anywhere else, and it is reachable
 * in one tap from a progress bar that is itself one tap away — so it is confirmed even though the
 * data it touches is cheap to rebuild. The body says what survives, because "reset" could reasonably
 * be read as emptying the collection.
 */
@Composable
internal fun ConfirmProgressResetDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        AzkarDialogSurface(modifier = Modifier.widthIn(min = 380.dp, max = 520.dp)) {
            // A Column, like every other dialog on this surface. [AzkarDialogSurface] is a Box, so
            // three children handed to it directly all measure against the same origin and paint on
            // top of one another — which is how this dialog ended up with its buttons sitting across
            // the middle of its own body copy.
            Column {
                Text(
                    text = stringResource(Res.string.collectionDetailResetConfirmTitle),
                    color = AzkarTheme.colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(Res.string.collectionDetailResetConfirmBody),
                    color = AzkarTheme.colors.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Row(
                    modifier = Modifier.padding(top = 18.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    SecondaryButton(
                        text = stringResource(Res.string.collectionsActionCancel),
                        onClick = onDismiss,
                    )
                    Spacer(Modifier.width(8.dp))
                    PrimaryButton(
                        text = stringResource(Res.string.collectionDetailActionResetProgress),
                        onClick = onConfirm,
                    )
                }
            }
        }
    }
}
