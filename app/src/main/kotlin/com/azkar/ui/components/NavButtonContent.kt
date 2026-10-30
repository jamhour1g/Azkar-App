package com.azkar.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun NavButtonContent(
    text: String,
    leadingIcon: ImageVector?,
    bounds: NavButtonBounds,
    containerColor: androidx.compose.runtime.State<Color>,
    contentColor: androidx.compose.runtime.State<Color>,
) {
    Row(
        modifier =
            Modifier
                .onGloballyPositioned { bounds.content = it }
                .drawBehind {
                    val button = bounds.button
                    val content = bounds.content
                    if (button != null && content != null && bounds.isAttached()) {
                        drawRect(
                            color = containerColor.value,
                            topLeft = content.localPositionOf(button, Offset.Zero),
                            size = Size(button.size.width.toFloat(), button.size.height.toFloat()),
                        )
                    }
                },
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                painter = rememberVectorPainter(leadingIcon),
                tint = { contentColor.value },
                contentDescription = null,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            color = { contentColor.value },
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

// Draw inside the button so Material's animated shape clips the color during a press.
internal class NavButtonBounds {
    var button: LayoutCoordinates? = null
    var content: LayoutCoordinates? = null

    fun isAttached(): Boolean = button?.isAttached == true && content?.isAttached == true
}
