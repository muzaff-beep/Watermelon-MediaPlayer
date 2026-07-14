package com.watermelon.app

import android.app.ActivityManager
import android.content.Context
import com.watermelon.common.model.VhsTier

/**
 * Detects the device's total RAM and derives the default [VhsTier]. The user can override
 * the auto-detected tier in settings (mapped from VhsIntensity), but this provides the
 * RAM-appropriate default so low-end devices don't attempt the heaviest shader.
 */
object VhsCapability {

    fun detectTier(context: Context): VhsTier {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return VhsTier.fromTotalRamBytes(info.totalMem)
    }
}
