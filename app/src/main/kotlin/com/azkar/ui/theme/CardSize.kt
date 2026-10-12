package com.azkar.ui.theme

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The three card surfaces the JavaFX stylesheets defined: `.card`, `.card-sm` and `.card-lg`.
 *
 * [padding] replaces the stylesheet's `padding` and [elevation] its `dropshadow` blur radius.
 */
enum class CardSize(
    val padding: Dp,
    val elevation: Dp,
) {
    SM(16.dp, 2.dp),
    MD(20.dp, 4.dp),
    LG(24.dp, 6.dp),
}
