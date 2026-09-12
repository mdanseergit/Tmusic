/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService

/**
 * Device capability detection used to gate expensive rendering effects
 * (live blur, heavy animations) behind a single source of truth.
 */
object DeviceTier {

    private var cachedContext: Context? = null
    private var cachedIsLowEnd: Boolean? = null
    private var cachedIsLargeRam: Boolean? = null

    private fun contextOf(context: Context): Context =
        cachedContext ?: context.applicationContext.also { cachedContext = it }

    /** Low-RAM devices (ActivityManager flag) or devices with under ~3GB of total RAM. */
    fun isLowEndDevice(context: Context): Boolean {
        val appContext = contextOf(context)
        return cachedIsLowEnd
            ?: (activityManager(appContext)?.isLowRamDevice == true || totalRamMb(appContext) < 3072)
                .also {
                    cachedIsLowEnd = it
                    if (it) cachedIsLargeRam = false
                }
    }

    /** More than 6GB of total RAM — safe to enable the most expensive effects. */
    fun isLargeRamDevice(context: Context): Boolean {
        val appContext = contextOf(context)
        return cachedIsLargeRam
            ?: (totalRamMb(appContext) >= 6144).also { cachedIsLargeRam = it }
    }

    /**
     * Live (per-frame) backdrop blur requires hardware acceleration and the
     * RenderEffect API (Android 12+); it is disabled on low-end devices.
     */
    fun supportsLiveBlur(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !isLowEndDevice(context)

    /** Graphically heavy per-frame work (blur, redundant layers). */
    fun canUseHeavyEffects(context: Context): Boolean = !isLowEndDevice(context)

    private fun activityManager(context: Context): ActivityManager? =
        runCatching { context.getSystemService<ActivityManager>() }.getOrNull()

    private fun totalRamMb(context: Context): Long {
        val am = activityManager(context) ?: return Long.MAX_VALUE
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalBytes = runCatching { memInfo.totalMem }.getOrDefault(Long.MAX_VALUE)
        return totalBytes / (1024 * 1024)
    }
}