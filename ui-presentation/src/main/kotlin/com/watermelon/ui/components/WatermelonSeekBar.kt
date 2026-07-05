package com.watermelon.ui.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import com.watermelon.ui.theme.PlayerColors
import kotlin.math.roundToLong

/**
 * Custom watermelon-themed seek bar. Played-progress + thumb only (no buffer — offline-first).
 * All colors come from [PlayerColors] semantic tokens, so the look retunes centrally.
 *
 * @param positionMs current playback position
 * @param durationMs total duration
 * @param onSeek invoked with the target position while scrubbing / on tap
 * @param onScrubChange optional: true when scrubbing starts, false when it ends
 */
@Composable
fun WatermelonSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    trackHeight: Dp = 4.dp,
    thumbRadius: Dp = 7.dp,
    onScrubChange: (Boolean) -> Unit = {}
) {
    var scrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableStateOf(0f) }
    var widthPx by remember { mutableStateOf(1f) }

    val liveFraction = if (durationMs > 0)
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val fraction = if (scrubbing) scrubFraction else liveFraction
    val colors = PlayerColors.current

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(thumbRadius * 2 + 8.dp)
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    val f = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeek((f * durationMs).roundToLong())
                }
            }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        scrubbing = true; onScrubChange(true)
                        scrubFraction = (offset.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        onSeek((scrubFraction * durationMs).roundToLong())
                        scrubbing = false; onScrubChange(false)
                    },
                    onDragCancel = { scrubbing = false; onScrubChange(false) }
                ) { change, _ ->
                    scrubFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                }
            }
    ) {
        widthPx = size.width
        drawSeekBar(colors, fraction, trackHeight.toPx(), thumbRadius.toPx(), scrubbing)
    }
}

private fun DrawScope.drawSeekBar(
    colors: PlayerColors.Scheme,
    fraction: Float,
    trackH: Float,
    thumbR: Float,
    scrubbing: Boolean
) {
    val cy = size.height / 2f
    val left = thumbR
    val right = size.width - thumbR
    val usableW = (right - left).coerceAtLeast(0f)
    val fillX = left + usableW * fraction

    // track (dim)
    drawRoundRect(
        color = colors.seekBarTrack,
        topLeft = Offset(left, cy - trackH / 2f),
        size = Size(usableW, trackH),
        cornerRadius = CornerRadius(trackH / 2f, trackH / 2f)
    )
    // played fill (watermelon red)
    drawRoundRect(
        color = colors.seekBarFill,
        topLeft = Offset(left, cy - trackH / 2f),
        size = Size((fillX - left).coerceAtLeast(0f), trackH),
        cornerRadius = CornerRadius(trackH / 2f, trackH / 2f)
    )
    // thumb: white ring + red core, grows slightly while scrubbing
    val r = if (scrubbing) thumbR * 1.25f else thumbR
    drawCircle(color = colors.seekBarThumbRing, radius = r + 2f, center = Offset(fillX, cy))
    drawCircle(color = colors.seekBarThumb, radius = r, center = Offset(fillX, cy))
}
    
