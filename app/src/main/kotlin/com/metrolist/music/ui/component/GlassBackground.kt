/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.Choreographer
import android.view.PixelCopy
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.max
import kotlin.math.min

/**
 * Frosted-glass backdrop for the navigation bar.
 *
 * Captures the band of window content directly above the bar (never the bar
 * itself, so there is no feedback loop) and draws it, blurred, behind the bar.
 * Use only when [DeviceTier.supportsLiveBlur] is satisfied; otherwise the
 * caller should fall back to a flat translucent container.
 */
@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    blurRadius: Dp = 18.dp,
    tintColor: Color,
) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return

    Box(modifier = modifier) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx -> BlurCaptureView(ctx, activity, blurRadius) },
            )
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(tintColor.copy(alpha = 0.25f), tintColor.copy(alpha = 0.6f)),
                        ),
                    ),
        )
    }
}

/**
 * Hardware-backed PixelCopy loop: reads the compositor frame, crops the clean
 * band above the nav bar, and blurs it on its own layer via [RenderEffect].
 * Refreshes at most [REFRESH_INTERVAL_NS] while visible. API 31+ only - the
 * caller must gate on [Build.VERSION.SDK_INT].
 */
private class BlurCaptureView(
    context: android.content.Context,
    private val activity: Activity,
    blurRadius: Dp,
) : View(context) {

    private val blurRadiusPx: Float = blurRadius.value * resources.displayMetrics.density

    private var snapshot: Bitmap? = null
    private var captureInFlight = false
    private var running = false
    private var lastCaptureAt = 0L
    private var consecutiveFailures = 0
    private val windowLocation = IntArray(2)
    private val srcRect = Rect()

    private val frameCallback: Choreographer.FrameCallback = Choreographer.FrameCallback { _ ->
        if (running && isShown) {
            val now = System.nanoTime()
            if (now - lastCaptureAt >= REFRESH_INTERVAL_NS) {
                lastCaptureAt = now
                capture()
            }
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setRenderEffect(
                RenderEffect.createBlurEffect(blurRadiusPx, blurRadiusPx, Shader.TileMode.CLAMP),
            )
        }
        if (visibility == View.VISIBLE) {
            start()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        start()
    }

    override fun onDetachedFromWindow() {
        running = false
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == View.VISIBLE) start() else running = false
    }

    private fun start() {
        if (!running && isAttachedToWindow && visibility == View.VISIBLE) {
            running = true
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) {
            snapshot = null
            return
        }
        val old = snapshot
        val created = Bitmap.createBitmap(w, h, Config.ARGB_8888)
        snapshot = created
        if (old != null && old !== created) {
            old.recycle()
        }
    }

    private fun capture() {
        val bitmap = snapshot ?: return
        if (captureInFlight) return
        if (activity.isFinishing || activity.isDestroyed) return
        if (!updateSourceRect()) return
        captureInFlight = true
        try {
            PixelCopy.request(
                activity.window,
                srcRect,
                bitmap,
                { result ->
                    captureInFlight = false
                    if (result == PixelCopy.SUCCESS) {
                        consecutiveFailures = 0
                        invalidate()
                    }
                },
                handler,
            )
        } catch (e: IllegalArgumentException) {
            // "Window doesn't have a backing surface!": thrown synchronously when
            // the window is between attach/detach (app backgrounded, activity
            // recreating, first frame after re-entry). Skip the frame instead of
            // crashing the whole app.
            captureInFlight = false
            consecutiveFailures++
            // Some OEM surfaces never expose a PixelCopy-able backing store.
            // Stop the capture loop so it can't crash or burn battery forever.
            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                running = false
            }
        }
    }

    private fun updateSourceRect(): Boolean {
        val window = activity.window ?: return false
        val decor = window.decorView
        val windowWidth = decor.width
        if (windowWidth == 0 || height == 0) return false
        getLocationInWindow(windowLocation)
        val barTop = windowLocation[1]
        val bandHeight = max(min(height, barTop), 1)
        srcRect.set(0, barTop - bandHeight, windowWidth, barTop)
        return srcRect.width() > 0 && srcRect.height() > 0
    }

    private companion object {
        const val REFRESH_INTERVAL_NS = 40_000_000L
        const val MAX_CONSECUTIVE_FAILURES = 6
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        snapshot?.let { bitmap ->
            if (!bitmap.isRecycled) {
                canvas.drawBitmap(bitmap, null, Rect(0, 0, width, height), null)
            }
        }
    }
}