package com.watermelon.app

import android.content.SharedPreferences
import com.watermelon.common.model.SubtitleDirection
import com.watermelon.common.model.SubtitlePosition
import com.watermelon.common.model.SubtitleStyle
import com.watermelon.ui.screens.ScreenshotMode
import com.watermelon.ui.screens.SettingsState
import com.watermelon.ui.screens.VhsIntensity

/**
 * Loads the full [SettingsState] from [prefs]. Every field has a fallback to its
 * [SettingsState] default, so a fresh install (no keys written yet) or a key from an older
 * app version that doesn't exist yet both degrade to sane defaults rather than crashing.
 *
 * `pureDark` and folder visibility are deliberately NOT included here — pureDark is read/
 * written directly by MainActivity (it needs to be known before the first composition to
 * pick the initial theme), and folder visibility is a separate SQLite-backed store
 * (FolderVisibilityStoreImpl) with its own per-folder persistence, not a single flat pref.
 */
fun loadSettingsState(prefs: SharedPreferences, pureDark: Boolean): SettingsState = SettingsState(
    pureDark = pureDark,
    forcedRtl = prefs.getBoolean("forced_rtl", false),
    gridDefault = prefs.getBoolean("grid_default", false),
    showThumbnails = prefs.getBoolean("show_thumbnails", true),
    showDurations = prefs.getBoolean("show_durations", true),
    showFileSize = prefs.getBoolean("show_file_size", false),
    vhsEnabled = prefs.getBoolean("vhs_enabled", true),
    vhsIntensity = runCatching {
        VhsIntensity.valueOf(prefs.getString("vhs_intensity", null) ?: VhsIntensity.MED.name)
    }.getOrDefault(VhsIntensity.MED),
    tunerSeekBarEnabled = prefs.getBoolean("tuner_seekbar_enabled", true),
    tunerSeekStepSeconds = prefs.getInt("tuner_seek_step_seconds", 5).coerceIn(1, 20),
    memorySafety = prefs.getBoolean("memory_safety", false),
    fullFolderAccess = prefs.getBoolean("full_folder_access", false),
    screenshotMode = runCatching {
        ScreenshotMode.valueOf(prefs.getString("screenshot_mode", null) ?: ScreenshotMode.SINGLE.name)
    }.getOrDefault(ScreenshotMode.SINGLE),
    continueWatchingEnabled = prefs.getBoolean("continue_watching_enabled", true),
    subtitleStyle = SubtitleStyle(
        enabled = prefs.getBoolean("subtitle_enabled", true),
        sizeSp = prefs.getInt("subtitle_size_sp", 18),
        textColorArgb = prefs.getLong("subtitle_color_argb", 0xFFFFFFFF.toLong()),
        position = runCatching {
            SubtitlePosition.valueOf(prefs.getString("subtitle_position", null) ?: SubtitlePosition.BOTTOM.name)
        }.getOrDefault(SubtitlePosition.BOTTOM),
        bold = prefs.getBoolean("subtitle_bold", false),
        italic = prefs.getBoolean("subtitle_italic", false),
        underline = prefs.getBoolean("subtitle_underline", false),
        direction = runCatching {
            SubtitleDirection.valueOf(prefs.getString("subtitle_direction", null) ?: SubtitleDirection.AUTO.name)
        }.getOrDefault(SubtitleDirection.AUTO),
        secondaryDirection = runCatching {
            SubtitleDirection.valueOf(
                prefs.getString("subtitle_secondary_direction", null) ?: SubtitleDirection.AUTO.name
            )
        }.getOrDefault(SubtitleDirection.AUTO)
    )
)

/**
 * Writes every field of [state] to [prefs] in one batch `apply()`. Called on every
 * SettingsScreen.onStateChange, so any toggle — not just the 3 that used to be wired
 * (vhsEnabled/vhsIntensity/tunerSeekBarEnabled) — survives an app restart.
 */
fun saveSettingsState(prefs: SharedPreferences, state: SettingsState) {
    prefs.edit()
        .putBoolean("forced_rtl", state.forcedRtl)
        .putBoolean("grid_default", state.gridDefault)
        .putBoolean("show_thumbnails", state.showThumbnails)
        .putBoolean("show_durations", state.showDurations)
        .putBoolean("show_file_size", state.showFileSize)
        .putBoolean("vhs_enabled", state.vhsEnabled)
        .putString("vhs_intensity", state.vhsIntensity.name)
        .putBoolean("tuner_seekbar_enabled", state.tunerSeekBarEnabled)
        .putInt("tuner_seek_step_seconds", state.tunerSeekStepSeconds)
        .putBoolean("memory_safety", state.memorySafety)
        .putBoolean("full_folder_access", state.fullFolderAccess)
        .putString("screenshot_mode", state.screenshotMode.name)
        .putBoolean("continue_watching_enabled", state.continueWatchingEnabled)
        .putBoolean("subtitle_enabled", state.subtitleStyle.enabled)
        .putInt("subtitle_size_sp", state.subtitleStyle.sizeSp)
        .putLong("subtitle_color_argb", state.subtitleStyle.textColorArgb)
        .putString("subtitle_position", state.subtitleStyle.position.name)
        .putBoolean("subtitle_bold", state.subtitleStyle.bold)
        .putBoolean("subtitle_italic", state.subtitleStyle.italic)
        .putBoolean("subtitle_underline", state.subtitleStyle.underline)
        .putString("subtitle_direction", state.subtitleStyle.direction.name)
        .putString("subtitle_secondary_direction", state.subtitleStyle.secondaryDirection.name)
        .apply()
}
