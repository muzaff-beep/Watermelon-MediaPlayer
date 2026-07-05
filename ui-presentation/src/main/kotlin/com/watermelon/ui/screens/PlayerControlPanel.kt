package com.watermelon.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watermelon.common.model.RepeatMode
import com.watermelon.ui.R
import com.watermelon.ui.WatermelonIcons
import com.watermelon.ui.theme.PlayerColors

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

@Composable
fun ControlPanel(
    modifier: Modifier,
    currentSpeed: Float,
    isMuted: Boolean,
    currentRatio: VideoRatio,
    repeatMode: RepeatMode,
    isShuffled: Boolean,
    isPiP: Boolean,
    isBackground: Boolean,
    isFavourite: Boolean,
    onSpeedChange: (Float) -> Unit,
    onMuteToggle: () -> Unit,
    onRatioChange: (VideoRatio) -> Unit,
    currentOrientation: ScreenOrientation,
    onOrientationChange: (ScreenOrientation) -> Unit,
    onRepeat: () -> Unit, onShuffle: () -> Unit, onScreenshot: () -> Unit,
    onSleepTimer: () -> Unit, onPip: () -> Unit, onBackground: () -> Unit,
    onShare: () -> Unit, onFavourite: () -> Unit, onAddToPlaylist: () -> Unit, onDelete: () -> Unit
) {
    Column(
        modifier = modifier
            .background(PlayerColors.current.sheetBackground.copy(alpha = 0.95f), RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        PanelLabel("Speed")
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            SPEEDS.forEach { speed ->
                val active = speed == currentSpeed
                TextButton(onClick = { onSpeedChange(speed) }, modifier = Modifier.height(32.dp)) {
                    Text(formatSpeed(speed), color = if (active) PlayerColors.current.iconActive else PlayerColors.current.iconDefault,
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
                    Text(ratio.label, color = if (active) PlayerColors.current.iconActive else PlayerColors.current.iconDefault,
                        fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
        PanelLabel("Rotate")
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            ScreenOrientation.values().forEach { orientation ->
                val active = orientation == currentOrientation
                IconButton(onClick = { onOrientationChange(orientation) }) {
                    Icon(painterResource(orientation.iconRes), orientation.name,
                        tint = if (active) PlayerColors.current.iconActive else PlayerColors.current.iconDefault,
                        modifier = Modifier.width(20.dp).height(20.dp))
                }
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconStub(
                icon        = if (isMuted) WatermelonIcons.VolumeMute else WatermelonIcons.VolumeHigh,
                description = if (isMuted) "Unmute" else "Mute",
                active      = isMuted,
                onClick     = onMuteToggle
            )
            val repeatIcon = when (repeatMode) {
                RepeatMode.NONE -> WatermelonIcons.RepeatOff
                RepeatMode.ONE -> WatermelonIcons.RepeatOne
                RepeatMode.ALL -> WatermelonIcons.RepeatAll
            }
            IconStub(repeatIcon, "Repeat", repeatMode != RepeatMode.NONE, onRepeat)
            IconStub(if (isShuffled) WatermelonIcons.ShuffleOn else WatermelonIcons.ShuffleOff, "Shuffle", isShuffled, onShuffle)
            IconStub(R.drawable.ic_screenshot_single, "Screenshot", false, onScreenshot)  // custom keep
            IconStub(R.drawable.ic_sleep_timer, "Sleep timer", false, onSleepTimer)  // custom keep
            IconStub(R.drawable.ic_pip, "PiP", isPiP, onPip)  // custom keep
            IconStub(R.drawable.ic_background_play, "Background play", isBackground, onBackground)  // custom keep
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
        PanelLabel("File")
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconStub(WatermelonIcons.Share, "Share", false, onShare)
            IconStub(
                icon        = if (isFavourite) WatermelonIcons.Favorite else WatermelonIcons.FavoriteBorder,
                description = if (isFavourite) "Remove from favourites" else "Add to favourites",
                active      = isFavourite,
                onClick     = onFavourite
            )
            IconStub(WatermelonIcons.PlaylistAdd, "Add to playlist", false, onAddToPlaylist)
            IconStub(WatermelonIcons.Delete, "Delete", false, onDelete)
        }
    }
}

@Composable
private fun PanelLabel(text: String) {
    Text(text, color = PlayerColors.current.textSecondary, fontSize = 11.sp)
}

@Composable
private fun IconStub(icon: Any, description: String, active: Boolean, onClick: () -> Unit) {  // updated to accept ImageVector or Int
    IconButton(onClick = onClick) {
        when (icon) {
            is androidx.compose.ui.graphics.vector.ImageVector -> {
                Icon(icon, description,
                    tint = if (active) PlayerColors.current.iconActive else PlayerColors.current.iconDefault,
                    modifier = Modifier.width(22.dp).height(22.dp))
            }
            is Int -> {
                Icon(painterResource(icon), description,
                    tint = if (active) PlayerColors.current.iconActive else PlayerColors.current.iconDefault,
                    modifier = Modifier.width(22.dp).height(22.dp))
            }
            else -> {}
        }
    }
}

private fun formatSpeed(speed: Float): String =
    if (speed == speed.toLong().toFloat()) "${speed.toLong()}×" else "${speed}×"
