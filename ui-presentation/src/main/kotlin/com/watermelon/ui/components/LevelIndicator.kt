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
import com.watermelon.ui.theme.WatermelonSpacing

/**
 * Transient brightness/volume indicator shown during a vertical drag gesture. A vertical
 * capsule track filled (bottom-up) by [fraction], with a level-aware [icon] above it.
 * All colors from [PlayerColors] tokens.
 *
 * The caller decides when to show/hide this (e.g. fade out shortly after the gesture ends).
 *
 * [isWarning] routes the fill/icon tint to the Warning Yellow semantic token instead of
 * the normal accent-driven [PlayerColors.Scheme.levelFill] — reused by callers that want
 * this same vertical-capsule HUD language for a transient buffering/caution state, kept
 * visually distinct from hard errors (which stay red).
 */
@Composable
fun LevelIndicator(
    fraction: Float,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    isWarning: Boolean = false
) {
    val colors = PlayerColors.current
    Column(
        modifier = modifier
            .width(48.dp)
            .height(180.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.sm)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isWarning) colors.warning else colors.levelIcon,
            modifier = Modifier.size(24.dp)
        )
        Canvas(modifier = Modifier.weight(1f).width(8.dp)) {
            drawLevel(colors, fraction.coerceIn(0f, 1f), isWarning)
        }
    }
}

private fun DrawScope.drawLevel(colors: PlayerColors.Scheme, fraction: Float, isWarning: Boolean) {
    val w = size.width
    val h = size.height
    val radius = CornerRadius(w / 2f, w / 2f)
    // track
    drawRoundRect(
        color = colors.levelTrack,
        topLeft = Offset(0f, 0f),
        size = Size(w, h),
        cornerRadius = radius
    )
    // fill from the bottom up
    val fillH = h * fraction
    drawRoundRect(
        color = if (isWarning) colors.warning else colors.levelFill,
        topLeft = Offset(0f, h - fillH),
        size = Size(w, fillH),
        cornerRadius = radius
    )
}
