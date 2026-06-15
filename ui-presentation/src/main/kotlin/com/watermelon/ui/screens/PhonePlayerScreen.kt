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
    screenshotMode: ScreenshotMode = ScreenshotMode.SINGLE,
    initialBrightness: Float = -1f,
    onPipClick: (() -> Unit)? = null,
    onBackgroundClick: ((Boolean) -> Unit)? = null,
    onBrightnessChange: ((Float) -> Unit)? = null,
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

    val startBrightness = remember {
        initialBrightness.takeIf { it in 0f..1f }
            ?: activity?.window?.attributes?.screenBrightness?.takeIf { it in 0f..1f }
            ?: 0.5f
    }
    var currentBrightness by remember { mutableFloatStateOf(startBrightness) }
    var showBrightnessIndicator by remember { mutableStateOf(false) }

    // ── VHS: configure + animate (no-op when disabled) ─────────────────────
    vhs.configure(vhsEnabled, vhsIntensity)
    vhs.DriveAnimation()

    // ── Auto-hide: suspended while a sheet/panel is open or locked ──────────
    LaunchedEffect(ui.controlsVisible, isPlaying, isSeekingFast, isHolding, showControlPanel, ui.isLocked) {
        if (ui.controlsVisible && isPlaying && !isSeekingFast && !isHolding && !showControlPanel && !ui.isLocked) {
            kotlinx.coroutines.delay(3_000)
            ui.hideControls()
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
            viewModel.onIntent(UserIntent.Pause)
            viewModel.onIntent(UserIntent.SetSpeed(1f))
        }
    }
    BackHandler { if (ui.sheetOpen || showControlPanel) { showControlPanel = false; ui.closeSheet() } else onBack() }

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
        SubtitleOverlay(
            text = activeCue?.displayText,
            isRtl = activeCue?.baseRtl ?: false,
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(bottom = if (ui.controlsVisible) 80.dp else 24.dp)
        )

        // ── Layer 2: Gesture surface (gated by ui.gesturesEnabled) ──────────
        Box(
            Modifier.fillMaxSize()
                .pointerInput(ui.gesturesEnabled) {
                    if (!ui.gesturesEnabled) return@pointerInput
                    detectTapGestures(
                        onTap = { ui.toggleControls(); if (!ui.controlsVisible) showControlPanel = false },
                        onDoubleTap = {
                            viewModel.onIntent(if (isPlaying) UserIntent.Pause else UserIntent.Resume)
                            ui.showControls()
                        }
                    )
                }
                .pointerInput(durationMs, ui.gesturesEnabled) {
                    if (!ui.gesturesEnabled) return@pointerInput
                    awaitEachGesture {
                        val firstDown = awaitFirstDown(requireUnconsumed = false)
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
                        ui.showControls()
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
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), "Back", tint = PlayerColors.iconDefault)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { ui.lock() }) {
                    Icon(painterResource(R.drawable.ic_orientation_auto), "Lock", tint = PlayerColors.iconDefault)
                }
                IconButton(onClick = { showControlPanel = !showControlPanel }) {
                    Icon(painterResource(R.drawable.ic_more_horizontal), "Menu", tint = if (showControlPanel) PlayerColors.iconActive else PlayerColors.iconDefault)
                }
            }

            // Center play/pause
            IconButton(
                onClick = { viewModel.onIntent(if (isPlaying) UserIntent.Pause else UserIntent.Resume) },
                modifier = Modifier.align(Alignment.Center)
            ) {
                Icon(
                    painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                    if (isPlaying) "Pause" else "Play",
                    tint = PlayerColors.iconDefault,
                    modifier = Modifier.width(48.dp).height(48.dp)
                )
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
                    durationMs = du
