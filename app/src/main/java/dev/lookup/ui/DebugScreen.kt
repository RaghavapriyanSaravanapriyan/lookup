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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lookup.R
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
    val overlayActive by DetectionBus.overlayActive.collectAsStateWithLifecycle()

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
                    Text(
                        stringResource(R.string.debug_confidence),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (!running) {
                        Text(
                            stringResource(R.string.debug_off_hint),
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
                EvidenceRow(
                    stringResource(R.string.debug_evidence_walking),
                    snapshot.walkingScore,
                )
                EvidenceRow(
                    stringResource(R.string.debug_evidence_view),
                    snapshot.phoneInViewScore,
                )
                EvidenceRow(
                    stringResource(R.string.debug_evidence_bar),
                    if (overlayActive) 1f else 0f,
                )
            }
        }

        Card {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(stringResource(R.string.debug_chart_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.debug_chart_legend),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                AccelChart(snapshot)
            }
        }

        Card {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(stringResource(R.string.debug_inputs_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                StatGrid(
                    listOf(
                        Stat(
                            stringResource(R.string.debug_stat_cadence),
                            stringResource(
                                R.string.debug_stat_cadence_value,
                                snapshot.cadenceSpm.roundToInt(),
                            ),
                        ),
                        Stat(
                            stringResource(R.string.debug_stat_tilt),
                            stringResource(
                                R.string.debug_stat_tilt_value,
                                snapshot.tiltDeg.roundToInt(),
                            ),
                        ),
                        Stat(
                            stringResource(R.string.debug_stat_screen),
                            if (snapshot.screenOn) {
                                stringResource(R.string.debug_on)
                            } else {
                                stringResource(R.string.debug_off)
                            },
                        ),
                        Stat(
                            stringResource(R.string.debug_stat_proximity),
                            when (snapshot.proximityNear) {
                                true -> stringResource(R.string.debug_near)
                                false -> stringResource(R.string.debug_far)
                                null -> "\u2014"
                            },
                        ),
                        Stat(
                            stringResource(R.string.debug_stat_threshold),
                            stringResource(
                                R.string.debug_stat_threshold_value,
                                snapshot.calibratedThreshold,
                            ),
                        ),
                        Stat(
                            stringResource(R.string.debug_stat_baseline),
                            if (snapshot.baselineCadenceSpm > 0f) {
                                stringResource(
                                    R.string.debug_stat_baseline_value,
                                    snapshot.baselineCadenceSpm.roundToInt(),
                                )
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
    val unitLabel = stringResource(R.string.debug_chart_unit)
    val thresholdLabel = stringResource(
        R.string.debug_chart_threshold_label,
        snapshot.calibratedThreshold,
    )
    val windowLabel = stringResource(R.string.debug_chart_window)
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
            drawText(unitLabel, 6f, 16f, labelPaint)
            drawText(
                thresholdLabel,
                6f,
                thresholdY - 8f,
                labelPaint,
            )
            drawText(windowLabel, w - 56f, h - 8f, labelPaint)
        }
    }
}
