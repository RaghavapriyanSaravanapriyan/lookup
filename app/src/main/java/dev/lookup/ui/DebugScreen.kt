package dev.lookup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lookup.R
import dev.lookup.service.DetectionBus
import dev.lookup.ui.charts.AccelFloor
import dev.lookup.ui.charts.ConfidenceTape
import dev.lookup.ui.charts.TickerTape
import dev.lookup.ui.charts.rememberConfidenceTape
import dev.lookup.ui.terminal.LiveDot
import dev.lookup.ui.terminal.Panel
import dev.lookup.ui.terminal.QuoteCell
import dev.lookup.ui.terminal.SectionRow
import dev.lookup.ui.terminal.StatCell
import dev.lookup.ui.terminal.Term
import dev.lookup.ui.terminal.oracleLine
import dev.lookup.ui.terminal.tapeColorFor
import dev.lookup.ui.terminal.zoneLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * the scrying floor: forensics for the terminally curious.
 * three live panes, an event ledger, and every fusion input —
 * still black & white, still only the tapes in colour.
 */
@Composable
fun DebugScreen(modifier: Modifier = Modifier) {
    val running by DetectionBus.running.collectAsStateWithLifecycle()
    val snapshot by DetectionBus.snapshot.collectAsStateWithLifecycle()
    val overlayActive by DetectionBus.overlayActive.collectAsStateWithLifecycle()
    val tape = rememberConfidenceTape(snapshot, running)
    val conf = if (running) snapshot.confidence else (tape.lastOrNull() ?: 0f)
    val zone = zoneLabel(conf)
    val tapeColor = tapeColorFor(conf)

    var ledger by remember { mutableStateOf(listOf("boot · the oracle opens one eye")) }
    fun stamp() = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
    fun push(msg: String) {
        ledger = (listOf("${stamp()}  $msg") + ledger).take(24)
    }

    // ledger: bar transitions are the market events of this terminal
    var prevBar by remember { mutableStateOf(overlayActive) }
    LaunchedEffect(overlayActive, running) {
        if (overlayActive != prevBar) {
            prevBar = overlayActive
            if (running) push(if (overlayActive) "BAR ATTACHED @ ${conf.roundToInt()} — omen confirmed" else "BAR DETACHED — air clear")
        }
    }
    var prevSteps by remember { mutableStateOf(snapshot.stepCount) }
    LaunchedEffect(snapshot.stepCount) {
        if (snapshot.stepCount > prevSteps) {
            val n = snapshot.stepCount - prevSteps
            prevSteps = snapshot.stepCount
            if (running && n > 0) push("STEP ×${snapshot.stepCount} · cad ${snapshot.cadenceSpm.roundToInt()}spm")
        } else if (snapshot.stepCount < prevSteps) {
            prevSteps = snapshot.stepCount
            push("CAL RESET — the oracle forgets your gait")
        }
    }
    var prevZone by remember { mutableStateOf(zone) }
    LaunchedEffect(zone) {
        if (zone != prevZone) {
            prevZone = zone
            if (running) push("ZONE → $zone @ ${conf.roundToInt()}")
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Term.Bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
    ) {
        TickerTape(snapshot = snapshot, running = running, overlayActive = overlayActive)

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiveDot(active = running, modifier = Modifier.padding(end = 8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "SCRYING FLOOR",
                    fontFamily = Term.Mono,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp,
                    color = Term.Ink,
                )
                Text(
                    if (running) "FORENSICS ● LIVE" else "FORENSICS ○ HALTED — DEMO TAPE",
                    fontFamily = Term.Mono,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    color = Term.Faint,
                )
            }
            Text(
                "${conf.roundToInt()}",
                fontFamily = Term.Mono,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                color = Term.Ink,
            )
        }

        Panel(title = "CONFIDENCE — AUTOPSY", right = zone) {
            Text(
                "${conf.roundToInt()}",
                fontFamily = Term.Mono,
                fontSize = 44.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 42.sp,
                color = Term.Ink,
            )
            Text(
                "❝ ${oracleLine(conf)} ❞",
                fontSize = 13.sp,
                fontStyle = FontStyle.Italic,
                color = Term.Dim,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            ConfidenceBar(conf)
            Spacer(Modifier.height(8.dp))
            ConfidenceTape(points = tape, heightDp = 132)
            Spacer(Modifier.height(6.dp))
            Row {
                QuoteCell("WALK", "${(snapshot.walkingScore * 100).roundToInt()}%", snapshot.walkingScore, "freshness-weighted", Modifier.weight(1f))
                Spacer(Modifier.padding(4.dp))
                QuoteCell("VIEW", "${(snapshot.phoneInViewScore * 100).roundToInt()}%", snapshot.phoneInViewScore, "tilt × prox", Modifier.weight(1f))
                Spacer(Modifier.padding(4.dp))
                QuoteCell("BAR", if (overlayActive) "ON" else "OFF", if (overlayActive) 1f else 0f, "gate 40/30", Modifier.weight(1f))
            }
            if (!running) {
                Text(
                    stringResource(R.string.debug_off_hint),
                    fontFamily = Term.Mono,
                    fontSize = 10.sp,
                    color = Term.Faint,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        Panel(title = "MOTION FLOOR — RAW TAPE", right = "LAST 10s") {
            AccelFloor(snapshot = snapshot, heightDp = 150)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.debug_chart_legend),
                fontFamily = Term.Mono,
                fontSize = 9.sp,
                color = Term.Faint,
            )
        }
        Spacer(Modifier.height(10.dp))

        Panel(title = "LEDGER — MARKET EVENTS", right = "${ledger.size} ROWS") {
            ledger.forEach { line ->
                Text(
                    line,
                    fontFamily = Term.Mono,
                    fontSize = 10.sp,
                    color = if (line.contains("ATTACHED") || line.contains("RED") || line.contains("OMEN")) Term.Ink else Term.Dim,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
            if (ledger.isEmpty()) {
                Text("no events yet. walk menacingly.", fontFamily = Term.Mono, fontSize = 10.sp, color = Term.Faint)
            }
        }
        Spacer(Modifier.height(10.dp))

        Panel(title = "FUSION INPUTS — FULL BOOK", right = "SCRY ×2") {
            Row {
                StatCell(stringResource(R.string.debug_stat_cadence), "${snapshot.cadenceSpm.roundToInt()} spm", Modifier.weight(1f))
                StatCell(stringResource(R.string.debug_stat_tilt), "${snapshot.tiltDeg.roundToInt()}°", Modifier.weight(1f))
                StatCell(
                    stringResource(R.string.debug_stat_screen),
                    if (snapshot.screenOn) stringResource(R.string.debug_on) else stringResource(R.string.debug_off),
                    Modifier.weight(1f),
                )
            }
            Row {
                StatCell(
                    stringResource(R.string.debug_stat_proximity),
                    when (snapshot.proximityNear) { true -> stringResource(R.string.debug_near); false -> stringResource(R.string.debug_far); null -> "—" },
                    Modifier.weight(1f),
                )
                StatCell(stringResource(R.string.debug_stat_threshold), "%.2f".format(snapshot.calibratedThreshold), Modifier.weight(1f))
                StatCell(
                    stringResource(R.string.debug_stat_baseline),
                    if (snapshot.baselineCadenceSpm > 0f) "${snapshot.baselineCadenceSpm.roundToInt()} spm" else "—",
                    Modifier.weight(1f),
                )
            }
            SectionRow("MEDIAN STEP AMP", "%.2f m/s²".format(snapshot.medianStepAmplitude))
            SectionRow("STEPS LEARNED", "${snapshot.stepCount}")
            SectionRow("GATE", "SHOW 40 · 250ms / HIDE 30 · 400ms")
            SectionRow("PULSE", if (conf >= 70f) "SWELLING" else "DORMANT")
            // keep the tape colour referenced so the floor feels alive
            Text(
                "tape ink: ${if (tapeColor == Term.Red) "RED" else if (tapeColor == Term.Amber) "AMBER" else "BLUE"} · zone: $zone",
                fontFamily = Term.Mono,
                fontSize = 9.sp,
                letterSpacing = 1.sp,
                color = Term.Faint,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
    }
}
