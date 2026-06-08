package com.watermelon.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BroadcastReceiver for Picture-in-Picture mini controls (play/pause, next, previous, mute).
 * Each button in the PiP window sends a [PendingIntent] with one of the [ACTION_*] strings.
 *
 * [MainActivity] registers a local receiver and forwards these actions to the
 * [PlaybackController] it owns. This avoids singletons and keeps the controller
 * inside the Activity's lifecycle.
 *
 * Usage in MainActivity:
 * ```
 * private val pipReceiver = object : BroadcastReceiver() {
 *     override fun onReceive(context: Context, intent: Intent) {
 *         when (intent.action) {
 *             PiPReceiver.ACTION_PLAY_PAUSE -> { if (isPlaying) controller.pause() else controller.resume() }
 *             PiPReceiver.ACTION_PREV       -> { /* seek to previous video in queue */ }
 *             PiPReceiver.ACTION_NEXT       -> { /* seek to next video in queue */ }
 *             PiPReceiver.ACTION_MUTE       -> { /* toggle mute */ }
 *         }
 *     }
 * }
 * ```
 * Register in onStart, unregister in onStop.
 *
 * Location: app/src/main/kotlin/com/watermelon/app/PiPReceiver.kt
 * Also declare in AndroidManifest.xml:
 * <receiver android:name=".PiPReceiver" android:exported="false" />
 */
class PiPReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Relay via a local broadcast so MainActivity can handle it with controller access.
        val relay = Intent(intent.action).setPackage(context.packageName)
        context.sendBroadcast(relay)
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.watermelon.pip.PLAY_PAUSE"
        const val ACTION_PREV       = "com.watermelon.pip.PREV"
        const val ACTION_NEXT       = "com.watermelon.pip.NEXT"
        const val ACTION_MUTE       = "com.watermelon.pip.MUTE"
    }
}
