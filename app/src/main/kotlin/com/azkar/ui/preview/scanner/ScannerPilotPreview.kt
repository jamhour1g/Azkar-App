package com.azkar.ui.preview.scanner

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.azkar.ui.theme.AzkarPreviewWrapperProvider

/** A fixed-state canary for the opt-in Roborazzi desktop preview scanner. */
@Preview(widthDp = 360, heightDp = 96, showBackground = true)
@PreviewWrapper(AzkarPreviewWrapperProvider::class)
@Composable
fun ScannerPilotPreview() {
    Surface(Modifier.fillMaxSize(), color = Color.White) {
        Text("Morning remembrance")
    }
}
