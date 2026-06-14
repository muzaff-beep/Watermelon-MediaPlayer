package com.watermelon.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow

/**
 * Renders the active subtitle cue with correct bidirectional layout.
 *
 * The text passed in is already bidi-formatted (base direction resolved, opposite-direction
 * runs isolated) by the parser. This component only handles alignment and styling:
 *   - RTL cue  → aligned to the right
 *   - LTR cue  → aligned to the left
 * decided per cue via [isRtl], never globally.
 */
@Composable
fun SubtitleOverlay(
    text: String?,
    isRtl: Boolean,
    modifier: Modifier = Modifier
) {
    if (text.isNullOrBlank()) return
    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            text = text,
            color = Color.White,
            textAlign = if (isRtl) TextAlign.Right else TextAlign.Left,
            style = TextStyle(
                fontSize = 18.sp,
                background = Color.Black.copy(alpha = 0.55f)
            ),
            overflow = TextOverflow.Visible,
            modifier = Modifier
                .align(if (isRtl) Alignment.CenterEnd else Alignment.CenterStart)
                .wrapContentWidth()
                .padding(4.dp)
        )
    }
}
