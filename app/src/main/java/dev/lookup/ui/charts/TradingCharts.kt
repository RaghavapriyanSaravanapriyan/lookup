package dev.lookup.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lookup.detection.EngineSnapshot
import dev.lookup.ui.terminal.Term
import dev.lookup.ui.terminal.tapeColorFor
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * the tape: a super-fast trading-style confidence line.
 * colour lives here and only here. everything else stays ink.
 */
@Composable
fun ConfidenceTape(
    points: List<Float>,
    modifier: Modifier = Modifier,
    heightDp: Int = 148,
    showBands: Boolean = true,
) {
    val density = LocalDensity.current
    val labelPaint = remember(density) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = 10f * density.density
            color = Term.Faint.toArgb()
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .background(Term.Bg),
    ) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        fun yOf(v: Float) = h - (v.coerceIn(0f, 100f) / 100f) * (h - 18f) - 4f

        // grid — faint ledger lines
        val grid = Term.Grid
        for (frac in listOf(0.25f, 0.5f, 0.75f)) {
            val y = h * frac
            drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }
        val cols = 6
        for (i in 1 until cols) {
            val x = w * i / cols
            drawLine(grid, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
        }

        if (showBands) {
            // gate bands: amber wash above 40, red wash above 70
            drawRect(
                color = Color(0xFFFBBF24).copy(alpha = 0.05f),
                topLeft = Offset(0f, yOf(70f)),
                size = androidx.compose.ui.geometry.Size(w, yOf(40f) - yOf(70f)),
            )
            drawRect(
                color = Color(0xFFEF4444).copy(alpha = 0.07f),
                topLeft = Offset(0f, 0f),
                size = androidx.compose.ui.geometry.Size(w, yOf(70f)),
            )
            val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
            drawLine(Color(0xFF52525B), Offset(0f, yOf(40f)), Offset(w, yOf(40f)), 1f, pathEffect = dash)
            drawLine(Color(0xFF52525B), Offset(0f, yOf(30f)), Offset(w, yOf(30f)), 1f, pathEffect = dash)
            drawLine(Term.Amber.copy(alpha = 0.7f), Offset(0f, yOf(70f)), Offset(w, yOf(70f)), 1f, pathEffect = dash)
        }

        if (points.size > 1) {
            val n = points.size
            fun xOf(i: Int) = (i.toFloat() / (n - 1)) * w
            // area fill under the line, faint white so colour stays on the stroke
            val area = Path()
            area.moveTo(xOf(0), h)
            points.forEachIndexed { i, v -> area.lineTo(xOf(i), yOf(v)) }
            area.lineTo(xOf(n - 1), h)
            area.close()
            drawPath(
                area,
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.0f)),
                ),
            )
            // segmented stroke: blue -> amber -> red, tick by tick
            for (i in 0 until n - 1) {
                val a = points[i]
                val b = points[i + 1]
                drawLine(
                    color = tapeColorFor((a + b) / 2f),
                    start = Offset(xOf(i), yOf(a)),
                    end = Offset(xOf(i + 1), yOf(b)),
                    strokeWidth = 4.5f,
                    cap = StrokeCap.Round,
                )
            }
            // white core highlight — the "super fast" sheen
            val core = Path()
            points.forEachIndexed { i, v ->
                if (i == 0) core.moveTo(xOf(i), yOf(v)) else core.lineTo(xOf(i), yOf(v))
            }
            drawPath(core, Color.White.copy(alpha = 0.55f), style = Stroke(width = 1.2f, cap = StrokeCap.Round))

            // last-tick glow
            val last = points.last()
            val lx = xOf(n - 1)
            val ly = yOf(last)
            drawCircle(tapeColorFor(last).copy(alpha = 0.25f), 14f, Offset(lx - 2f, ly))
            drawCircle(Color.White, 4.5f, Offset(lx - 2f, ly))
            drawCircle(tapeColorFor(last), 3f, Offset(lx - 2f, ly))
        } else {
            drawLine(Term.Faint, Offset(0f, yOf(0f)), Offset(w, yOf(0f)), 2f)
        }

        drawContext.canvas.nativeCanvas.apply {
            drawText("100", 4f, 12f, labelPaint)
            drawText("70 PULSE", 4f, yOf(70f) - 4f, labelPaint)
            drawText("40 SHOW", 4f, yOf(40f) - 4f, labelPaint)
            drawText("0", 4f, h - 4f, labelPaint)
        }
    }
}

/**
 * remembers a rolling tape of confidence ticks.
 * live when the service runs; a synthetic demo feed when halted
 * so the terminal never looks dead.
 */
@Composable
fun rememberConfidenceTape(
    snapshot: EngineSnapshot,
    running: Boolean,
    maxPoints: Int = 140,
): List<Float> {
    var tape by remember { mutableStateOf(listOf<Float>()) }
    // live feed
    LaunchedEffect(snapshot.timestampNanos, running) {
        if (running) {
            val next = (tape + snapshot.confidence).takeLast(maxPoints)
            tape = next
        }
    }
    // demo feed — fast synthetic micro-ticks when halted
    LaunchedEffect(running) {
        if (!running) {
            var t = 0.0
            while (true) {
                // doomscroll-like random walk with regimes: calm, omen, red
                val regime = (t / 90.0) % 3.0
                val base = when {
                    regime < 1.0 -> 18 + 12 * kotlin.math.sin(t / 9.0)
                    regime < 2.0 -> 48 + 16 * kotlin.math.sin(t / 5.0)
                    else -> 74 + 14 * kotlin.math.sin(t / 3.0)
                }
                val jitter = (Math.random().toFloat() - 0.5f) * 9f
                val v = (base + jitter).toFloat().coerceIn(2f, 98f)
                tape = (tape + v).takeLast(maxPoints)
                t += 1.0
                delay(90)
            }
        } else {
            // seed the tape so the chart isn't empty on arm
            if (tape.isEmpty()) tape = List(maxPoints / 4) { 4f }
        }
    }
    return tape
}

/** high-frequency motion floor: white deviation line, amber threshold, step volume. */
@Composable
fun AccelFloor(
    snapshot: EngineSnapshot,
    modifier: Modifier = Modifier,
    heightDp: Int = 132,
) {
    val density = LocalDensity.current
    val labelPaint = remember(density) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = 10f * density.density
            color = Term.Faint.toArgb()
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .background(Term.Bg),
    ) {
        val windowNanos = 10_000_000_000L
        val now = snapshot.timestampNanos
        val leftEdge = if (now == 0L) 0L else now - windowNanos
        val maxY = 4.5f
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        fun xOf(t: Long) = if (now == 0L) 0f else ((t - leftEdge).toFloat() / windowNanos) * w
        fun yOf(v: Float) = h - 14f - (v.coerceIn(0f, maxY) / maxY) * (h - 28f)

        val grid = Term.Grid
        for (i in 1..3) {
            val y = h * i / 4f
            drawLine(grid, Offset(0f, y), Offset(w, y), 1f)
        }
        for (i in 1 until 6) {
            val x = w * i / 6f
            drawLine(grid, Offset(x, 0f), Offset(x, h), 1f)
        }

        // step volume bars along the floor
        snapshot.recentSteps.filter { it >= leftEdge }.forEach { t ->
            val x = xOf(t)
            drawLine(
                Color.White.copy(alpha = 0.28f),
                Offset(x, h - 14f),
                Offset(x, h - 34f),
                strokeWidth = 5f,
                cap = StrokeCap.Round,
            )
        }

        // threshold — the only amber in this panel
        val ty = yOf(snapshot.calibratedThreshold)
        drawLine(
            Term.Amber,
            Offset(0f, ty),
            Offset(w, ty),
            2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
        )

        val pts = snapshot.recentSamples.filter { it.timestampNanos >= leftEdge }
        if (pts.size > 1 && now != 0L) {
            val path = Path()
            pts.forEachIndexed { i, p ->
                val x = xOf(p.timestampNanos)
                val y = yOf(p.deviation)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            // glow underlay then white core — reads fast at a glance
            drawPath(path, Color.White.copy(alpha = 0.18f), style = Stroke(width = 7f, cap = StrokeCap.Round))
            drawPath(path, Color.White, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
        } else if (now == 0L) {
            // idle shimmer when the feed is halted
            drawLine(Term.Faint, Offset(0f, h / 2f), Offset(w, h / 2f), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
        }

        drawContext.canvas.nativeCanvas.apply {
            drawText("m/s²", 4f, 12f, labelPaint)
            drawText("THR ${"%.2f".format(snapshot.calibratedThreshold)}", 4f, ty - 5f, labelPaint)
            drawText("10s", w - 26f, h - 4f, labelPaint)
        }
    }
}

/** scrolling quote tape across the top of the terminal. pure mono, no colour. */
@Composable
fun TickerTape(
    snapshot: EngineSnapshot,
    running: Boolean,
    overlayActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val conf = snapshot.confidence
    val arrow = if (conf >= 70f) "▲" else if (conf >= 40f) "●" else "▽"
    val text = buildString {
        repeat(3) {
            append("LKUP ${"%.2f".format(conf)} $arrow  ")
            append("WALK ${(snapshot.walkingScore * 100).roundToInt()}%  ")
            append("VIEW ${(snapshot.phoneInViewScore * 100).roundToInt()}%  ")
            append("CAD ${snapshot.cadenceSpm.roundToInt()}SPM  ")
            append("TILT ${snapshot.tiltDeg.roundToInt()}°  ")
            append("THR ${"%.2f".format(snapshot.calibratedThreshold)}  ")
            append("N=${snapshot.stepCount}  ")
            append(if (running) (if (overlayActive) "BAR:ON  " else "BAR:ARMED  ") else "FEED:HALTED  ")
            append("···  ")
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Term.Bg)
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = text,
            fontFamily = Term.Mono,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            color = Term.Dim,
            maxLines = 1,
            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
        )
    }
}
