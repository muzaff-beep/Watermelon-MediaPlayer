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
 * position.
 *
 * Movement is discrete, not a continuous pixel-to-time mapping: each tick that crosses
 * the center pointer moves position by exactly [secondsPerTick] seconds, forward or
 * backward depending on drag direction. This is a deliberate redesign from an earlier
 * version that mapped drag pixels to milliseconds via a fixed ratio — that made long
 * videos practically unreachable, since covering a 2-hour video required dragging tens of
 * thousands of pixels in one continuous gesture with no way to re-base mid-drag. Discrete
 * per-tick stepping means reaching anywhere in any video is a matter of how many ticks you
 * drag through, not how many pixels — consistent regardless of video length, and the same
 * mental model as a real tuner dial or click-wheel (each detent is a fixed step).
 *
 * @param positionMs current playback position
 * @param durationMs total duration
 * @param onSeek invoked with the target position while scrubbing / on drag end
 * @param onScrubChange true when scrubbing starts, false when it ends
 * @param secondsPerTick how many seconds each tick crossing the pointer represents (1-20,
 *   adjustable in Settings)
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
    secondsPerTick: Int = 5,
    onScrubChange: (Boolean) -> Unit = {}
) {
    var scrubbing by remember { mutableStateOf(false) }
    // While scrubbing, position is tracked as an absolute ms offset from the drag start —
    // advanced in whole-tick steps as drag distance crosses each tick's pixel spacing.
    var scrubPositionMs by remember { mutableStateOf(0L) }
    // Raw, unconsumed drag distance since the last tick was crossed. Whenever this exceeds
    // one tick's pixel width, we step scrubPositionMs by one tick's worth of seconds and
    // subtract that width back out — same idea as a mouse wheel accumulating sub-notch
    // motion until it clicks over.
    var pendingDragPx by remember { mutableStateOf(0f) }

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
    val stepMs = secondsPerTick.coerceIn(1, 20) * 1000L

    // Fixed pixel spacing per tick — how far you need to drag to cross one tick, i.e. one
    // step of secondsPerTick. Independent of video length by design (see class doc).
    val tickSpacingPx = 28f

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
            .pointerInput(durationMs, stepMs) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        scrubbing = true
                        onScrubChange(true)
                        scrubPositionMs = livePositionMs
                        pendingDragPx = 0f
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
                    pendingDragPx += dragAmount
                    // Step forward/backward by whole ticks as accumulated drag distance
                    // crosses each tick's pixel width — dragging right advances (tuning
                    // forward), left rewinds. Looping (not just one if-check) means a fast
                    // flick that crosses several ticks' worth of pixels in one callback
                    // still advances by the correct number of steps, not just one.
                    while (pendingDragPx >= tickSpacingPx) {
                        pendingDragPx -= tickSpacingPx
                        scrubPositionMs = (scrubPositionMs + stepMs).coerceAtMost(durationMs.coerceAtLeast(0L))
                    }
                    while (pendingDragPx <= -tickSpacingPx) {
                        pendingDragPx += tickSpacingPx
                        scrubPositionMs = (scrubPositionMs - stepMs).coerceAtLeast(0L)
                    }
                }
            }
    ) {
        drawTunerDial(colors, displayPositionMs, tickSpacingPx, stepMs)
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
    tickSpacingPx: Float,
    stepMs: Long
) {
    val cx = size.width / 2f
    val cy = size.height / 2f

    // Ticks are spaced tickSpacingPx apart on screen — the exact same spacing the drag
    // gesture uses to decide when a tick has been "crossed" (see pointerInput above), so
    // what you see sliding past the pointer is exactly what's driving the step count, not
    // an independently-tuned visual approximation of it. Each tick represents stepMs.
    val tickCount = (size.width / tickSpacingPx).toInt() + 4
    val startIndex = -(tickCount / 2)

    for (i in startIndex..(tickCount / 2)) {
        val tickTimeMs = positionMs + (i * stepMs)
        if (tickTimeMs < 0) continue

        val x = cx + i * tickSpacingPx
        if (x < -4f || x > size.width + 4f) continue

        // Every 10th tick is a "major" mark — taller, like the numbered frequency ticks
        // on a radio dial. The rest are short minor ticks.
        val isMajor = i % 10 == 0
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
    // as ticks scroll past underneath it.
    val pointerW = 3.dp.toPx()
    val pointerH = size.height
    drawRoundRect(
        color = colors.seekBarFill,
        topLeft = Offset(cx - pointerW / 2f, cy - pointerH / 2f),
        size = Size(pointerW, pointerH),
        cornerRadius = CornerRadius(pointerW / 2f, pointerW / 2f)
    )
}
