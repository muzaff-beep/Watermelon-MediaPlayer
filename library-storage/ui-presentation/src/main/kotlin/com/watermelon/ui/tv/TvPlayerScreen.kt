package com.watermelon.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watermelon.common.model.ParsedSubtitle
import com.watermelon.common.model.PlaybackState
import com.watermelon.common.model.SubtitleStyle
import com.watermelon.common.model.UserIntent
import com.watermelon.ui.R
import com.watermelon.ui.components.SubtitleOverlay
import com.watermelon.ui.theme.PlayerColors
import com.watermelon.ui.theme.WatermelonSpacing
import com.watermelon.ui.viewmodel.PlayerViewModel

/**
 * TV player composition (Manifest §8) — a separate D-pad-first composition from
 * [com.watermelon.ui.screens.PhonePlayerScreen], sharing only the playback core
 * ([PlayerViewModel]) and video [surface], per this module's design: phone and TV are two
 * compositions sharing one playback core, not a single screen with conditionals.
 *
 * Deliberately excluded, matching the agreed TV scope: pinch-zoom, brightness/volume drag,
 * VHS shader, PiP, rotation lock. None of these map to a D-pad + OK input model, and the VHS
 * shader in particular relies on touch-driven hold gestures that don't exist on a remote.
 *
 * Requirements satisfied:
 *  - D-pad-first: every action reachable by up/down/left/right + OK only (see
 *    [TvPlayerControls] for the key-event handling).
 *  - Works with a partially-broken remote: OK on the Play/Pause button is the single
 *    guaranteed control; nothing else is required to operate the player.
 *  - Minimal action set: transport (previous/play-pause/next) + seek-hold + subtitle nudge.
 *  - Large, focusable targets with a visible focus ring (no reliance on color alone).
 */
@Composable
fun TvPlayerScreen(
    viewModel: PlayerViewModel,
    surface: @Composable (Modifier) -> Unit,
    durationMs: Long,
    hasPreviousTrack: Boolean,
    hasNextTrack: Boolean,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    subtitleTrack: ParsedSubtitle? = null,
    subtitleStyle: SubtitleStyle = SubtitleStyle(),
    modifier: Modifier = Modifier
) {
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val positionMs by viewModel.currentPositionMs.collectAsStateWithLifecycle()
    val isPlaying = playbackState == PlaybackState.PLAYING

    // Auto-advance on natural end-of-video — same reasoning as PhonePlayerScreen's
    // identical effect: reuses the same onSkipNext the manual Next button calls, so it
    // inherits the correct Continue Watching scoping (queue is seeded per-screen, not
    // re-derived here), and naturally never fires against an active sleep timer (see
    // PhonePlayerScreen's LaunchedEffect doc for why the StateFlow conflation makes that
    // safe without an explicit check here).
    LaunchedEffect(playbackState) {
        if (playbackState == PlaybackState.ENDED && hasNextTrack) {
            onSkipNext()
        }
    }

    // Local, render-time-only subtitle offset nudge (Up/Down). Not persisted — matches the
    // scope of what TvPlayerControls exposes today; wiring this into the storage-backed
    // SubtitleOffsets table is a separate change shared with the phone screen, which doesn't
    // yet expose a live re-nudge control either.
    var liveOffsetMs by remember(subtitleTrack) { mutableLongStateOf(subtitleTrack?.offsetMs ?: 0L) }
    val effectiveSubtitle = remember(subtitleTrack, liveOffsetMs) {
        subtitleTrack?.copy(offsetMs = liveOffsetMs)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PlayerColors.current.background)
    ) {
        surface(Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                // Overscan-safe padding for 10-foot readability (Manifest §8).
                .padding(
                    horizontal = dimensionResource(R.dimen.tv_overscan_horizontal),
                    vertical = dimensionResource(R.dimen.tv_overscan_vertical)
                )
        ) {
            // Status text at top — visible confirmation of playback/buffering/error state,
            // since a TV viewer sitting across the room can't rely on subtle chrome cues.
            when (playbackState) {
                PlaybackState.BUFFERING -> Text("Buffering…", color = PlayerColors.current.textPrimary)
                PlaybackState.LOADING -> Text("Loading…", color = PlayerColors.current.textPrimary)
                PlaybackState.ERROR -> Text("Playback error", color = PlayerColors.current.textPrimary)
                else -> {}
            }

            val activeCue = remember(effectiveSubtitle, positionMs) { effectiveSubtitle?.cueAt(positionMs) }
            SubtitleOverlay(
                text = activeCue?.displayText,
                isRtl = activeCue?.baseRtl ?: false,
                style = subtitleStyle,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.weight(1f))

            // Read-only progress — seeking is via hold-left/hold-right in TvPlayerControls,
            // not direct seekbar drag (a D-pad can't drag; a draggable-looking bar with no
            // drag support would be a worse affordance than no bar at all).
            val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = PlayerColors.current.accent,
                trackColor = PlayerColors.current.sheetBackground
            )

            Text(
                text = "${formatTvTime(positionMs)} / ${formatTvTime(durationMs)}",
                color = PlayerColors.current.textPrimary,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = WatermelonSpacing.xs, bottom = WatermelonSpacing.sm)
            )

            TvPlayerControls(
                isPlaying = isPlaying,
                hasPreviousTrack = hasPreviousTrack,
                hasNextTrack = hasNextTrack,
                onIntent = viewModel::onIntent,
                onSkipPrevious = onSkipPrevious,
                onSkipNext = onSkipNext,
                onSubtitleNudge = { deltaMs -> liveOffsetMs += deltaMs },
                onSeekHold = { direction ->
                    val target = (positionMs + direction * SEEK_STEP_MS).coerceIn(0L, durationMs)
                    viewModel.onIntent(UserIntent.Seek(target))
                }
            )
        }
    }
}

/** Per-repeat seek amount for D-pad hold-left/hold-right — matches the phone screen's base
 *  10s swipe-to-seek granularity so the two platforms feel consistent. */
private const val SEEK_STEP_MS = 10_000L

private fun formatTvTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
