package com.watermelon.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.watermelon.ui.theme.PlayerColors

/**
 * Transient brightness/volume indicator shown during a vertical drag gesture. A vertical
 * capsule track filled (bottom-up) by [fraction], with a level-aware [icon] above it.
 * All colors from [PlayerColors] tokens.
 *
 * The caller decides when to show/hide this (e.g. fade out shortly after the gesture ends).
 */
@Composable
fun LevelIndicator(
    fraction: Float,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(48.dp)
            .height(180.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = PlayerColors.levelIcon,
            modifier = Modifier.size(24.dp)
        )
        Canvas(modifier = Modifier.weight(1f).width(8.dp)) {
            drawLevel(fraction.coerceIn(0f, 1f))
        }
    }
}

private fun DrawScope.drawLevel(fraction: Float) {
    val w = size.width
    val h = size.height
    val radius = CornerRadius(w / 2f, w / 2f)
    // track
    drawRoundRect(
        color = PlayerColors.levelTrack,
        topLeft = Offset(0f, 0f),
        size = Size(w, h),
        cornerRadius = radius
    )
    // fill from the bottom up
    val fillH = h * fraction
    drawRoundRect(
        color = PlayerColors.levelFill,
        topLeft = Offset(0f, h - fillH),
        size = Size(w, fillH),
        cornerRadius = radius
    )
}
