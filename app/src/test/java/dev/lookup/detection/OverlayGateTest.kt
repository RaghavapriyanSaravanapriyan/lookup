package dev.lookup.detection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [OverlayGate] using synthetic confidence ramps on a virtual
 * monotonic clock. Each tick advances the clock by 40 ms (~25 Hz snapshots).
 */
class OverlayGateTest {

    private class Harness {
        val gate = OverlayGate()
        var t = 0L

        fun step(confidence: Float, ticks: Int = 1): Boolean {
            var last = false
            repeat(ticks) {
                t += TICK_NANOS
                last = gate.onConfidence(confidence, t)
            }
            return last
        }
    }

    @Test
    fun staysHiddenBelowThresholdIndefinitely() {
        val h = Harness()
        repeat(100) { assertFalse(h.step(20f)) }
        assertFalse(h.step(39f, ticks = 100))
    }

    @Test
    fun appearsAfterSustainedRise() {
        val h = Harness()
        // t=280 ms: only 240 ms above the show line — below the 250 ms debounce.
        assertFalse(h.step(80f, ticks = 7))
        // t=320 ms: 280 ms sustained — visible.
        assertTrue(h.step(80f))
    }

    @Test
    fun briefSpikeDoesNotAppear() {
        val h = Harness()
        repeat(5) { assertFalse(h.step(80f)) } // 200 ms rise
        repeat(10) { assertFalse(h.step(35f)) } // falls back into the band
        assertFalse(h.step(20f))
    }

    @Test
    fun hysteresisBandHoldsVisibleState() {
        val h = Harness()
        h.step(80f, ticks = 8)
        assertTrue(h.step(80f))
        repeat(125) { assertTrue(h.step(35f)) } // 5 s in the band: still visible
    }

    @Test
    fun disappearsAfterSustainedDrop() {
        val h = Harness()
        h.step(80f, ticks = 8)
        assertTrue(h.step(80f))
        // 9 ticks of 20: at most 360 ms below the hide line — under 400 ms.
        assertTrue(h.step(20f, ticks = 9))
        assertTrue(h.step(20f)) // t=720: 360 ms, still holds
        // t=760: 400 ms sustained — hidden.
        assertFalse(h.step(20f))
    }

    @Test
    fun briefDipBelowHideDoesNotFlicker() {
        val h = Harness()
        h.step(80f, ticks = 8)
        assertTrue(h.step(80f))
        repeat(5) { assertTrue(h.step(20f)) } // 200 ms dip: holds
        repeat(50) { assertTrue(h.step(60f)) } // confidence recovers: still visible
    }

    @Test
    fun riseTimerResetsAfterBandDip() {
        val h = Harness()
        repeat(5) { assertFalse(h.step(80f)) } // 200 ms rise, not yet visible
        assertFalse(h.step(35f, ticks = 3)) // dip into the band: timer resets
        assertFalse(h.step(80f, ticks = 5)) // fresh 200 ms rise: still not visible
        assertTrue(h.step(80f, ticks = 3)) // 320 ms sustained: visible
    }

    @Test
    fun fallTimerResetsAfterBandRise() {
        val h = Harness()
        h.step(80f, ticks = 8)
        assertTrue(h.step(80f))
        assertTrue(h.step(20f, ticks = 5)) // 200 ms drop, not yet hidden
        assertTrue(h.step(35f)) // bounce into the band: timer resets, holds
        assertTrue(h.step(20f, ticks = 9)) // fresh 360 ms drop: still holds
        assertFalse(h.step(20f, ticks = 2)) // 400 ms sustained: hidden
    }

    @Test
    fun boundaryValuesCountTowardsTheirSide() {
        val h = Harness()
        repeat(6) { assertFalse(h.step(OverlayGate.SHOW_THRESHOLD)) }
        assertTrue(h.step(OverlayGate.SHOW_THRESHOLD, ticks = 2))
        repeat(9) { assertTrue(h.step(OverlayGate.HIDE_THRESHOLD)) }
        assertTrue(h.step(OverlayGate.HIDE_THRESHOLD)) // t=720: 360 ms, holds
        assertFalse(h.step(OverlayGate.HIDE_THRESHOLD)) // t=760: 400 ms, hidden
    }

    companion object {
        private const val TICK_NANOS = 40_000_000L
    }
}
