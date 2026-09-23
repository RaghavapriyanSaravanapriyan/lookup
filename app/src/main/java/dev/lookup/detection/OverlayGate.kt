package dev.lookup.detection

/**
 * Pure hysteresis/debounce gate deciding when the overlay warning bar should
 * be visible. Zero Android dependencies — the service feeds it the engine's
 * [EngineSnapshot.confidence] at ~25 Hz; unit tests feed it synthetic ramps.
 *
 * Behavior:
 * - The bar turns on only after confidence stays at or above [showThreshold]
 *   for [riseDebounceNanos] (appears fast, as a cue).
 * - The bar turns off only after confidence stays at or below [hideThreshold]
 *   for [fallDebounceNanos] (disappears a touch slower so boundary noise
 *   does not flicker it).
 * - Between the two thresholds is a hysteresis band: the current state holds
 *   and both debounce timers reset, so hovering at the boundary can neither
 *   appear nor disappear the bar.
 */
class OverlayGate(
    private val showThreshold: Float = SHOW_THRESHOLD,
    private val hideThreshold: Float = HIDE_THRESHOLD,
    private val riseDebounceNanos: Long = RISE_DEBOUNCE_NANOS,
    private val fallDebounceNanos: Long = FALL_DEBOUNCE_NANOS,
) {
    var visible: Boolean = false
        private set

    private var aboveSince = 0L
    private var belowSince = 0L

    /**
     * Feed one confidence sample. [nowNanos] must be monotonic (the same
     * clock driving the engine). Returns the desired visibility.
     */
    fun onConfidence(confidence: Float, nowNanos: Long): Boolean {
        when {
            confidence >= showThreshold -> {
                belowSince = 0L
                if (aboveSince == 0L) aboveSince = nowNanos
                if (!visible && nowNanos - aboveSince >= riseDebounceNanos) visible = true
            }
            confidence <= hideThreshold -> {
                aboveSince = 0L
                if (belowSince == 0L) belowSince = nowNanos
                if (visible && nowNanos - belowSince >= fallDebounceNanos) visible = false
            }
            else -> {
                aboveSince = 0L
                belowSince = 0L
            }
        }
        return visible
    }

    companion object {
        /** Appear when confidence sustains at/above this. */
        const val SHOW_THRESHOLD = 40f
        /** Disappear when confidence sustains at/below this. */
        const val HIDE_THRESHOLD = 30f
        /** Confidence must hold above the show threshold this long. */
        const val RISE_DEBOUNCE_NANOS = 250_000_000L
        /** Confidence must hold below the hide threshold this long. */
        const val FALL_DEBOUNCE_NANOS = 400_000_000L
    }
}
