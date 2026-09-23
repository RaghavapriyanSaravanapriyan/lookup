package dev.lookup.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.lookup.detection.OverlayPalette
import dev.lookup.ui.terminal.Term
import kotlin.math.min

/**
 * compose twin of the overlay warning bar, dressed for the terminal:
 * black rail, hairline frame, blue -> amber -> red fill that shifts
 * with confidence. the only coloured bar in the chrome.
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
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Term.Bg, RoundedCornerShape(8.dp))
            .border(1.dp, Term.Hairline, RoundedCornerShape(8.dp))
            .padding(8.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(15.dp)) {
            val pulseBoost = if (pulsing) 1f + 0.12f * pulse.value else 1f
            val basePx = with(density) { (5 + 7 * (animated / 100f)).dp.toPx() }
            val barH = min(basePx * pulseBoost, size.height)
            val corner = CornerRadius(barH / 2f, barH / 2f)
            val (colorA, colorB) = OverlayPalette.colors(animated)
            // rail
            drawRoundRect(
                color = Term.Grid,
                cornerRadius = corner,
            )
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(Color(colorA), Color(colorB))),
                cornerRadius = corner,
            )
            // gate ticks: 30 / 40 / 70
            for (gate in listOf(30f, 40f, 70f)) {
                val x = size.width * gate / 100f
                drawLine(
                    color = Color.White.copy(alpha = 0.65f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1.5f,
                )
            }
        }
    }
}
