package dev.lookup.detection

/**
 * Tunable knobs for [DetectionEngine]. Both sensitivities are normalized to 0..1
 * with 0.5 as the neutral midpoint.
 */
data class DetectionSettings(
    val motionSensitivity: Float = 0.5f,
    val lookSensitivity: Float = 0.5f,
) {
    init {
        require(motionSensitivity in 0f..1f) { "motionSensitivity must be in 0..1" }
        require(lookSensitivity in 0f..1f) { "lookSensitivity must be in 0..1" }
    }
}

/** One downsampled linear-acceleration point kept for the live debug chart. */
data class SamplePoint(
    val timestampNanos: Long,
    val deviation: Float,
)

/**
 * Full state of the engine, published at ~25 Hz for the UI and the overlay.
 */
data class EngineSnapshot(
    val timestampNanos: Long = 0L,
    /** Smoothed 0..100 confidence that the user is walking while looking at the phone. */
    val confidence: Float = 0f,
    /** 0..1 evidence of walking motion (step cadence, freshness weighted). */
    val walkingScore: Float = 0f,
    /** 0..1 evidence that the screen is visible to the user (screen, tilt, proximity). */
    val phoneInViewScore: Float = 0f,
    val screenOn: Boolean = true,
    val proximityNear: Boolean? = null,
    /** Angle between the screen normal and vertical: 0 = flat face-up, 90 = upright facing the user, 180 = face-down. */
    val tiltDeg: Float = 0f,
    /** Current step cadence in steps/min, 0 when stale. */
    val cadenceSpm: Float = 0f,
    /** Effective step-detection threshold in m/s^2 (after self-calibration and sensitivity). */
    val calibratedThreshold: Float = DetectionEngine.DEFAULT_INITIAL_THRESHOLD,
    /** Rolling median step amplitude in m/s^2 learned from the user's gait. */
    val medianStepAmplitude: Float = DetectionEngine.DEFAULT_INITIAL_AMPLITUDE,
    /** Self-calibrated baseline cadence in steps/min (0 until learned). */
    val baselineCadenceSpm: Float = 0f,
    val stepCount: Int = 0,
    /** Recent (timestamp, deviation) points for the debug chart (~10 s at 25 Hz). */
    val recentSamples: List<SamplePoint> = emptyList(),
    /** Timestamps of the most recent detected steps. */
    val recentSteps: List<Long> = emptyList(),
) {
    companion object {
        val INITIAL = EngineSnapshot()
    }
}
