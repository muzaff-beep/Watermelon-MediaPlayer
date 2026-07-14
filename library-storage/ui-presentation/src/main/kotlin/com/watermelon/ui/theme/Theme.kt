package com.watermelon.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * Composition-local flag for whether the app is currently in dark mode. Read by
 * [PlayerColors.current] so the custom-drawn player controls (which bypass Material3's
 * colorScheme entirely) stay in sync with the same toggle as everything else.
 */
val LocalWatermelonDarkTheme = compositionLocalOf { true }

/**
 * Material 3 theme with RTL-first layout overrides (Manifest §1.1, Teams §6) and a
 * dark/light toggle driven by Settings → Appearance → "Pure dark theme".
 *
 * Watermelon is RTL-native: for Persian/Arabic locales the entire layout direction is
 * inverted at the theme root, not mirrored per-widget. Pass [forceRtl] = true to force RTL
 * regardless of system locale (the "Forced RTL overrides per locale" setting).
 *
 * @param darkTheme true = dark scheme (default), false = light scheme. Bound to
 *   [com.watermelon.ui.screens.SettingsState.pureDark] by the app shell.
 */
@Composable
fun WatermelonTheme(
    darkTheme: Boolean = true,
    forceRtl: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            background = WatermelonColors.DarkBackground,
            surface = WatermelonColors.DarkSurface,
            surfaceVariant = WatermelonColors.DarkSurfaceVariant,
            onBackground = WatermelonColors.DarkOnBackground,
            onSurface = WatermelonColors.DarkOnSurface,
            onSurfaceVariant = WatermelonColors.DarkOnSurfaceVariant,
            primary = WatermelonColors.Accent,
            onPrimary = WatermelonColors.OnAccent,
            secondary = WatermelonColors.AccentVariant,
            error = WatermelonColors.Error,
            outline = WatermelonColors.DarkOutline
        )
    } else {
        lightColorScheme(
            background = WatermelonColors.LightBackground,
            surface = WatermelonColors.LightSurface,
            surfaceVariant = WatermelonColors.LightSurfaceVariant,
            onBackground = WatermelonColors.LightOnBackground,
            onSurface = WatermelonColors.LightOnSurface,
            onSurfaceVariant = WatermelonColors.LightOnSurfaceVariant,
            primary = WatermelonColors.Accent,
            onPrimary = WatermelonColors.OnAccent,
            secondary = WatermelonColors.AccentVariant,
            error = WatermelonColors.Error,
            outline = WatermelonColors.LightOutline
        )
    }

    val layoutDirection =
        if (forceRtl) LayoutDirection.Rtl else LocalLayoutDirection.current

    CompositionLocalProvider(
        LocalLayoutDirection provides layoutDirection,
        LocalWatermelonDarkTheme provides darkTheme
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = WatermelonTypography.typography,
            content = content
        )
    }
}
