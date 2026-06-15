package com.watermelon.ui.screens

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration

/**
 * Detects whether the app is running on a TV (leanback) device, so the player can route to
 * the appropriate composition. Phone and TV are separate compositions sharing one playback
 * core and one [PlayerUiState] — not a single screen with conditionals.
 */
object PlayerDeviceRouting {
    fun isTelevision(context: Context): Boolean {
        val ui = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        if (ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true
        // Fallback: leanback feature flag.
        val pm = context.packageManager
        return pm.hasSystemFeature("android.software.leanback") ||
               pm.hasSystemFeature("android.hardware.type.television")
    }
}
