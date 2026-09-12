/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metrolist.music.utils.DeviceTier

/** True when the device is capable of expensive per-frame rendering. */
@Composable
fun isHighEndDevice(): Boolean =
    DeviceTier.canUseHeavyEffects(LocalContext.current)

/**
 * Applies a real [blur] when the device tier allows it, otherwise leaves the
 * modifier untouched. Keeps the design identical on low-end devices while
 * swapping GPU-heavy effects for a cheaper flat surface.
 */
@Composable
fun Modifier.blurIfHighEnd(radius: Dp): Modifier {
    if (!DeviceTier.canUseHeavyEffects(LocalContext.current)) return this
    return if (radius > 0.dp) this.blur(radius) else this
}