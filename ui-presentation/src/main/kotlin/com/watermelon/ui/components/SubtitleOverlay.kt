package com.watermelon.ui.components

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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watermelon.common.model.SubtitleDirection
import com.watermelon.common.model.SubtitleStyle

/**
 * Renders one subtitle cue. Text arrives already bidi-formatted; [style] drives size, color,
 * weight/italic/underline. Direction: [style]'s enforce mode overrides the auto-detected
 * [isRtl]. Effects (stroke/shadow/shade/frame) arrive in ST2. Position (top/bottom) is
 * handled by the caller's alignment via style.position.
 */
@Composable
fun SubtitleOverlay(
    text: String?,
    isRtl: Boolean,
    style: SubtitleStyle = SubtitleStyle(),
    directionOverride: SubtitleDirection = style.direction,
    modifier: Modifier = Modifier
) {
    if (!style.enabled || text.isNullOrBlank()) return
    val rtl = when (directionOverride) {
        SubtitleDirection.AUTO -> isRtl
        SubtitleDirection.FORCE_RTL -> true
        SubtitleDirection.FORCE_LTR -> false
    }
    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            text = text,
            color = Color(style.textColorArgb),
            textAlign = if (rtl) TextAlign.Right else TextAlign.Left,
            fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal,
            textDecoration = if (style.underline) TextDecoration.Underline else TextDecoration.None,
            style = TextStyle(
                fontSize = style.sizeSp.sp,
                background = Color.Black.copy(alpha = 0.55f)
            ),
            overflow = TextOverflow.Visible,
            modifier = Modifier
                .align(if (rtl) Alignment.CenterEnd else Alignment.CenterStart)
                .wrapContentWidth()
                .padding(4.dp)
        )
    }
}