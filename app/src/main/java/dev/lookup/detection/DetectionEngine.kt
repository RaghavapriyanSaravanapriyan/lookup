package dev.lookup.detection

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.acos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Pure-Kotlin sensor-fusion engine that scores how likely it is that the user is
 * walking while looking at their phone. It has no Android dependencies; the host
 * service feeds it sensor samples and collects the published state.
 *
 * Fusion inputs:
 *  - accelerometer  -> gravity estimation, tilt angle, step detection (deviation peaks)
 *  - proximity      -> near (pocket / against body) strongly suppresses "phone in view"
 *  - screen state   -> distraction requires the screen to be on
 *  - user settings   -> motion / look sensitivities
 *
 * Scoring: confidence = walkingScore * phoneInViewScore * 100, smoothed with an EMA.
 *
 * Rolling self-calibration on walking cadence:
 *  - the step threshold tracks 0.45x of the rolling median step amplitude
 *    (clamped), so it adapts to the user's gait strength;
 *  - a baseline cadence is learned from sustained walking and relaxes the
 *    cadence needed for a full walking score;
 *  - the amplitude estimate decays back to the population default while the
 *    user is not stepping, so thresholds follow gait changes in both directions.
 */
class DetectionEngine(
    initialSettings: DetectionSettings = DetectionSettings(),
) {
    companion object {
        /** Bootstrap median step amplitude (m/s^2) until the user's own gait is learned. */
        const val DEFAULT_INITIAL_AMPLITUDE = 1.6f
        /** Threshold (m/s^2) the engine starts with at neutral sensitivity. */
        const val DEFAULT_INITIAL_THRESHOLD = 0.72f
        const val STEP_THRESHOLD_RATIO = 0.45f
        const val MIN_THRESHOLD = 0.70f
        const val MAX_THRESHOLD = 2.60f
        const val MIN_STEP_INTERVAL_NS = 260_000_000L
        const val MAX_STEP_INTERVAL_NS = 1_400_000_000L
        const val TILT_BAND_LOW_DEG = 55f
        const val TILT_BAND_HIGH_DEG = 120f
        const val DEFAULT_FULL_SPM = 60f
        const val POCKET_CAP = 0.12f
        const val PROXIMITY_UNKNOWN_FACTOR = 0.78f

        private const val LOWPASS_TAU_S = 0.18f
        private const val GAP_RESET_S = 1.5f
        private const val DEFAULT_DT_S = 0.02f
        private const val STEP_FALL_RATIO = 0.5f
        private const val PEAK_TIMEOUT_NS = 700_000_000L
        private const val STEP_HISTORY = 24
        private const val RECENT_STEPS = 24
        private const val SUSTAINED_WINDOW_NS = 8_000_000_000L
        private const val INACTIVE_DECAY_AFTER_NS = 4_000_000_000L
        private const val INACTIVE_DECAY_TAU_S = 12f
        private const val CADENCE_CLEAR_MS = 6_000f
        private const val FRESHNESS_GRACE_MS = 1_600f
        private const val FRESHNESS_TAU_MS = 1_400f
        private const val TILT_FALLOFF_DEG = 45f
        private const val TILT_WEIGHT = 0.62f
        private const val PROXIMITY_WEIGHT = 0.38f
        private const val SMOOTHING_TAU_S = 0.45f
        private const val PUBLISH_EVERY = 2
        private const val CHART_POINTS = 260
    }

    private var settings = initialSettings

    private val _confidence = MutableStateFlow(0)
    val confidence: StateFlow<Int> = _confidence

    private val _snapshot = MutableStateFlow(EngineSnapshot.INITIAL)
    val snapshot: StateFlow<EngineSnapshot> = _snapshot

    // Gravity estimation (low-pass of the raw accelerometer vector).
    private val gravity = FloatArray(3)
    private var gravitySeeded = false
    private var lastTimestamp = 0L

    // Step detection state machine.
    private var inPeak = false
    private var peakValue = 0f
    private var peakStart = 0L
    private var lastStepAt = 0L

    // Rolling self-calibration.
    private var medianAmplitude = DEFAULT_INITIAL_AMPLITUDE
    private val stepTimes = ArrayDeque<Long>()
    private val stepAmplitudes = ArrayDeque<Float>()
    private var cadenceSpm = 0f
    private var baselineCadence = 0f
    private var stepCount = 0

    // Environment inputs.
    private var screenOn = true
    private var proximityNear: Boolean? = null
    private var tiltDeg = 0f
    private var smoothedConfidence = 0f
    private var lastWalkingScore = 0f
    private var lastViewScore = 0f

    // Ring buffers for the published snapshot.
    private val chart = ArrayDeque<SamplePoint>()
    private val recentSteps = ArrayDeque<Long>()
    private var publishCounter = 0

    fun onSettingsChanged(newSettings: DetectionSettings) {
        settings = newSettings
    }

    fun onScreenStateChanged(on: Boolean) {
        screenOn = on
    }

    fun onProximityChanged(near: Boolean?) {
        proximityNear = near
    }

    /** Drops all learned gait calibration and step history. */
    fun resetCalibration() {
        stepTimes.clear()
        stepAmplitudes.clear()
        recentSteps.clear()
        inPeak = false
        lastStepAt = 0L
        medianAmplitude = DEFAULT_INITIAL_AMPLITUDE
        cadenceSpm = 0f
        baselineCadence = 0f
    }

    /**
     * Feed one accelerometer sample. [timestampNanos] must come from a monotonic
     * clock (e.g. SensorEvent.timestamp); all engine timing derives from it.
     */
    fun onAccelerometer(x: Float, y: Float, z: Float, timestampNanos: Long) {
        if (timestampNanos <= lastTimestamp) return
        val dt = if (lastTimestamp == 0L) DEFAULT_DT_S
        else (timestampNanos - lastTimestamp) * 1e-9f
        if (dt > GAP_RESET_S) {
            // Long gap (sensor stall): rebuild the gravity estimate from scratch.
            gravitySeeded = false
            inPeak = false
            chart.clear()
        }
        lastTimestamp = timestampNanos

        if (!gravitySeeded) {
            gravity[0] = x
            gravity[1] = y
            gravity[2] = z
            gravitySeeded = true
        } else {
            val alpha = LOWPASS_TAU_S / (LOWPASS_TAU_S + dt)
            gravity[0] = alpha * gravity[0] + (1 - alpha) * x
            gravity[1] = alpha * gravity[1] + (1 - alpha) * y
            gravity[2] = alpha * gravity[2] + (1 - alpha) * z
        }
        val gMag = sqrt(gravity[0] * gravity[0] + gravity[1] * gravity[1] + gravity[2] * gravity[2])
            .coerceAtLeast(0.1f)
        tiltDeg = Math.toDegrees(acos((gravity[2] / gMag).coerceIn(-1f, 1f)).toDouble()).toFloat()

        val dx = x - gravity[0]
        val dy = y - gravity[1]
        val dz = z - gravity[2]
        val deviation = sqrt(dx * dx + dy * dy + dz * dz)

        detectStep(deviation, timestampNanos)
        evaluate(dt, timestampNanos)

        if (++publishCounter % PUBLISH_EVERY == 0) {
            chart.addLast(SamplePoint(timestampNanos, deviation))
            while (chart.size > CHART_POINTS) chart.removeFirst()
            publishSnapshot(timestampNanos)
        }
    }

    private fun detectStep(deviation: Float, now: Long) {
        val threshold = currentThreshold()
        if (!inPeak) {
            if (deviation > threshold) {
                inPeak = true
                peakValue = deviation
                peakStart = now
            }
        } else {
            if (deviation > peakValue) peakValue = deviation
            if (deviation < threshold * STEP_FALL_RATIO) {
                inPeak = false
                if (now - lastStepAt >= MIN_STEP_INTERVAL_NS) {
                    recordStep(now, peakValue)
                }
            } else if (now - peakStart > PEAK_TIMEOUT_NS) {
                inPeak = false
            }
        }
    }

    private fun recordStep(now: Long, amplitude: Float) {
        lastStepAt = now
        stepCount++
        stepTimes.addLast(now)
        stepAmplitudes.addLast(amplitude)
        while (stepTimes.size > STEP_HISTORY) {
            stepTimes.removeFirst()
            stepAmplitudes.removeFirst()
        }
        recentSteps.addLast(now)
        while (recentSteps.size > RECENT_STEPS) recentSteps.removeFirst()

        // Rolling calibration, part 1: threshold follows the user's step amplitude.
        medianAmplitude = medianOf(stepAmplitudes)

        val intervals = ArrayList<Long>(stepTimes.size - 1)
        var prev: Long? = null
        for (t in stepTimes) {
            if (prev != null) {
                val interval = t - prev
                if (interval in MIN_STEP_INTERVAL_NS..MAX_STEP_INTERVAL_NS) intervals.add(interval)
            }
            prev = t
        }
        if (intervals.size >= 2) {
            cadenceSpm = 60e9f / medianOfLong(intervals)
            // Rolling calibration, part 2: learn the user's baseline walking cadence.
            if (stepTimes.size >= 5 && now - stepTimes.first() <= SUSTAINED_WINDOW_NS) {
                baselineCadence = if (baselineCadence <= 0f) cadenceSpm
                else baselineCadence + 0.15f * (cadenceSpm - baselineCadence)
            }
        }
    }

    private fun evaluate(dt: Float, now: Long) {
        // Rolling calibration, part 3: while not stepping, drift the amplitude
        // estimate back toward the population default so thresholds track gait changes.
        if (lastStepAt != 0L && now - lastStepAt > INACTIVE_DECAY_AFTER_NS) {
            val idleS = (now - lastStepAt - INACTIVE_DECAY_AFTER_NS) * 1e-9f
            val k = 1f - exp(-idleS / INACTIVE_DECAY_TAU_S)
            medianAmplitude += (DEFAULT_INITIAL_AMPLITUDE - medianAmplitude) * k
        }

        val freshMs = if (lastStepAt == 0L) Float.MAX_VALUE else (now - lastStepAt) * 1e-6f
        if (freshMs > CADENCE_CLEAR_MS) cadenceSpm = 0f

        val freshness = if (freshMs < FRESHNESS_GRACE_MS) 1f
        else exp(-(freshMs - FRESHNESS_GRACE_MS) / FRESHNESS_TAU_MS)
        // Once the user's own cadence is learned, a full walking score only needs
        // 80% of their baseline; before calibration a fixed cadence is required.
        val fullSpm = if (baselineCadence > 50f) max(55f, 0.8f * baselineCadence) else DEFAULT_FULL_SPM
        lastWalkingScore = smoothstep(cadenceSpm / fullSpm) * freshness

        lastViewScore = phoneInViewScore()

        val raw = (lastWalkingScore * lastViewScore * 100f).coerceIn(0f, 100f)
        val alpha = 1f - exp(-dt / SMOOTHING_TAU_S)
        smoothedConfidence += (raw - smoothedConfidence) * alpha
        _confidence.value = smoothedConfidence.roundToInt().coerceIn(0, 100)
    }

    private fun phoneInViewScore(): Float {
        if (!screenOn) return 0f
        // Higher look-sensitivity widens the "raised toward the face" tilt band.
        val widenDeg = (settings.lookSensitivity - 0.5f) * 30f
        val lo = TILT_BAND_LOW_DEG - widenDeg
        val hi = TILT_BAND_HIGH_DEG + widenDeg
        val tiltScore = when {
            tiltDeg in lo..hi -> 1f
            tiltDeg < lo -> max(0.05f, 1f - (lo - tiltDeg) / TILT_FALLOFF_DEG)
            else -> max(0.05f, 1f - (tiltDeg - hi) / TILT_FALLOFF_DEG)
        }
        val proximityScore = when (proximityNear) {
            true -> 0.05f
            false -> 1f
            null -> PROXIMITY_UNKNOWN_FACTOR
        }
        val view = TILT_WEIGHT * tiltScore + PROXIMITY_WEIGHT * proximityScore
        // Covering the proximity sensor (pocket, against the body) caps the score.
        return if (proximityNear == true) min(view, POCKET_CAP) else view
    }

    private fun currentThreshold(): Float {
        val calibrated = (STEP_THRESHOLD_RATIO * medianAmplitude).coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
        return calibrated * (1.35f - 0.7f * settings.motionSensitivity)
    }

    private fun publishSnapshot(now: Long) {
        _snapshot.value = EngineSnapshot(
            timestampNanos = now,
            confidence = smoothedConfidence,
            walkingScore = lastWalkingScore,
            phoneInViewScore = lastViewScore,
            screenOn = screenOn,
            proximityNear = proximityNear,
            tiltDeg = tiltDeg,
            cadenceSpm = cadenceSpm,
            calibratedThreshold = currentThreshold(),
            medianStepAmplitude = medianAmplitude,
            baselineCadenceSpm = baselineCadence,
            stepCount = stepCount,
            recentSamples = chart.toList(),
            recentSteps = recentSteps.toList(),
        )
    }

    private fun smoothstep(x: Float): Float {
        val t = x.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun medianOf(values: Collection<Float>): Float {
        if (values.isEmpty()) return DEFAULT_INITIAL_AMPLITUDE
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    private fun medianOfLong(values: List<Long>): Long {
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }
}
