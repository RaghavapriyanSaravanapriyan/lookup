package dev.lookup.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lookup.detection.EngineSnapshot
import dev.lookup.service.DetectionBus
import kotlin.math.roundToInt

/**
 * Live sensor view: confidence readout with the animated bar, evidence
 * sub-scores, the rolling linear-acceleration chart with step markers and the
 * adaptive threshold, and the raw fusion inputs.
 */
@Composable
fun DebugScreen(modifier: Modifier = Modifier) {
    val running by DetectionBus.running.collectAsStateWithLifecycle()
    val snapshot by DetectionBus.snapshot.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        Card {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Confidence", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (!running) {
                        Text(
                            "off \u2014 start protection in Settings",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "${snapshot.confidence.roundToInt()}",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                ConfidenceBar(snapshot.confidence)
                Spacer(Modifier.height(16.dp))
                EvidenceRow("Walking evidence", snapshot.walkingScore)
                EvidenceRow("Phone in view", snapshot.phoneInViewScore)
            }
        }

        Card {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Linear acceleration", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Line: deviation from gravity \u00B7 Dashed: step threshold \u00B7 " +
                        "Marks: detected steps",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                AccelChart(snapshot)
            }
        }

        Card {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Fusion inputs", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                StatGrid(
                    listOf(
                        Stat("Cadence", "${snapshot.cadenceSpm.roundToInt()} spm"),
                        Stat("Tilt", "${snapshot.tiltDeg.roundToInt()}\u00B0"),
                        Stat("Screen", if (snapshot.screenOn) "On" else "Off"),
                        Stat(
                            "Proximity",
                            when (snapshot.proximityNear) {
                                true -> "Near"
                                false -> "Far"
                                null -> "\u2014"
                            },
                        ),
                        Stat("Threshold", "%.2f m/s\u00B2".format(snapshot.calibratedThreshold)),
                        Stat(
                            "Baseline",
                            if (snapshot.baselineCadenceSpm > 0f) {
                                "${snapshot.baselineCadenceSpm.roundToInt()} spm"
                            } else {
                                "\u2014"
                            },
                        ),
                    ),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun EvidenceRow(label: String, value: Float) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                "${(value * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        LinearProgressIndicator(
            progress = { value.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 8.dp),
        )
    }
}

private data class Stat(val label: String, val value: String)

@Composable
private fun StatGrid(stats: List<Stat>) {
    stats.chunked(2).forEach { rowStats ->
        Row(modifier = Modifier.fillMaxWidth()) {
            rowStats.forEach { stat ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 6.dp),
                ) {
                    Text(
                        stat.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(stat.value, style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (rowStats.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun AccelChart(snapshot: EngineSnapshot) {
    val density = LocalDensity.current
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelPaint = remember(density, labelColor) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = with(density) { 10.dp.toPx() }
            color = labelColor.toArgb()
        }
    }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
    ) {
        val windowNanos = 10_000_000_000L
        val now = snapshot.timestampNanos
        val leftEdge = now - windowNanos
        val maxY = 4.5f
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        fun xOf(t: Long) = ((t - leftEdge).toFloat() / windowNanos) * w
        fun yOf(value: Float) = h - (value / maxY) * h

        // Adaptive step threshold.
        val thresholdY = yOf(snapshot.calibratedThreshold)
        drawLine(
            color = Color(0xFFF59E0B),
            start = Offset(0f, thresholdY),
            end = Offset(w, thresholdY),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
        )

        // Step markers.
        val markerColor = Color(0x402E6BFF)
        snapshot.recentSteps.asSequence()
            .filter { it >= leftEdge }
            .forEach { t ->
                val x = xOf(t)
                drawLine(
                    color = markerColor,
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                    strokeWidth = 4f,
                    cap = StrokeCap.Round,
                )
            }

        // Deviation polyline.
        val points = snapshot.recentSamples.asSequence()
            .filter { it.timestampNanos >= leftEdge }
            .toList()
        if (points.size > 1) {
            val path = Path()
            points.forEachIndexed { index, point ->
                val x = xOf(point.timestampNanos)
                val y = yOf(point.deviation)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path,
                color = Color(0xFF2E6BFF),
                style = Stroke(width = 3f, cap = StrokeCap.Round),
            )
        }

        // Axis annotations.
        drawContext.canvas.nativeCanvas.apply {
            drawText("m/s\u00B2", 6f, 16f, labelPaint)
            drawText(
                "threshold %.2f".format(snapshot.calibratedThreshold),
                6f,
                thresholdY - 8f,
                labelPaint,
            )
            drawText("last 10 s", w - 56f, h - 8f, labelPaint)
        }
    }
}
