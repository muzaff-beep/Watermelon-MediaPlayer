package com.watermelon.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Centralized icons for WatermelonPlayer.
 * Replaces R.drawable where possible with Material Icons to reduce XML bloat.
 * Custom themed icons still use painterResource(R.drawable.*) for now.
 */
object WatermelonIcons {
    // Playback
    val Play: ImageVector = Icons.Filled.PlayArrow
    val Pause: ImageVector = Icons.Filled.Pause
    val SkipNext: ImageVector = Icons.Filled.SkipNext
    val SkipPrevious: ImageVector = Icons.Filled.SkipPrevious
    val FastForward: ImageVector = Icons.Filled.FastForward
    val Rewind: ImageVector = Icons.Filled.Replay

    // Volume & Audio
    val VolumeHigh: ImageVector = Icons.Filled.VolumeUp
    val VolumeMedium: ImageVector = Icons.Filled.VolumeDown
    val VolumeLow: ImageVector = Icons.Filled.VolumeDown
    val VolumeMute: ImageVector = Icons.Filled.VolumeOff
    val VolumeMuteOff: ImageVector = Icons.Filled.VolumeUp // toggle

    // Repeat & Shuffle
    val RepeatOff: ImageVector = Icons.Filled.Repeat
    val RepeatOne: ImageVector = Icons.Filled.RepeatOne
    val RepeatAll: ImageVector = Icons.Filled.Repeat
    val ShuffleOn: ImageVector = Icons.Filled.Shuffle
    val ShuffleOff: ImageVector = Icons.Filled.Shuffle

    // Common actions
    val Share: ImageVector = Icons.Filled.Share
    val Favorite: ImageVector = Icons.Filled.Favorite
    val FavoriteBorder: ImageVector = Icons.Filled.FavoriteBorder
    val Delete: ImageVector = Icons.Filled.Delete
    val PlaylistAdd: ImageVector = Icons.Filled.PlaylistAdd
    val Search: ImageVector = Icons.Filled.Search
    val Settings: ImageVector = Icons.Filled.Settings
    val Close: ImageVector = Icons.Filled.Close
    val Check: ImageVector = Icons.Filled.Check
    val Edit: ImageVector = Icons.Filled.Edit
    val Refresh: ImageVector = Icons.Filled.Refresh

    // Layout & View
    val ViewList: ImageVector = Icons.Filled.ViewList
    val ViewGrid: ImageVector = Icons.Filled.ViewModule
    val Sort: ImageVector = Icons.Filled.Sort

    // Note: Custom app-themed icons (VHS, sleep timer, PiP, screenshot, badge_new, size_*, sort_asc/desc, orientation, etc.)
    // still require painterResource(R.drawable.ic_*) or custom ImageVector definitions.
    // Next step: migrate remaining or define as vectors.
}