package com.watermelon.ui.screens

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.os.Build
import android.util.Rational
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watermelon.common.model.PlaybackState
import com.watermelon.common.model.RepeatMode
import com.watermelon.common.model.SleepTimerMode
import com.watermelon.common.model.UserIntent
import com.watermelon.common.model.VhsTier
import com.watermelon.ui.components.SleepTimerDialog
import com.watermelon.ui.components.SubtitleOverlay
import com.watermelon.ui.components.VhsSeekBar
import com.watermelon.ui.components.WatermelonLoadingAnimation
import com.watermelon.ui.utils.ScreenshotManager
import com.watermelon.ui.utils.ScreenshotResult
import com.watermelon.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

// ─── Enums ───────────────────────────────────────────────────────────────────

enum class VideoRatio(val label: String, val ratio: Float?) {
    FILL("Fill", null), ORIGINAL("Original", null),
    RATIO_16_9("16:9", 16f / 9f), RATIO_4_3("4:3", 4f / 3f), RATIO_21_9("21:9", 21f / 9f)
}

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

enum class ScreenOrientation(val label: String) {
    AUTO("↻ Auto"), PORTRAIT("↑ Port"), LANDSCAPE("↔ Land")
}

// ─── Player screen ────────────────────────────────────────────────────────────

/**
 * Full-screen video player with complete gesture + control panel support.
 *
 * Gestures:
 *   Tap               → toggle controls visibility
 *   Double-tap        → play / pause
 *   Hold left half    → continuous rewind (5 s / 300 ms) + VHS
 *   Hold right half   → 2× fast-forward + VHS
 *   Horizontal drag   → seek (0.3× relative sensitivity)
 *   Right-half drag ↕ → system volume (float accumulator, 1.5× gain)
 *   Left-half drag ↕  → screen brightness
 *   Pinch             → zoom (1–4×) + pan
 *
 * Control panel stubs now wired:
 *   🔁 Repeat       → cycles NONE → ONE → ALL
 *   ⇌  Shuffle      → toggles ExoPlayer shuffle
 *   📷 Screenshot   → single or burst via [ScreenshotManager]
 *   ⏱  Sleep timer  → [SleepTimerDialog] with 3 modes
 *   ⊡  PiP          → system picture-in-picture (mutually exclusive with background)
 *   ⬛ Background   → starts [WatermelonPlaybackService] (mutually exclusive with PiP)
 *   +▶ Playlist     → stub (playlist screen not yet built)
 */
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    vhsTier: VhsTier,
    vhsIntensity: Float,
    durationMs: Long,
    currentSubtitle: String?,
    surface: @Composable (Modifier) -> Unit,
    onBack: () -> Unit,
    uri: String = "",
    screenshotMode: ScreenshotMode = ScreenshotMode.SINGLE,
    onPipClick: (() -> Unit)? = null,
    onBackgroundClick: ((Boolean) -> Unit)? = null,
    vhsRenderEffectProvider: () -> RenderEffect? = { null },
    modifier: Modifier = Modifier
) {
    val context      = LocalContext.current
    val activity     = context as? Activity
    val audioManager = remember { context.getSystemService(AudioManager::class.java) }
    val maxVolume    = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    val coroutineScope = rememberCoroutineScope()

    val position      by viewModel.currentPositionMs.collectAsStateWithLifecycle()
    val isSeekingFast by viewModel.isSeekingFast.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val repeatMode    by viewModel.repeatMode.collectAsStateWithLifecycle()
    val isShuffled    by viewModel.shuffleEnabled.collectAsStateWithLifecycle()
    val isPlaying = playbackState == PlaybackState.PLAYING

    var controlsVisible     by remember { mutableStateOf(true) }
    var showControlPanel    by remember { mutableStateOf(false) }
    var playbackSpeed       by remember { mutableFloatStateOf(1f) }
    var currentRatio        by remember { mutableStateOf(VideoRatio.FILL) }
    var scale               by remember { mutableFloatStateOf(1f) }
    var panOffset           by remember { mutableStateOf(Offset.Zero) }
    var isHolding           by remember { mutableStateOf(false) }
    var isPointerDown       by remember { mutableStateOf(false) }
    var holdIsLeft          by remember { mutableStateOf(false) }
    var currentVolume       by remember { mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    var volumeFraction      by remember {
        mutableFloatStateOf(
            if (maxVolume > 0) audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume else 0f
        )
    }
    var showVolumeIndicator     by remember { mutableStateOf(false) }
    var seekFrac                by remember { mutableFloatStateOf(0f) }
    var currentOrientation      by rememberSaveable { mutableStateOf(ScreenOrientation.AUTO) }
    var showSleepTimerDialog    by remember { mutableStateOf(false) }
    var screenshotMessage       by remember { mutableStateOf<String?>(null) }
    var isPiPEnabled            by remember { mutableStateOf(false) }
    var isBackgroundEnabled     by remember { mutableStateOf(false) }

    val initialBrightness = remember {
        activity?.window?.attributes?.screenBrightness?.takeIf { it in 0f..1f } ?: 0.5f
    }
    var currentBrightness       by remember { mutableFloatStateOf(initialBrightness) }
    var showBrightnessIndicator by remember { mutableStateOf(false) }

    // Auto-hide controls.
    LaunchedEffect(controlsVisible, isPlaying, isSeekingFast, isHolding) {
        if (controlsVisible && isPlaying && !isSeekingFast && !isHolding) {
            delay(3_000); controlsVisible = false
        }
    }
    LaunchedEffect(showVolumeIndicator)     { if (showVolumeIndicator) { delay(1_500); showVolumeIndicator = false } }
    LaunchedEffect(showBrightnessIndicator) { if (showBrightnessIndicator) { delay(1_500); showBrightnessIndicator = false } }
    LaunchedEffect(screenshotMessage)       { if (screenshotMessage != null) { delay(2_500); screenshotMessage = null } }

    // Orientation lock.
    LaunchedEffect(currentOrientation) {
        activity?.requestedOrientation = when (currentOrientation) {
            ScreenOrientation.AUTO      -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            ScreenOrientation.PORTRAIT  -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            ScreenOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    // Hold gesture.
    LaunchedEffect(isPointerDown) {
        if (isPointerDown) {
            delay(500L)
            if (isPointerDown) {
                isHolding = true
                if (holdIsLeft) {
                    while (isPointerDown) {
                        viewModel.onIntent(UserIntent.Seek((position - 5_000L).coerceAtLeast(0L)))
                        delay(300L)
                    }
                } else {
                    viewModel.onIntent(UserIntent.SetSpeed(2f))
                }
            }
        } else {
            if (isHolding) { isHolding = false; viewModel.onIntent(UserIntent.SetSpeed(1f)) }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.onIntent(UserIntent.Pause)
            viewModel.onIntent(UserIntent.SetSpeed(1f))
        }
    }

    BackHandler { onBack() }

    val renderEffect = remember(vhsTier, vhsIntensity, isSeekingFast, isHolding) {
        val active = (isSeekingFast || isHolding) &&
            vhsTier != VhsTier.C &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        if (active) vhsRenderEffectProvider() else null
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {

        // ── Video surface ─────────────────────────────────────────────────────
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val surfaceMod = when (currentRatio) {
                VideoRatio.FILL, VideoRatio.ORIGINAL -> Modifier.fillMaxSize()
                else -> currentRatio.ratio?.let { Modifier.fillMaxWidth().aspectRatio(it) }
                    ?: Modifier.fillMaxSize()
            }
            surface(surfaceMod.graphicsLayer {
                scaleX = scale; scaleY = scale
                translationX = panOffset.x; translationY = panOffset.y
                this.renderEffect = renderEffect
            })
        }

        // ── Subtitles ─────────────────────────────────────────────────────────
        SubtitleOverlay(
            currentText = currentSubtitle,
            modifier    = Modifier.align(Alignment.BottomCenter)
                .padding(bottom = if (controlsVisible) 80.dp else 24.dp)
        )

        // ── Gesture layer ─────────────────────────────────────────────────────
        Box(
            Modifier.fillMaxSize()
            .pointerInput(isPlaying) {
                detectTapGestures(
                    onTap = { controlsVisible = !controlsVisible; if (!controlsVisible) showControlPanel = false },
                    onDoubleTap = {
                        viewModel.onIntent(if (isPlaying) UserIntent.Pause else UserIntent.Resume)
                        controlsVisible = true
                    }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    holdIsLeft = down.position.x < size.width / 2f
                    isPointerDown = true
                    waitForUpOrCancellation()
                    isPointerDown = false
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (zoom != 1f) scale = (scale * zoom).coerceIn(1f, 4f)
                    if (scale > 1f) panOffset = Offset(panOffset.x + pan.x, panOffset.y + pan.y)
                    else panOffset = Offset.Zero
                }
            }
            .pointerInput(durationMs) {
                var isHorizontal: Boolean? = null
                detectDragGestures(
                    onDragStart = { _ ->
                        isHorizontal = null
                        seekFrac = if (durationMs > 0) position.toFloat() / durationMs else 0f
                    },
                    onDragEnd    = { isHorizontal = null; controlsVisible = true },
                    onDragCancel = { isHorizontal = null }
                ) { change, dragAmount ->
                    if (isHorizontal == null &&
                        (abs(dragAmount.x) > 10f || abs(dragAmount.y) > 10f)) {
                        isHorizontal = abs(dragAmount.x) > abs(dragAmount.y)
                    }
                    when (isHorizontal) {
                        true -> {
                            seekFrac = (seekFrac + dragAmount.x / size.width.toFloat() * 0.3f).coerceIn(0f, 1f)
                            viewModel.onIntent(UserIntent.Seek((seekFrac * durationMs).toLong()))
                        }
                        false -> {
                            if (change.position.x > size.width / 2f) {
                                volumeFraction = (volumeFraction - dragAmount.y / size.height * 1.5f).coerceIn(0f, 1f)
                                val newVol = (volumeFraction * maxVolume).toInt().coerceIn(0, maxVolume)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                currentVolume = newVol; showVolumeIndicator = true
                            } else {
                                val newBright = (currentBrightness - dragAmount.y / size.height).coerceIn(0.01f, 1f)
                                currentBrightness = newBright
                                activity?.window?.let { win ->
                                    val attrs = win.attributes; attrs.screenBrightness = newBright; win.attributes = attrs
                                }
                                showBrightnessIndicator = true
                            }
                        }
                        null -> {}
                    }
                }
            }
        )

        // ── Controls overlay ──────────────────────────────────────────────────
        if (controlsVisible) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))) {
                Row(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopStart)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onBack) { Text("← Back", color = Color.White) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { showControlPanel = !showControlPanel }) {
                        Text("⋯", color = Color.White, fontSize = 22.sp)
                    }
                }

                if (showControlPanel) {
                    ControlPanel(
                        modifier           = Modifier.align(Alignment.TopEnd).padding(top = 48.dp),
                        currentSpeed       = playbackSpeed,
                        isMuted            = currentVolume == 0,
                        currentRatio       = currentRatio,
                        repeatMode         = repeatMode,
                        isShuffled         = isShuffled,
                        isPiP              = isPiPEnabled,
                        isBackground       = isBackgroundEnabled,
                        onSpeedChange      = { speed -> playbackSpeed = speed; viewModel.onIntent(UserIntent.SetSpeed(speed)) },
                        onMuteToggle = {
                            val muted = currentVolume == 0
                            val vol = if (muted) (maxVolume / 2).coerceAtLeast(1) else 0
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0)
                            currentVolume = vol; volumeFraction = vol.toFloat() / maxVolume
                        },
                        onRatioChange       = { currentRatio = it },
                        currentOrientation  = currentOrientation,
                        onOrientationChange = { currentOrientation = it },
                        onRepeat    = { viewModel.cycleRepeat() },
                        onShuffle   = { viewModel.toggleShuffle() },
                        onScreenshot = {
                            coroutineScope.launch {
                                val mode = when (screenshotMode) {
                                    ScreenshotMode.BURST  -> ScreenshotManager.Mode.BURST
                                    ScreenshotMode.SINGLE -> ScreenshotManager.Mode.SINGLE
                                }
                                val result = ScreenshotManager.takeScreenshot(
                                    context           = context,
                                    videoUri          = uri,
                                    currentPositionMs = position,
                                    durationMs        = durationMs,
                                    mode              = mode
                                )
                                screenshotMessage = when (result) {
                                    is ScreenshotResult.Success -> "📷 Saved ${result.files.size} screenshot(s)"
                                    is ScreenshotResult.Error   -> "Screenshot failed"
                                }
                            }
                        },
                        onSleepTimer = { showSleepTimerDialog = true },
                        onPip = {
                            isPiPEnabled    = true
                            isBackgroundEnabled = false
                            controlsVisible = false
                            onPipClick?.invoke()
                        },
                        onBackground = {
                            if (!isBackgroundEnabled) {
                                isBackgroundEnabled = true
                                isPiPEnabled        = false
                                onBackgroundClick?.invoke(true)
                            } else {
                                isBackgroundEnabled = false
                                onBackgroundClick?.invoke(false)
                            }
                        },
                        onPlaylist = { /* stub: playlist screen not yet built */ }
                    )
                }

                TextButton(
                    onClick  = { viewModel.onIntent(if (isPlaying) UserIntent.Pause else UserIntent.Resume) },
                    modifier = Modifier.align(Alignment.Center)
                ) { Text(if (isPlaying) "⏸" else "▶", fontSize = 44.sp, color = Color.White) }

                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(formatTime(position), color = Color.White)
                        Spacer(Modifier.weight(1f))
                        Text(formatTime(durationMs), color = Color.White)
                    }
                    VhsSeekBar(
                        positionMs          = position,
                        durationMs          = durationMs,
                        onSeek              = { viewModel.onIntent(UserIntent.Seek(it)) },
                        onSeekingFastChange = { fast ->
                            viewModel.onIntent(UserIntent.SetVhsIntensity(if (fast) vhsIntensity else 0f))
                            controlsVisible = true
                        },
                        onSeekSpeedChange = {},
                        modifier          = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // ── Hold indicator ────────────────────────────────────────────────────
        if (isHolding) {
            Box(
                modifier = Modifier.align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) { Text(if (holdIsLeft) "⏪ 2×" else "2× ⏩", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        }

        // ── Volume indicator ──────────────────────────────────────────────────
        if (showVolumeIndicator) {
            SideIndicator(
                fraction = volumeFraction,
                topLabel = if (currentVolume == 0) "🔇" else "🔊",
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 24.dp)
            )
        }

        // ── Brightness indicator ──────────────────────────────────────────────
        if (showBrightnessIndicator) {
            SideIndicator(
                fraction = currentBrightness,
                topLabel = "☀️",
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp)
            )
        }

        // ── Screenshot toast ──────────────────────────────────────────────────
        screenshotMessage?.let { msg ->
            Box(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp)
                    .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) { Text(msg, color = Color.White) }
        }
    }

    // ── Sleep timer dialog ────────────────────────────────────────────────────
    if (showSleepTimerDialog) {
        SleepTimerDialog(
            onDismiss = { showSleepTimerDialog = false },
            onSetTimer = { mode, minutes ->
                val sleepMode = when (mode) {
                    "current_video" -> SleepTimerMode.EndOfVideo
                    "folder"        -> SleepTimerMode.EndOfFolder
                    "custom"        -> SleepTimerMode.Custom(minutes ?: 15)
                    else            -> SleepTimerMode.Custom(15)
                }
                viewModel.setSleepTimer(sleepMode)
                showSleepTimerDialog = false
            }
        )
    }
}

// ─── Control panel ────────────────────────────────────────────────────────────

@Composable
private fun ControlPanel(
    modifier: Modifier,
    currentSpeed: Float,
    isMuted: Boolean,
    currentRatio: VideoRatio,
    repeatMode: RepeatMode,
    isShuffled: Boolean,
    isPiP: Boolean,
    isBackground: Boolean,
    onSpeedChange: (Float) -> Unit,
    onMuteToggle: () -> Unit,
    onRatioChange: (VideoRatio) -> Unit,
    currentOrientation: ScreenOrientation,
    onOrientationChange: (ScreenOrientation) -> Unit,
    onRepeat: () -> Unit, onShuffle: () -> Unit, onScreenshot: () -> Unit,
    onSleepTimer: () -> Unit, onPip: () -> Unit, onBackground: () -> Unit, onPlaylist: () -> Unit
) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.88f), RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        PanelLabel("Speed")
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            SPEEDS.forEach { speed ->
                val active = speed == currentSpeed
                TextButton(onClick = { onSpeedChange(speed) }, modifier = Modifier.height(32.dp)) {
                    Text(formatSpeed(speed), color = if (active) MaterialTheme.colorScheme.primary else Color.White,
                        fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
        PanelLabel("Ratio")
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            VideoRatio.values().forEach { ratio ->
                val active = ratio == currentRatio
                TextButton(onClick = { onRatioChange(ratio) }, modifier = Modifier.height(32.dp)) {
                    Text(ratio.label, color = if (active) MaterialTheme.colorScheme.primary else Color.White,
                        fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
        PanelLabel("Rotate")
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            ScreenOrientation.values().forEach { orientation ->
                val active = orientation == currentOrientation
                TextButton(onClick = { onOrientationChange(orientation) }, modifier = Modifier.height(32.dp)) {
                    Text(orientation.label, color = if (active) MaterialTheme.colorScheme.primary else Color.White,
                        fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            // Mute
            Stub("🔇", isMuted, onMuteToggle)
            // Repeat — label shows current mode
            val repeatLabel = when (repeatMode) {
                RepeatMode.NONE -> "🔁"; RepeatMode.ONE -> "🔂"; RepeatMode.ALL -> "🔁✓"
            }
            Stub(repeatLabel, repeatMode != RepeatMode.NONE, onRepeat)
            // Shuffle
            Stub("⇌", isShuffled, onShuffle)
            // Screenshot
            Stub("📷", false, onScreenshot)
            // Sleep timer
            Stub("⏱", false, onSleepTimer)
            // PiP — mutually exclusive with background
            Stub("⊡", isPiP, onPip)
            // Background — mutually exclusive with PiP
            Stub("⬛", isBackground, onBackground)
            // Playlist — stub
            Stub("+▶", false, onPlaylist)
        }
    }
}

@Composable
private fun PanelLabel(text: String) {
    Text(text, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
}

@Composable
private fun Stub(label: String, active: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.height(40.dp)) {
        Text(label, color = if (active) MaterialTheme.colorScheme.primary else Color.White,
            fontSize = 16.sp, textAlign = TextAlign.Center)
    }
}

// ─── Side indicator ───────────────────────────────────────────────────────────

@Composable
private fun SideIndicator(fraction: Float, topLabel: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .width(36.dp).height(140.dp)
            .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(4.dp))
            .padding(6.dp),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(topLabel, fontSize = 12.sp, color = Color.White)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.width(8.dp).weight(1f).background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(fraction.coerceIn(0f, 1f))
                .background(Color.White, RoundedCornerShape(4.dp)).align(Alignment.BottomCenter))
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60)
}

private fun formatSpeed(speed: Float): String =
    if (speed == speed.toLong().toFloat()) "${speed.toLong()}×" else "${speed}×"
