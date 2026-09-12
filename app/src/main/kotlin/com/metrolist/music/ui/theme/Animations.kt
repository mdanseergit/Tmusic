/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Tmusic (fork) — single source of truth for motion. Every animation in the
 * app should reference these specs so the feel stays consistent: fast,
 * standard easing for transitions, bouncy springs for emphasis moments.
 */

package com.metrolist.music.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

object MotionDefaults {
    /** Sub-200ms flicks (icon swaps, tiny fades). */
    const val DURATION_SHORT = 160

    /** Default transition duration — all screen/state changes. */
    const val DURATION_MEDIUM = 220

    /** Heavier structural transitions (sheets, large containers). */
    const val DURATION_LONG = 300

    /** Album-art crossfade duration. */
    const val CROSSFADE_MS = 300

    /** Springs stay under 380ms to avoid sluggish, "floaty" motion. */
    const val SPRING_STIFFNESS = 380f

    /** Slight overshoot for emphasis moments (like, bounce, selection). */
    const val SPRING_DAMPING = 0.8f
}

/**
 * Fast, swift ease — standard StateWeighted/Material acceleration curve adapted
 * to an optimistic, energetic feel. Use for anything that moves.
 */
fun <T> standardTween(): TweenSpec<T> =
    tween(
        durationMillis = MotionDefaults.DURATION_MEDIUM,
        easing = FastOutSlowInEasing,
    )

/**
 * Emphasis spring for celebratory moments — like animation, selection,
 * checkbox ticks. Bounded duration keeps it snappy rather than floaty.
 */
fun <T> emphasisSpring(): SpringSpec<T> =
    spring(
        dampingRatio = MotionDefaults.SPRING_DAMPING,
        stiffness = MotionDefaults.SPRING_STIFFNESS,
        visibilityThreshold = null,
    )