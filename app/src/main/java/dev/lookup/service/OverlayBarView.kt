package dev.lookup.service

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.View
import android.view.animation.PathInterpolator
import androidx.core.animation.doOnEnd
import dev.lookup.detection.OverlayPalette
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The warning bar drawn over other apps: a non-interactive pill pinned to the
 * top edge of the screen (over the status bar) whose gradient shifts
 * blue -> amber -> red with the detection confidence.
 *
 * The window itself is a fixed-height transparent frame; the drawn bar grows
 * 5 dp -> 12 dp with confidence so no window relayout is ever needed. Showing
 * and hiding animates a reveal factor (alpha * drawn height) in ~220 ms so
 * the cue registers immediately without popping. At high confidence the bar
 * keeps full opacity and pulses via a slight height swell instead.
 */
class OverlayBarView(context: Context) : View(context) {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var confidence = 0f
    private var reveal = 0f
    private var pulsePhase = 0f
    private var transition: ValueAnimator? = null

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

    /** Fast fade/scale in. Safe to call while a disappear animation is running. */
    fun appear() {
        transition?.cancel()
        transition = ValueAnimator.ofFloat(reveal, 1f).apply {
            duration = APPEAR_MS
            interpolator = APPEAR_INTERPOLATOR
            addUpdateListener { animator ->
                reveal = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /**
     * Fast fade/scale out. [onFinished] runs only if the animation completes —
     * a cancelled animation (e.g. confidence rose again) does not invoke it.
     */
    fun disappear(onFinished: () -> Unit) {
        transition?.cancel()
        transition = ValueAnimator.ofFloat(reveal, 0f).apply {
            duration = DISAPPEAR_MS
            addUpdateListener { animator ->
                reveal = animator.animatedValue as Float
                invalidate()
            }
            doOnEnd { onFinished() }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        transition?.cancel()
        pulseAnimator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val windowH = height.toFloat()
        if (w <= 0f || windowH <= 0f || reveal <= 0.01f) return

        // Height pulse at high confidence — opacity stays at full.
        val pulseBoost = if (confidence >= OverlayPalette.PULSE_CONFIDENCE) {
            1f + PULSE_HEIGHT_BOOST * pulsePhase
        } else {
            1f
        }
        val barH = min(barHeightPx(resources.displayMetrics.density, confidence) * reveal * pulseBoost, windowH)
        if (barH <= 0.5f) return

        val (colorA, colorB) = OverlayPalette.colors(confidence)
        barPaint.shader = LinearGradient(0f, 0f, w, 0f, colorA, colorB, Shader.TileMode.CLAMP)
        barPaint.alpha = (255 * reveal.coerceIn(0f, 1f)).toInt()
        canvas.drawRoundRect(0f, 0f, w, barH, barH / 2f, barH / 2f, barPaint)
    }

    companion object {
        private const val PULSE_DURATION_MS = 550L
        private const val APPEAR_MS = 220L
        private const val DISAPPEAR_MS = 180L
        private const val PULSE_HEIGHT_BOOST = 0.12f
        private const val MIN_HEIGHT_DP = 5f
        private const val MAX_HEIGHT_DP = 12f
        private const val WINDOW_HEIGHT_DP = 15f
        private val APPEAR_INTERPOLATOR = PathInterpolator(0.4f, 0f, 0.2f, 1f)

        /** Drawn bar height for a given confidence, 5 dp -> 12 dp. */
        fun barHeightPx(density: Float, confidence: Float): Float {
            val t = confidence.coerceIn(0f, 100f) / 100f
            return (MIN_HEIGHT_DP + (MAX_HEIGHT_DP - MIN_HEIGHT_DP) * t) * density
        }

        /** Fixed window height (max bar + pulse headroom) — never needs relayout. */
        fun windowHeightPx(density: Float): Int =
            (WINDOW_HEIGHT_DP * density).roundToInt()
    }
}
