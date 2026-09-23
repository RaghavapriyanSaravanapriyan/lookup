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
import androidx.compose.ui.unit.dp
import dev.lookup.detection.OverlayPalette

/**
 * Compose twin of the overlay warning bar: blue -> amber -> red gradient that
 * shifts with confidence, grows a little, and pulses at high confidence.
 * Used by the settings preview and the debug screen.
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
    val height = (3 + 6 * (animated / 100f)).dp
    val (colorA, colorB) = OverlayPalette.colors(animated)
    Canvas(modifier.height(height)) {
        val corner = CornerRadius(size.height / 2f, size.height / 2f)
        val alpha = if (pulsing) 0.78f + 0.22f * pulse.value else 1f
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(Color(colorA), Color(colorB))),
            cornerRadius = corner,
            alpha = alpha,
        )
    }
}
