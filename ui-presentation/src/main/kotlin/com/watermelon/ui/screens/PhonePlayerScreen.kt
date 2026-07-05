package com.watermelon.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watermelon.common.model.PlaybackState
import com.watermelon.common.model.SleepTimerMode
import com.watermelon.common.model.UserIntent
import com.watermelon.ui.R
import com.watermelon.ui.components.LevelIndicator
import com.watermelon.ui.components.SleepTimerDialog
import com.watermelon.ui.components.SubtitleOverlay
import com.watermelon.ui.components.WatermelonSeekBar
import com.watermelon.ui.player.VhsEffectController
import com.watermelon.ui.theme.PlayerColors
import com.watermelon.ui.utils.ScreenshotManager
import com.watermelon.ui.utils.ScreenshotResult
import com.watermelon.ui.viewmodel.PlayerViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Phone video player — X-Player-style, layered architecture.
 *
 * LAYER ORDER (bottom → top), each layer's touch handling is explicit:
 *   1. Video surface
 *   2. Gesture surface        — active ONLY when ui.gesturesEnabled (no sheet, not locked)
 *   3. Tap/scrim + controls   — controls are tap-toggled; a light gradient sits only behind
 *                               the top/bottom bars (NOT a full-screen pause dim)
 *   4. Transient indicators   — brightness/volume level, hold speed
 *   5. Panels / dialogs       — control panel, sleep timer (suspend auto-hide while open)
 *
 * VHS is fully external: this screen only calls vhs.configure / onSurfaceSize / setRewind /
 * effectOrNull. When VHS is disabled in settings the controller is a complete no-op.
 *
 * FF/FR hold gesture is core and stays here (hold → 2×, drag ramps 3/4/8×, left = reverse).
 */
@Composable
fun PhonePlayerScreen(
    viewModel: PlayerViewModel,
    vhs: VhsEffectController,
    vhsEnabled: Boolean,
    vhsIntensity: Float,
    durationMs: Long,
    surface: @Composable (Modifier) -> Unit,
    onBack: () -> Unit,
    uri: String = "",
    subtitleTrack: com.watermelon.common.model.ParsedSubtitle? = null,
    subtitleStyle: com.watermelon.common.model.SubtitleStyle = com.watermelon.common.model.SubtitleStyle(),
    screenshotMode: ScreenshotMode = ScreenshotMode.SINGLE,
    initialBrightness: Float = -1f,
    onPipClick: (() -> Unit)? = null,
    onBackgroundClick: ((Boolean) -> Unit)? = null,
    onBrightnessChange: ((Float) -> Unit)? = null,
    onSkipToTrack: ((String) -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    isFavourite: Boolean = false,
    onFavourite: ((Boolean) -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onLockChanged: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(AudioManager::class.java) }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    val scope = rememberCoroutineScope()

    val position by viewModel.currentPositionMs.collectAsStateWithLifecycle()
    val isSeekingFast by viewModel.isSeekingFast.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
    val isShuffled by viewModel.shuffleEnabled.collectAsStateWithLifecycle()
    val isPlaying = playbackState == PlaybackState.PLAYING

    // ── Single UI state holder ──────────────────────────────────────────────
    val ui = remember { PlayerUiState() }

    // Player feature state
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var currentRatio by remember { mutableStateOf(VideoRatio.FILL) }
    var scale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var currentOrientation by rememberSaveable { mutableStateOf(ScreenOrientation.AUTO) }
    var showControlPanel by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var screenshotMessage by remember { mutableStateOf<String?>(null) }
    var isPiPEnabled by remember { mutableStateOf(false) }
    var isBackgroundEnabled by remember { mutableStateOf(false) }

    // Gesture transient state
    var isHolding by remember { mutableStateOf(false) }
    var isPointerDown by remember { mutableStateOf(false) }
    var holdIsLeft by remember { mutableStateOf(false) }
    var holdSpeed by remember { mutableFloatStateOf(2f) }
    var seekFrac by remember { mutableFloatStateOf(0f) }

    var currentVolume by remember { mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    var volumeFraction by remember {
        mutableFloatStateOf(if (maxVolume > 0) currentVolume.toFloat() / maxVolume else 0f)
    }
    var showVolumeIndicator by remember { mutableStateOf(false) }

    // Capture the window's brightness BEFORE we touch it, so we can restore on exit.
    val priorWindowBrightness = remember {
        activity?.window?.attributes?.screenBrightness ?: -1f  // -1 = follow system
    }
    val startBrightness = remember {
        initialBrightness.takeIf { it in 0f..1f }
            ?: priorWindowBrightness.takeIf { it in 0f..1f }
            ?: 0.5f
    }
    var currentBrightness by remember { mutableFloatStateOf(startBrightness) }
    var showBrightnessIndicator by remember { mutableStateOf(false) }

    // ── VHS: configure + animate (no-op when disabled) ─────────────────────
    vhs.configure(vhsEnabled, vhsIntensity)
    vhs.DriveAnimation()

    // ── Auto-hide: a single timer reset by any interaction. Stays 5s minimum, and never
    //    hides while paused, scrubbing, holding, panel open, or locked.
    //
    //    isSeekingFast/isHolding must be keys here, not just checked after delay() —
    //    otherwise a scrub or fast-forward hold that starts and finishes inside the 5s
    //    window doesn't restart the timer, and controls can vanish mid-gesture right as
    //    the user reaches for a button. Keying on them cancels + relaunches this effect
    //    the instant either becomes true, and again once it goes back to false. ──────
    var lastInteraction by remember { mutableLongStateOf(0L) }
    LaunchedEffect(
        lastInteraction, ui.controlsVisible, isPlaying, showControlPanel, ui.isLocked,
        isSeekingFast, isHolding
    ) {
        if (isSeekingFast || isHolding) return@LaunchedEffect
        if (ui.controlsVisible && isPlaying && !showControlPanel && !ui.isLocked) {
            kotlinx.coroutines.delay(5_000)
            // Re-check we're still idle before hiding (belt-and-suspenders: the key
            // restart above should already cover this, but keep the guard).
            if (!isSeekingFast && !isHolding) ui.hideControls()
        }
    }
    LaunchedEffect(showVolumeIndicator) { if (showVolumeIndicator) { kotlinx.coroutines.delay(1_500); showVolumeIndicator = false } }
    LaunchedEffect(showBrightnessIndicator) { if (showBrightnessIndicator) { kotlinx.coroutines.delay(1_500); showBrightnessIndicator = false } }
    LaunchedEffect(screenshotMessage) { if (screenshotMessage != null) { kotlinx.coroutines.delay(2_500); screenshotMessage = null } }

    // Restore brightness on launch (window-scoped, reverts on exit).
    LaunchedEffect(Unit) {
        if (startBrightness in 0f..1f) activity?.window?.let { win ->
            val a = win.attributes; a.screenBrightness = startBrightness; win.attributes = a
        }
    }
    LaunchedEffect(currentOrientation) {
        activity?.requestedOrientation = when (currentOrientation) {
            ScreenOrientation.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            ScreenOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            ScreenOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    // FF/FR hold gesture (CORE — independent of VHS). Notifies vhs.setRewind for the effect.
    LaunchedEffect(isPointerDown) {
        if (isPointerDown) {
            kotlinx.coroutines.delay(500L)
            if (isPointerDown) {
                isHolding = true
                while (isPointerDown) {
                    if (holdIsLeft) {
                        vhs.setRewind(active = true, forward = false, speed = holdSpeed)
                        val stepMs = (holdSpeed * 1_000L).toLong()
                        viewModel.onIntent(UserIntent.Seek((position - stepMs).coerceAtLeast(0L)))
                        kotlinx.coroutines.delay((220L / holdSpeed).toLong().coerceAtLeast(40L))
                    } else {
                        vhs.setRewind(active = true, forward = true, speed = holdSpeed)
                        viewModel.onIntent(UserIntent.SetSpeed(holdSpeed))
                        kotlinx.coroutines.delay(80L)
                    }
                }
            }
        } else if (isHolding) {
            isHolding = false
            holdSpeed = 2f
            vhs.setRewind(active = false, forward = false, speed = 0f)
            viewModel.onIntent(UserIntent.SetSpeed(1f))
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Don't pause if the user chose background play or PiP — that's the whole point.
            if (!isBackgroundEnabled && !isPiPEnabled) viewModel.onIntent(UserIntent.Pause)
            viewModel.onIntent(UserIntent.SetSpeed(1f))
            // Revert the window brightness to whatever it was before the player opened.
            activity?.window?.let { win ->
                val a = win.attributes
                a.screenBrightness = priorWindowBrightness  // -1 = follow system, or the prior value
                win.attributes = a
            }
        }
    }
    BackHandler(enabled = true) {
        when {
            ui.isLocked -> { /* locked: Back does nothing — must use the slide-unlock */ }
            ui.sheetOpen || showControlPanel -> { showControlPanel = false; ui.closeSheet() }
            else -> onBack()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(PlayerColors.background)) {

        // ── Layer 1: Video surface (+ VHS render effect, no pause dim) ──────
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val surfaceMod = when (currentRatio) {
                VideoRatio.FILL, VideoRatio.ORIGINAL -> Modifier.fillMaxSize()
                else -> currentRatio.ratio?.let { Modifier.fillMaxWidth().aspectRatio(it) } ?: Modifier.fillMaxSize()
            }
            surface(
                surfaceMod
                    .onSizeChanged { vhs.onSurfaceSize(it.width.toFloat(), it.height.toFloat()) }
                    .graphicsLayer {
                        scaleX = scale; scaleY = scale
                        translationX = panOffset.x; translationY = panOffset.y
                        renderEffect = vhs.effectOrNull()?.asComposeRenderEffect()
                    }
            )
        }

        // ── Subtitles ───────────────────────────────────────────────────────
        val activeCue = remember(subtitleTrack, position) { subtitleTrack?.cueAt(position) }
        val subAtTop = subtitleStyle.position == com.watermelon.common.model.SubtitlePosition.TOP
        SubtitleOverlay(
            text = activeCue?.displayText,
            isRtl = activeCue?.baseRtl ?: false,
            style = subtitleStyle,
            modifier = Modifier
                .align(if (subAtTop) Alignment.TopCenter else Alignment.BottomCenter)
                .padding(
                    top = if (subAtTop) (if (ui.controlsVisible) 96.dp else 32.dp) else 0.dp,
                    bottom = if (!subAtTop) (if (ui.controlsVisible) 80.dp else 24.dp) else 0.dp
                )
        )

        // ── Layer 2: Gesture surface (gated by ui.gesturesEnabled) ──────────
        Box(
            Modifier.fillMaxSize()
                .pointerInput(ui.gesturesEnabled, showControlPanel) {
                    if (!ui.gesturesEnabled || showControlPanel) return@pointerInput
                    detectTapGestures(
                        onTap = { lastInteraction = System.nanoTime(); ui.toggleControls(); if (!ui.controlsVisible) showControlPanel = false },
                        onDoubleTap = {
                            viewModel.onIntent(if (isPlaying) UserIntent.Pause else UserIntent.Resume)
                            run { lastInteraction = System.nanoTime(); ui.showControls() }
                        }
                    )
                }
                .pointerInput(durationMs, ui.gesturesEnabled, showControlPanel) {
                    if (!ui.gesturesEnabled || showControlPanel) return@pointerInput
                    awaitEachGesture {
                        val firstDown = awaitFirstDown()  // requireUnconsumed=true: ignore touches consumed by controls
                        val holdOriginX = firstDown.position.x
                        holdIsLeft = firstDown.position.x < size.width / 2f
                        isPointerDown = true
                        var isHorizontal: Boolean? = null
                        var isMultiTouch = false
                        seekFrac = if (durationMs > 0) position.toFloat() / durationMs else 0f

                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            val pointerCount = pressed.size

                            if (isHolding && pointerCount == 1) {
                                val dx = abs(pressed.first().position.x - holdOriginX)
                                val frac = (dx / size.width).coerceIn(0f, 1f)
                                holdSpeed = when {
                                    frac < 0.12f -> 2f
                                    frac < 0.28f -> 3f
                                    frac < 0.5f -> 4f
                                    else -> 8f
                                }
                            }

                            if (pointerCount >= 2) {
                                isMultiTouch = true
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                if (zoom != 1f) scale = (scale * zoom).coerceIn(1f, 4f)
                                panOffset = if (scale > 1f) Offset(panOffset.x + pan.x, panOffset.y + pan.y) else Offset.Zero
                                event.changes.forEach { it.consume() }
                            } else if (pointerCount == 1 && !isMultiTouch) {
                                val change = pressed.first()
                                val drag = change.positionChange()
                                if (isHorizontal == null && (abs(drag.x) > 10f || abs(drag.y) > 10f)) {
                                    isHorizontal = abs(drag.x) > abs(drag.y)
                                }
                                when (isHorizontal) {
                                    true -> {
                                        seekFrac = (seekFrac + drag.x / size.width.toFloat() * 0.3f).coerceIn(0f, 1f)
                                        viewModel.onIntent(UserIntent.Seek((seekFrac * durationMs).toLong()))
                                        change.consume()
                                    }
                                    false -> {
                                        if (change.position.x > size.width / 2f) {
                                            volumeFraction = (volumeFraction - drag.y / size.height * 1.5f).coerceIn(0f, 1f)
                                            val newVol = (volumeFraction * maxVolume).toInt().coerceIn(0, maxVolume)
                                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                            currentVolume = newVol; showVolumeIndicator = true
                                        } else {
                                            val newBright = (currentBrightness - drag.y / size.height).coerceIn(0.01f, 1f)
                                            currentBrightness = newBright
                                            activity?.window?.let { win ->
                                                val a = win.attributes; a.screenBrightness = newBright; win.attributes = a
                                            }
                                            onBrightnessChange?.invoke(newBright)
                                            showBrightnessIndicator = true
                                        }
                                        change.consume()
                                    }
                                    null -> {}
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        isPointerDown = false
                        run { lastInteraction = System.nanoTime(); ui.showControls() }
                    }
                }
        )

        // ── Layer 3: Controls (top/bottom scrim only; NO full-screen pause dim) ──
        if (ui.controlsVisible) {
            // Top scrim gradient behind the top bar
            Box(
                Modifier.fillMaxWidth().height(96.dp).align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(PlayerColors.controlBarScrim.copy(alpha = 0.6f), Color.Transparent)))
            )
            // Bottom scrim gradient behind the bottom bar
            Box(
                Modifier.fillMaxWidth().height(140.dp).align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, PlayerColors.controlBarScrim.copy(alpha = 0.7f))))
            )

            // Top bar: back · spacer · lock · more
            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopStart).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (showControlPanel) showControlPanel = false else onBack()
                }) {
                    Icon(painterResource(R.drawable.ic_arrow_back), "Back", tint = PlayerColors.iconDefault)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { ui.lock(); onLockChanged?.invoke(true) }) {
                    Icon(painterResource(R.drawable.ic_lock), "Lock", tint = PlayerColors.iconDefault)
                }
                IconButton(onClick = { showControlPanel = !showControlPanel }) {
                    Icon(painterResource(R.drawable.ic_more_horizontal), "Menu", tint = if (showControlPanel) PlayerColors.iconActive else PlayerColors.iconDefault)
                }
            }

            // Center cluster: prev-track · skip-back · play/pause · skip-forward · next-track
            val hasNextTrack = remember(uri) { PlaybackQueue.nextOf(uri) != null }
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Previous track: if >3s in, restart current; else go to previous file.
                IconButton(onClick = {
                    if (position > 3_000L) {
                        viewModel.onIntent(UserIntent.Seek(0L))
                    } else {
                        val prev = PlaybackQueue.previousOf(uri)
                        if (prev != null) onSkipToTrack?.invoke(prev)
                        else viewModel.onIntent(UserIntent.Seek(0L))
                    }
                    run { lastInteraction = System.nanoTime(); ui.showControls() }
                }) {
                    Icon(painterResource(R.drawable.ic_skip_previous), "Previous track",
                        tint = PlayerColors.iconDefault, modifier = Modifier.width(30.dp).height(30.dp))
                }
                IconButton(onClick = {
                    viewModel.onIntent(UserIntent.Seek((position - 10_000L).coerceAtLeast(0L)))
                    run { lastInteraction = System.nanoTime(); ui.showControls() }
                }) {
                    Icon(painterResource(R.drawable.ic_rewind), "Skip back 10s",
                        tint = PlayerColors.iconDefault, modifier = Modifier.width(34.dp).height(34.dp))
                }
                IconButton(
                    onClick = { viewModel.onIntent(if (isPlaying) UserIntent.Pause else UserIntent.Resume) }
                ) {
                    Icon(
                        painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                        if (isPlaying) "Pause" else "Play",
                        tint = PlayerColors.iconDefault,
                        modifier = Modifier.width(52.dp).height(52.dp)
                    )
                }
                IconButton(onClick = {
                    viewModel.onIntent(UserIntent.Seek((position + 10_000L).coerceAtMost(durationMs)))
                    run { lastInteraction = System.nanoTime(); ui.showControls() }
                }) {
                    Icon(painterResource(R.drawable.ic_fast_forward), "Skip forward 10s",
                        tint = PlayerColors.iconDefault, modifier = Modifier.width(34.dp).height(34.dp))
                }
                // Next track: INVISIBLE when there is no adjacent next file.
                if (hasNextTrack) {
                    IconButton(onClick = {
                        PlaybackQueue.nextOf(uri)?.let { onSkipToTrack?.invoke(it) }
                        run { lastInteraction = System.nanoTime(); ui.showControls() }
                    }) {
                        Icon(painterResource(R.drawable.ic_skip_next), "Next track",
                            tint = PlayerColors.iconDefault, modifier = Modifier.width(30.dp).height(30.dp))
                    }
                }
            }

            // Bottom bar: time + custom seekbar
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(Modifier.fillMaxWidth()) {
                    Text(formatTime(position), color = PlayerColors.textPrimary)
                    Spacer(Modifier.weight(1f))
                    Text(formatTime(durationMs), color = PlayerColors.textPrimary)
                }
                WatermelonSeekBar(
                    positionMs = position,
                    durationMs = durationMs,
                    onSeek = { viewModel.onIntent(UserIntent.Seek(it)) },
                    onScrubChange = { run { lastInteraction = System.nanoTime(); ui.showControls() } },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Control panel
            if (showControlPanel) {
                ControlPanel(
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 48.dp),
                    currentSpeed = playbackSpeed,
                    isMuted = currentVolume == 0,
                    currentRatio = currentRatio,
                    repeatMode = repeatMode,
                    isShuffled = isShuffled,
                    isPiP = isPiPEnabled,
                    isBackground = isBackgroundEnabled,
                    isFavourite = isFavourite,
                    onSpeedChange = { s -> playbackSpeed = s; viewModel.onIntent(UserIntent.SetSpeed(s)) },
                    onMuteToggle = {
                        val muted = currentVolume == 0
                        val vol = if (muted) (maxVolume / 2).coerceAtLeast(1) else 0
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0)
                        currentVolume = vol; volumeFraction = vol.toFloat() / maxVolume
                    },
                    onRatioChange = { currentRatio = it },
                    currentOrientation = currentOrientation,
                    onOrientationChange = { currentOrientation = it },
                    onRepeat = { viewModel.cycleRepeat() },
                    onShuffle = { viewModel.toggleShuffle() },
                    onScreenshot = {
                        scope.launch {
                            val mode = when (screenshotMode) {
                                ScreenshotMode.BURST -> ScreenshotManager.Mode.BURST
                                ScreenshotMode.SINGLE -> ScreenshotManager.Mode.SINGLE
                            }
                            val result = ScreenshotManager.takeScreenshot(context, uri, position, durationMs, mode)
                            screenshotMessage = when (result) {
                                is ScreenshotResult.Success -> "Saved ${result.uris.size} screenshot(s)"
                                is ScreenshotResult.Error -> "Screenshot failed"
                            }
                        }
                    },
                    onSleepTimer = { showSleepTimerDialog = true },
                    onPip = { isPiPEnabled = true; isBackgroundEnabled = false; ui.hideControls(); onPipClick?.invoke() },
                    onBackground = {
                        if (!isBackgroundEnabled) { isBackgroundEnabled = true; isPiPEnabled = false; onBackgroundClick?.invoke(true) }
                        else { isBackgroundEnabled = false; onBackgroundClick?.invoke(false) }
                    },
                    onShare = { onShare?.invoke() },
                    onFavourite = { onFavourite?.invoke(!isFavourite) },
                    onAddToPlaylist = { onAddToPlaylist?.invoke() },
                    onDelete = { onDelete?.invoke() }
                )
            }
        }

        // ── Layer 4: Transient indicators ───────────────────────────────────
        if (isHolding) {
            Row(
                modifier = Modifier.align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (holdIsLeft) Icon(painterResource(R.drawable.ic_rewind), null, tint = PlayerColors.iconDefault, modifier = Modifier.width(24.dp).height(24.dp))
                Text("${holdSpeed.toInt()}×", color = PlayerColors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (!holdIsLeft) Icon(painterResource(R.drawable.ic_fast_forward), null, tint = PlayerColors.iconDefault, modifier = Modifier.width(24.dp).height(24.dp))
            }
        }
        if (showVolumeIndicator) {
            LevelIndicator(
                fraction = volumeFraction,
                icon = if (currentVolume == 0) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "Volume",
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 24.dp)
            )
        }
        if (showBrightnessIndicator) {
            LevelIndicator(
                fraction = currentBrightness,
                icon = Icons.Filled.BrightnessHigh,
                contentDescription = "Brightness",
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp)
            )
        }
        screenshotMessage?.let { msg ->
            Box(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp)
                    .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) { Text(msg, color = PlayerColors.textPrimary) }
        }

        // ── TOPMOST: lock overlay above everything, blocks all touch ────────
        if (ui.isLocked) {
            com.watermelon.ui.components.LockOverlay(
                onUnlock = { ui.unlock(); onLockChanged?.invoke(false) },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    // ── Layer 5: Sleep timer dialog ─────────────────────────────────────────
    if (showSleepTimerDialog) {
        SleepTimerDialog(
            onDismiss = { showSleepTimerDialog = false },
            onSetTimer = { mode, minutes ->
                val sleepMode = when (mode) {
                    "current_video" -> SleepTimerMode.EndOfVideo
                    "folder" -> SleepTimerMode.EndOfFolder
                    "custom" -> SleepTimerMode.Custom(minutes ?: 15)
                    else -> SleepTimerMode.Custom(15)
                }
                viewModel.setSleepTimer(sleepMode)
                showSleepTimerDialog = false
            }
        )
    }
}

private fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60)
}