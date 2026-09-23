package dev.lookup.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionEngineTest {

    // ------------------------------------------------------------------
    // Scenario: standing still, phone flat on a table.
    // ------------------------------------------------------------------
    @Test
    fun standingStillPhoneFlatStaysQuiet() {
        val scene = Scene()
        scene.engine.onScreenStateChanged(true)
        scene.engine.onProximityChanged(false)

        val snap = scene.play(20.0, FrameConfig(tiltDeg = 2.0, noiseSigma = 0.05))

        assertEquals(0, snap.stepCount)
        assertTrue("cadence was ${snap.cadenceSpm}", snap.cadenceSpm < 1f)
        assertTrue("confidence was ${scene.engine.confidence.value}", scene.engine.confidence.value < 15)
    }

    // ------------------------------------------------------------------
    // Scenario: standing still, phone raised in hand but not moving.
    // ------------------------------------------------------------------
    @Test
    fun standingStillPhoneRaisedStaysQuiet() {
        val scene = Scene()
        scene.engine.onScreenStateChanged(true)
        scene.engine.onProximityChanged(false)

        val snap = scene.play(20.0, FrameConfig(tiltDeg = 85.0, noiseSigma = 0.08))

        assertEquals(0, snap.stepCount)
        assertTrue("confidence was ${scene.engine.confidence.value}", scene.engine.confidence.value < 15)
        assertTrue("view score was ${snap.phoneInViewScore}", snap.phoneInViewScore > 0.85f)
    }

    // ------------------------------------------------------------------
    // Scenario: walking with the screen off (phone in hand at the side).
    // Gait must be detected but no warning may be raised.
    // ------------------------------------------------------------------
    @Test
    fun walkingWithScreenOffDetectsGaitWithoutWarning() {
        val scene = Scene()
        scene.engine.onScreenStateChanged(false)
        scene.engine.onProximityChanged(false)

        scene.play(3.0, FrameConfig(tiltDeg = 82.0, noiseSigma = 0.08)) // settle
        val snap = scene.play(
            14.0,
            FrameConfig(
                cadenceSpm = 112.0,
                stepAmplitude = 2.2,
                tiltDeg = 82.0,
                tiltSwayDeg = 3.0,
                noiseSigma = 0.08,
            ),
        )

        assertTrue("only ${snap.stepCount} steps", snap.stepCount > 20)
        assertTrue("cadence was ${snap.cadenceSpm}", snap.cadenceSpm in 100f..125f)
        assertTrue("walking score was ${snap.walkingScore}", snap.walkingScore > 0.6f)
        assertTrue("confidence was ${scene.engine.confidence.value}", scene.engine.confidence.value < 10)
    }

    // ------------------------------------------------------------------
    // Scenario: phone in pocket while walking (proximity near, screen
    // against the body, worst case screen on). No warning.
    // ------------------------------------------------------------------
    @Test
    fun walkingWithPhoneInPocketStaysQuiet() {
        val scene = Scene()
        scene.engine.onScreenStateChanged(true)
        scene.engine.onProximityChanged(true)

        scene.play(3.0, FrameConfig(tiltDeg = 168.0, noiseSigma = 0.15)) // settle
        val snap = scene.play(
            15.0,
            FrameConfig(
                cadenceSpm = 110.0,
                stepAmplitude = 2.8,
                tiltDeg = 168.0,
                azimuthDeg = 20.0,
                noiseSigma = 0.15,
            ),
        )

        assertTrue("only ${snap.stepCount} steps", snap.stepCount > 20)
        assertTrue("view score was ${snap.phoneInViewScore}", snap.phoneInViewScore < 0.15f)
        assertTrue("confidence was ${scene.engine.confidence.value}", scene.engine.confidence.value < 15)
    }

    // ------------------------------------------------------------------
    // Scenario: sitting with the phone raised (reading/typing), including
    // small hand gestures. No gait, no warning.
    // ------------------------------------------------------------------
    @Test
    fun sittingWithPhoneRaisedStaysQuiet() {
        val scene = Scene()
        scene.engine.onScreenStateChanged(true)
        scene.engine.onProximityChanged(false)

        val snap = scene.play(
            25.0,
            FrameConfig(
                tiltDeg = 78.0,
                noiseSigma = 0.12,
                gestureBumps = true,
            ),
        )

        assertTrue("unexpected step count ${snap.stepCount}", snap.stepCount <= 1)
        assertTrue("cadence was ${snap.cadenceSpm}", snap.cadenceSpm < 20f)
        assertTrue("confidence was ${scene.engine.confidence.value}", scene.engine.confidence.value < 15)
    }

    // ------------------------------------------------------------------
    // The danger case: walking while looking at the phone.
    // ------------------------------------------------------------------
    @Test
    fun distractedWalkingScoresHigh() {
        val scene = Scene()
        scene.engine.onScreenStateChanged(true)
        scene.engine.onProximityChanged(false)

        scene.play(3.0, FrameConfig(tiltDeg = 82.0, tiltSwayDeg = 3.0, noiseSigma = 0.08)) // settle
        val snap = scene.play(
            15.0,
            FrameConfig(
                cadenceSpm = 110.0,
                stepAmplitude = 2.2,
                tiltDeg = 82.0,
                tiltSwayDeg = 3.0,
                noiseSigma = 0.08,
            ),
        )

        assertTrue("cadence was ${snap.cadenceSpm}", snap.cadenceSpm in 100f..120f)
        assertTrue("view score was ${snap.phoneInViewScore}", snap.phoneInViewScore > 0.85f)
        assertTrue("walking score was ${snap.walkingScore}", snap.walkingScore > 0.8f)
        assertTrue(
            "confidence was ${scene.engine.confidence.value}",
            scene.engine.confidence.value > 70,
        )
    }

    // ------------------------------------------------------------------
    // Stopping the gait (waiting for a light, phone still raised) must
    // let the confidence decay instead of latching.
    // ------------------------------------------------------------------
    @Test
    fun confidenceDecaysAfterUserStopsWalking() {
        val scene = Scene()
        scene.engine.onScreenStateChanged(true)
        scene.engine.onProximityChanged(false)

        scene.play(3.0, FrameConfig(tiltDeg = 82.0, tiltSwayDeg = 3.0, noiseSigma = 0.08))
        scene.play(
            12.0,
            FrameConfig(cadenceSpm = 110.0, stepAmplitude = 2.2, tiltDeg = 82.0, tiltSwayDeg = 3.0),
        )
        assertTrue(
            "expected high confidence while distracted, got ${scene.engine.confidence.value}",
            scene.engine.confidence.value > 70,
        )

        val snap = scene.play(8.0, FrameConfig(tiltDeg = 82.0, noiseSigma = 0.08))

        assertTrue("walking score was ${snap.walkingScore}", snap.walkingScore < 0.1f)
        assertTrue(
            "confidence did not decay: ${scene.engine.confidence.value}",
            scene.engine.confidence.value < 25,
        )
    }

    // ------------------------------------------------------------------
    // Rolling self-calibration: a strong gait raises the step threshold,
    // an idle period relaxes it, and a weak gait is still detected.
    // ------------------------------------------------------------------
    @Test
    fun calibrationAdaptsThresholdToGaitStrength() {
        val scene = Scene()
        scene.engine.onScreenStateChanged(true)
        scene.engine.onProximityChanged(false)

        val raised = FrameConfig(tiltDeg = 82.0, tiltSwayDeg = 3.0, noiseSigma = 0.08)

        scene.play(3.0, raised)
        val strong = scene.play(
            20.0,
            FrameConfig(cadenceSpm = 110.0, stepAmplitude = 2.6, tiltDeg = 82.0, tiltSwayDeg = 3.0),
        )
        val strongThreshold = strong.calibratedThreshold
        // The low-passed deviation peak is a fraction of the raw step amplitude,
        // so the threshold settles proportionally below 0.45 * 2.6.
        assertTrue("threshold after strong gait was $strongThreshold", strongThreshold > 0.78f)
        assertTrue(
            "median amplitude after strong gait was ${strong.medianStepAmplitude}",
            strong.medianStepAmplitude > 1.65f,
        )

        // Idle period: threshold decays back toward the default.
        scene.play(12.0, raised)

        // Weak gait: must still be detected and pull the threshold back down.
        val stepsBefore = scene.engine.snapshot.value.stepCount
        val weak = scene.play(
            15.0,
            FrameConfig(cadenceSpm = 105.0, stepAmplitude = 1.2, tiltDeg = 82.0, tiltSwayDeg = 3.0),
        )

        assertTrue(
            "weak gait not detected (${weak.stepCount - stepsBefore} new steps)",
            weak.stepCount - stepsBefore >= 20,
        )
        assertTrue(
            "threshold did not re-calibrate down: ${weak.calibratedThreshold}",
            weak.calibratedThreshold < strongThreshold,
        )
        assertTrue(
            "confidence on weak gait was ${scene.engine.confidence.value}",
            scene.engine.confidence.value > 60,
        )
    }

    // ------------------------------------------------------------------
    // Baseline cadence learned while walking relaxes the full-score
    // cadence; resetting calibration wipes it.
    // ------------------------------------------------------------------
    @Test
    fun resetCalibrationClearsLearnedGait() {
        val scene = Scene()
        scene.engine.onScreenStateChanged(true)
        scene.engine.onProximityChanged(false)

        scene.play(3.0, FrameConfig(tiltDeg = 82.0, tiltSwayDeg = 3.0, noiseSigma = 0.08))
        val walking = scene.play(
            12.0,
            FrameConfig(cadenceSpm = 110.0, stepAmplitude = 2.2, tiltDeg = 82.0, tiltSwayDeg = 3.0),
        )
        assertTrue("baseline not learned: ${walking.baselineCadenceSpm}", walking.baselineCadenceSpm > 50f)

        scene.engine.resetCalibration()
        val snap = scene.play(0.1, FrameConfig(tiltDeg = 82.0, noiseSigma = 0.08))

        assertEquals(0f, snap.baselineCadenceSpm, 0.01f)
        assertEquals(0f, snap.cadenceSpm, 0.01f)
        assertEquals(
            DetectionEngine.DEFAULT_INITIAL_THRESHOLD * (1.35f - 0.7f * 0.5f),
            snap.calibratedThreshold,
            0.01f,
        )

        scene.play(5.0, FrameConfig(tiltDeg = 82.0, noiseSigma = 0.08))
        assertTrue(
            "confidence after reset was ${scene.engine.confidence.value}",
            scene.engine.confidence.value < 15,
        )
    }

    // ------------------------------------------------------------------
    // Screen state gates the warning: screen off kills it instantly.
    // ------------------------------------------------------------------
    @Test
    fun screenOffSuppressesAndRestoringScreenRecovers() {
        val scene = Scene()
        scene.engine.onScreenStateChanged(true)
        scene.engine.onProximityChanged(false)

        val gait = FrameConfig(cadenceSpm = 110.0, stepAmplitude = 2.2, tiltDeg = 82.0, tiltSwayDeg = 3.0)
        scene.play(3.0, FrameConfig(tiltDeg = 82.0, tiltSwayDeg = 3.0, noiseSigma = 0.08))
        scene.play(12.0, gait)
        assertTrue(
            "expected high confidence, got ${scene.engine.confidence.value}",
            scene.engine.confidence.value > 70,
        )

        scene.engine.onScreenStateChanged(false)
        scene.play(4.0, gait)
        assertTrue(
            "screen off should suppress, got ${scene.engine.confidence.value}",
            scene.engine.confidence.value < 15,
        )

        scene.engine.onScreenStateChanged(true)
        scene.play(4.0, gait)
        assertTrue(
            "confidence did not recover: ${scene.engine.confidence.value}",
            scene.engine.confidence.value > 60,
        )
    }

    // ------------------------------------------------------------------
    // Sensor gaps (delivery stall) must not corrupt the tracking state.
    // ------------------------------------------------------------------
    @Test
    fun longSensorGapDoesNotLatchWarning() {
        val scene = Scene()
        scene.engine.onScreenStateChanged(true)
        scene.engine.onProximityChanged(false)

        scene.play(3.0, FrameConfig(tiltDeg = 82.0, tiltSwayDeg = 3.0, noiseSigma = 0.08))
        scene.play(
            12.0,
            FrameConfig(cadenceSpm = 110.0, stepAmplitude = 2.2, tiltDeg = 82.0, tiltSwayDeg = 3.0),
        )
        assertTrue(scene.engine.confidence.value > 70)

        // Simulate a 30 s sensor delivery gap.
        scene.tNanos += 30_000_000_000L
        val snap = scene.play(3.0, FrameConfig(tiltDeg = 82.0, noiseSigma = 0.08))

        assertTrue(
            "confidence latched after gap: ${scene.engine.confidence.value}",
            scene.engine.confidence.value < 15,
        )
        assertTrue(snap.screenOn)
    }

    // ------------------------------------------------------------------
    // Out-of-order timestamps are ignored.
    // ------------------------------------------------------------------
    @Test
    fun outOfOrderTimestampsAreIgnored() {
        val engine = DetectionEngine()
        engine.onAccelerometer(0f, 0f, 9.81f, 1_000_000_000L)
        engine.onAccelerometer(5f, 0f, 9.81f, 500_000_000L) // older, must be dropped
        engine.onAccelerometer(0f, 0f, 9.81f, 1_000_000_000L) // duplicate, must be dropped
        assertEquals(0, engine.snapshot.value.stepCount)
        assertEquals(0, engine.confidence.value)
    }
}
