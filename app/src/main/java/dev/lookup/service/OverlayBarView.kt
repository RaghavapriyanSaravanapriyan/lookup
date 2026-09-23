package dev.lookup.service

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.View
import dev.lookup.detection.OverlayPalette
import kotlin.math.roundToInt

/**
 * The warning bar drawn over other apps. A thin, non-interactive rounded bar
 * pinned to the top edge of the screen (over the status bar) whose gradient
 * shifts blue -> amber -> red with the detection confidence. Above the pulse
 * threshold it gently pulses to draw the eye.
 */
class OverlayBarView(context: Context) : View(context) {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var confidence = 0f
    private var pulsePhase = 0f

    private val pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = PULSE_DURATION_MS
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener { animator ->
            pulsePhase = animator.animatedValue as Float
            invalidate()
        }
    }

    fun setConfidence(value: Float) {
        confidence = value.coerceIn(0f, 100f)
        val shouldPulse = confidence >= OverlayPalette.PULSE_CONFIDENCE
        if (shouldPulse && !pulseAnimator.isRunning) pulseAnimator.start()
        if (!shouldPulse && pulseAnimator.isRunning) {
            pulseAnimator.cancel()
            pulsePhase = 0f
        }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        pulseAnimator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val (colorA, colorB) = OverlayPalette.colors(confidence)
        barPaint.shader = LinearGradient(0f, 0f, w, 0f, colorA, colorB, Shader.TileMode.CLAMP)
        barPaint.alpha = if (confidence >= OverlayPalette.PULSE_CONFIDENCE) {
            (255 * (0.78f + 0.22f * pulsePhase)).toInt().coerceIn(0, 255)
        } else {
            255
        }
        canvas.drawRoundRect(0f, 0f, w, h, h / 2f, h / 2f, barPaint)
    }

    companion object {
        private const val PULSE_DURATION_MS = 550L
        private const val MIN_HEIGHT_DP = 3f
        private const val MAX_HEIGHT_DP = 9f

        /** Pixel height of the bar for a given confidence, 3dp..9dp. */
        fun heightFor(density: Float, confidence: Float): Int {
            val t = confidence.coerceIn(0f, 100f) / 100f
            return ((MIN_HEIGHT_DP + (MAX_HEIGHT_DP - MIN_HEIGHT_DP) * t) * density)
                .roundToInt().coerceAtLeast(1)
        }
    }
}
