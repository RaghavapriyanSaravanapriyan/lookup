package dev.lookup.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.lookup.detection.OverlayPalette
import kotlin.math.min

/**
 * Compose twin of the overlay warning bar: blue -> amber -> red gradient that
 * shifts with confidence, drawn 5 dp -> 12 dp inside a fixed 15 dp frame —
 * the same geometry as the real overlay. Full opacity throughout; at high
 * confidence it pulses via a slight height swell.
 * Used by the settings/dashboard preview and the debug screen.
 */
@Composable
fun ConfidenceBar(confidence: Float, modifier: Modifier = Modifier) {
    val target = confidence.coerceIn(0f, 100f)
    val animated by animateFloatAsState(target, tween(180), label = "confidence")
    val pulse = remember { Animatable(0f) }
    val pulsing = animated >= OverlayPalette.PULSE_CONFIDENCE
    LaunchedEffect(pulsing) {
        if (pulsing) {
            while (true) {
                pulse.animateTo(1f, tween(550, easing = LinearEasing))
                pulse.animateTo(0f, tween(550, easing = LinearEasing))
            }
        } else {
            pulse.snapTo(0f)
        }
    }
    val density = LocalDensity.current
    Canvas(modifier.height(15.dp)) {
        val pulseBoost = if (pulsing) 1f + 0.12f * pulse.value else 1f
        val basePx = with(density) { (5 + 7 * (animated / 100f)).dp.toPx() }
        val barH = min(basePx * pulseBoost, size.height)
        val corner = CornerRadius(barH / 2f, barH / 2f)
        val (colorA, colorB) = OverlayPalette.colors(animated)
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(Color(colorA), Color(colorB))),
            cornerRadius = corner,
        )
    }
}
