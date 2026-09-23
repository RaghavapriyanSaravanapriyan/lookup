package dev.lookup.detection

/**
 * Pure ARGB color math for the warning bar's blue -> amber -> red gradient.
 * Shared by the overlay View and the Compose preview so both render identically.
 */
object OverlayPalette {
    // Vivid, saturated stops chosen for peripheral-vision legibility: deep
    // anchor colors, brighter B endpoint for a subtle left-to-right shift.
    val BLUE_A = 0xFF2563EB.toInt()
    val BLUE_B = 0xFF3B82F6.toInt()
    val AMBER_A = 0xFFF59E0B.toInt()
    val AMBER_B = 0xFFFBBF24.toInt()
    val RED_A = 0xFFDC2626.toInt()
    val RED_B = 0xFFEF4444.toInt()

    /** Confidence (0..100) at which the pulse animation kicks in. */
    const val PULSE_CONFIDENCE = 70f

    /**
     * Gradient endpoint colors for the given confidence. The whole bar shifts
     * through blue -> amber -> red as confidence rises.
     */
    fun colors(confidence: Float): Pair<Int, Int> {
        val c = confidence.coerceIn(0f, 100f)
        return if (c <= 40f) {
            BLUE_A to BLUE_B
        } else if (c <= 70f) {
            val t = (c - 40f) / 30f
            blend(BLUE_A, AMBER_A, t) to blend(BLUE_B, AMBER_B, t)
        } else {
            val t = (c - 70f) / 30f
            blend(AMBER_A, RED_A, t) to blend(AMBER_B, RED_B, t)
        }
    }

    private fun blend(from: Int, to: Int, fraction: Float): Int {
        val t = fraction.coerceIn(0f, 1f)
        fun channel(from: Int, to: Int): Int =
            (((from and 0xFF) + ((to and 0xFF) - (from and 0xFF)) * t).toInt())
        val a = channel(from ushr 24, to ushr 24)
        val r = channel(from ushr 16, to ushr 16)
        val g = channel(from ushr 8, to ushr 8)
        val b = channel(from, to)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
