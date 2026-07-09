package com.watermelon.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.watermelon.ui.theme.PlayerColors
import kotlin.math.roundToLong

/**
 * Analog-radio-tuner style seek control. A strip of evenly spaced tick marks scrolls
 * horizontally underneath a fixed red pointer at the center — exactly like spinning the
 * frequency dial on an old AM/FM radio, except the "frequency" being tuned is playback
 * position, down to the millisecond.
 *
 * Dragging left/right spins the dial: ticks slide under the fixed center pointer and the
 * mapped time position is reported via [onSeek]. There's no filled track — position is
 * communicated purely by which tick currently sits under the pointer, and by the
 * elapsed/remaining time labels the caller renders alongside it.
 *
 * @param positionMs current playback position
 * @param durationMs total duration
 * @param onSeek invoked with the target position while scrubbing / on drag end
 * @param onScrubChange true when scrubbing starts, false when it ends
 * @param dialWidth visual width of the tuner strip — intentionally narrower than the
 *   full screen so it reads as a physical dial rather than an edge-to-edge bar
 */
@Composable
fun WatermelonTunerSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    dialWidth: Dp = 260.dp,
    dialHeight: Dp = 40.dp,
    onScrubChange: (Boolean) -> Unit = {}
) {
    var scrubbing by remember { mutableStateOf(false) }
    // While scrubbing, position is tracked as an absolute ms offset from the drag start,
    // independent of tick pixel spacing — this is what makes it feel like spinning a wheel
    // rather than dragging a linear bar.
    var scrubPositionMs by remember { mutableStateOf(0L) }
    var dragAccumPx by remember { mutableStateOf(0f) }

    // If this composable is removed from the tree mid-drag — e.g. the user toggles the
    // tuner seek bar off in Settings while actively scrubbing, or navigates away — neither
    // onDragEnd nor onDragCancel fires (Compose just tears the pointerInput coroutine down),
    // so onScrubChange(false) would otherwise never be called and the caller's "currently
    // seeking" state gets stuck true forever, along with whatever visual indicator it
    // drives. Force it closed on disposal so scrubbing can never outlive this composable.
    DisposableEffect(Unit) {
        onDispose { if (scrubbing) onScrubChange(false) }
    }

    val livePositionMs = positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
    val displayPositionMs = if (scrubbing) scrubPositionMs else livePositionMs
    val colors = PlayerColors.current

    // Pixels-per-millisecond of the dial — controls how "sensitive" spinning feels.
    // Tuned so a full dial width of drag covers a modest, controllable time window
    // rather than the whole video, like fine-tuning a radio frequency.
    val msPerPx = 40f

    Canvas(
        modifier = modifier
            .width(dialWidth)
            .height(dialHeight)
            .semantics {
                contentDescription =
                    "Seek tuner, ${formatTunerTimeForA11y(livePositionMs)} of ${formatTunerTimeForA11y(durationMs)}"
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = if (durationMs > 0) (livePositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
                    range = 0f..1f
                )
                setProgress { targetFraction ->
                    val clamped = targetFraction.coerceIn(0f, 1f)
                    onSeek((clamped * durationMs).roundToLong())
                    true
                }
            }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        scrubbing = true
                        onScrubChange(true)
                        scrubPositionMs = livePositionMs
                        dragAccumPx = 0f
                    },
                    onDragEnd = {
                        onSeek(scrubPositionMs)
                        scrubbing = false
                        onScrubChange(false)
                    },
                    onDragCancel = {
                        scrubbing = false
                        onScrubChange(false)
                    }
                ) { change, dragAmount ->
                    change.consume()
                    dragAccumPx += dragAmount
                    // Dragging right spins the dial forward (like turning the tuner up),
                    // dragging left rewinds — ticks appear to slide the opposite direction.
                    val deltaMs = (dragAmount * msPerPx).roundToLong()
                    scrubPositionMs = (scrubPositionMs + deltaMs).coerceIn(0L, durationMs.coerceAtLeast(0L))
                }
            }
    ) {
        drawTunerDial(colors, displayPositionMs, msPerPx)
    }
}

/** "3:45" / "1:02:03.500" style formatting, with milliseconds, for a11y announcements. */
private fun formatTunerTimeForA11y(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun DrawScope.drawTunerDial(
    colors: PlayerColors.Scheme,
    positionMs: Long,
    msPerPx: Float
) {
    val cx = size.width / 2f
    val cy = size.height / 2f

    // Ticks are spaced every 500ms of "dial time" (in pixel terms, tickSpacingPx apart).
    // A tick's on-screen x position is determined by how far its own timestamp is from
    // the current position, converted through msPerPx — so as position changes, the
    // whole strip appears to slide under the fixed center pointer.
    val tickIntervalMs = 500L
    val tickSpacingPx = tickIntervalMs / msPerPx
    val tickCount = (size.width / tickSpacingPx).toInt() + 4
    val startIndex = -(tickCount / 2)

    for (i in startIndex..(tickCount / 2)) {
        val tickTimeMs = positionMs + (i * tickIntervalMs)
        if (tickTimeMs < 0) continue

        val x = cx + i * tickSpacingPx
        if (x < -4f || x > size.width + 4f) continue

        // Every 10th tick (5 seconds) is a "major" mark — taller, like the numbered
        // frequency ticks on a radio dial. The rest are short minor ticks.
        val isMajor = (tickTimeMs / tickIntervalMs) % 10 == 0L
        val baseTickH = if (isMajor) size.height * 0.7f else size.height * 0.4f
        val tickW = if (isMajor) 2.5f else 1.5f

        // Distance from the center pointer, 0 at center → 1 at either edge.
        val distFrac = (kotlin.math.abs(x - cx) / (size.width / 2f)).coerceIn(0f, 1f)

        // Cylinder/barrel falloff: height and alpha both fall off with distance from
        // center, using an eased curve (ease-in, distFrac²) rather than linear — this
        // keeps ticks near the pointer close to full size for longer, then tapers faster
        // toward the edges, reading as a curved surface turning away from the viewer
        // (like film sprockets around a reel, or a rotary dial seen edge-on) instead of a
        // flat bar that's simply dimmer at the ends. Height never fully collapses to 0 —
        // floored at 25% of base — so edge ticks stay visible as "receding," not erased.
        val falloff = 1f - (distFrac * distFrac)
        val tickH = baseTickH * (0.25f + 0.75f * falloff)
        val alpha = 1f - distFrac * 0.6f

        drawRoundRect(
            color = colors.seekBarTrack.copy(alpha = colors.seekBarTrack.alpha.coerceAtLeast(0.5f) * alpha + 0.2f),
            topLeft = Offset(x - tickW / 2f, cy - tickH / 2f),
            size = Size(tickW, tickH),
            cornerRadius = CornerRadius(tickW / 2f, tickW / 2f)
        )
    }

    // Fixed red pointer/needle at dead center — this is what "reads" the current time,
    // down to the millisecond, as ticks scroll past underneath it.
    val pointerW = 3.dp.toPx()
    val pointerH = size.height
    drawRoundRect(
        color = colors.seekBarFill,
        topLeft = Offset(cx - pointerW / 2f, cy - pointerH / 2f),
        size = Size(pointerW, pointerH),
        cornerRadius = CornerRadius(pointerW / 2f, pointerW / 2f)
    )
}
