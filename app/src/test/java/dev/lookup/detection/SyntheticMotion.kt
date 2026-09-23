package dev.lookup.detection

import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Synthetic accelerometer-frame generator for unit tests. Produces the raw
 * (gravity + linear motion + noise) signal a real sensor would report.
 *
 * Orientation is expressed as a tilt angle away from "flat face-up"
 * (0 deg = screen facing the sky, 90 deg = upright facing the user,
 * 180 deg = face-down) plus an azimuth around the screen normal.
 */
class FrameConfig(
    /** Steps/min; 0 disables gait motion. */
    val cadenceSpm: Double = 0.0,
    /** Peak linear-acceleration deviation of one step, m/s^2. */
    val stepAmplitude: Double = 2.2,
    val tiltDeg: Double = 90.0,
    val azimuthDeg: Double = 0.0,
    /** Slow side-to-side sway of the device while walking, +/- degrees. */
    val tiltSwayDeg: Double = 0.0,
    /** Per-axis gaussian noise sigma, m/s^2. */
    val noiseSigma: Double = 0.06,
    /** Occasional low-amplitude hand gestures (reading/typing while sitting). */
    val gestureBumps: Boolean = false,
)

/**
 * Drives a [DetectionEngine] with 50 Hz synthetic frames while keeping a
 * monotonic virtual clock and a continuous tick index (for gait phase).
 */
class Scene(val engine: DetectionEngine = DetectionEngine()) {
    var tNanos = 10_000_000_000L
    var tick = 0
        private set
    private val rnd = Random(42)

    fun play(seconds: Double, cfg: FrameConfig): EngineSnapshot {
        val frames = (seconds * HZ).roundToInt()
        repeat(frames) {
            tNanos += DT_NANOS
            val (x, y, z) = frame(cfg)
            engine.onAccelerometer(x, y, z, tNanos)
            tick++
        }
        return engine.snapshot.value
    }

    fun frame(cfg: FrameConfig): Triple<Float, Float, Float> {
        val swayDeg = if (cfg.tiltSwayDeg > 0) {
            cfg.tiltSwayDeg * sin(2 * PI * SWAY_HZ * tick / HZ)
        } else {
            0.0
        }
        val tilt = Math.toRadians(cfg.tiltDeg + swayDeg)
        val azimuth = Math.toRadians(cfg.azimuthDeg)
        val gx = GRAVITY * sin(tilt) * cos(azimuth)
        val gy = GRAVITY * sin(tilt) * sin(azimuth)
        val gz = GRAVITY * cos(tilt)

        var deviation = 0.0
        if (cfg.cadenceSpm > 0) {
            val periodTicks = HZ * 60.0 / cfg.cadenceSpm
            val phase = (tick % periodTicks) / periodTicks
            if (phase in STEP_CENTER - STEP_WIDTH / 2..STEP_CENTER + STEP_WIDTH / 2) {
                deviation = cfg.stepAmplitude *
                    sin(PI * (phase - (STEP_CENTER - STEP_WIDTH / 2)) / STEP_WIDTH)
            }
        }
        if (cfg.gestureBumps) {
            val gestureTick = tick % GESTURE_INTERVAL_TICKS
            if (gestureTick < GESTURE_BUMP_TICKS) {
                deviation = max(
                    deviation,
                    GESTURE_AMPLITUDE * sin(PI * gestureTick / GESTURE_BUMP_TICKS),
                )
            }
        }

        val nx = rnd.nextGaussian() * cfg.noiseSigma
        val ny = rnd.nextGaussian() * cfg.noiseSigma
        val nz = rnd.nextGaussian() * cfg.noiseSigma
        return Triple(
            (gx + deviation + nx).toFloat(),
            (gy + ny).toFloat(),
            (gz + nz).toFloat(),
        )
    }

    companion object {
        const val HZ = 50
        const val DT_NANOS = 1_000_000_000L / HZ
        const val GRAVITY = 9.81
        private const val STEP_CENTER = 0.30
        private const val STEP_WIDTH = 0.30
        private const val SWAY_HZ = 0.4
        private const val GESTURE_INTERVAL_TICKS = 150
        private const val GESTURE_BUMP_TICKS = 12
        private const val GESTURE_AMPLITUDE = 0.30
    }
}
