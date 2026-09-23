package dev.lookup.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lookup.R
import dev.lookup.data.SettingsRepository
import dev.lookup.service.DetectionBus
import dev.lookup.service.OverlayService
import dev.lookup.ui.charts.AccelFloor
import dev.lookup.ui.charts.ConfidenceTape
import dev.lookup.ui.charts.TickerTape
import dev.lookup.ui.charts.rememberConfidenceTape
import dev.lookup.ui.terminal.HaltBanner
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
import kotlinx.coroutines.delay

/**
 * the terminal: one dense black-and-white floor for the whole system.
 * colour appears only in the live tapes + the warning bar.
 * everything else is ink, hairlines, and the oracle's opinions.
 */
@Composable
fun DashboardScreen(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings by SettingsRepository.settings.collectAsStateWithLifecycle()
    val running by DetectionBus.running.collectAsStateWithLifecycle()
    val overlayActive by DetectionBus.overlayActive.collectAsStateWithLifecycle()
    val overlayLost by DetectionBus.overlayPermissionLost.collectAsStateWithLifecycle()
    val snapshot by DetectionBus.snapshot.collectAsStateWithLifecycle()
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var batteryExempt by remember { mutableStateOf(context.isIgnoringBatteryOptimizations()) }

    LifecycleResumeEffect(Unit) {
        overlayGranted = Settings.canDrawOverlays(context)
        batteryExempt = context.isIgnoringBatteryOptimizations()
        onPauseOrDispose { }
    }

    val overlaySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { overlayGranted = Settings.canDrawOverlays(context) }
    val batteryExemptionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { batteryExempt = context.isIgnoringBatteryOptimizations() }

    val openOverlaySettings = {
        overlaySettingsLauncher.launch(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri()),
        )
    }
    val openBatteryExemption = {
        batteryExemptionLauncher.launch(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri()),
        )
    }

    val tape = rememberConfidenceTape(snapshot, running)
    val conf = if (running) snapshot.confidence else (tape.lastOrNull() ?: 0f)
    val delta = if (tape.size >= 20) tape.last() - tape[tape.size - 20] else 0f
    val high = tape.maxOrNull() ?: conf
    val low = tape.minOrNull() ?: conf
    val zone = zoneLabel(conf)
    val tapeColor = tapeColorFor(conf)

    var clock by remember { mutableStateOf("--:--:--") }
    LaunchedEffect(Unit) {
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
        while (true) {
            clock = fmt.format(Date())
            delay(1000)
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

        // ── masthead ──────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiveDot(active = running, modifier = Modifier.padding(end = 8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "LOOKUP TERMINAL",
                    fontFamily = Term.Mono,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp,
                    color = Term.Ink,
                )
                Text(
                    if (running) "FEED ● LIVE  ·  $clock" else "FEED ○ HALTED  ·  $clock  ·  DEMO TAPE",
                    fontFamily = Term.Mono,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    color = Term.Faint,
                )
            }
            Text(
                if (running) "LIVE" else "HALT",
                fontFamily = Term.Mono,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = if (running) Term.InvertInk else Term.Ink,
                modifier = Modifier
                    .background(
                        if (running) Term.InvertBg else Term.Panel2,
                        RoundedCornerShape(4.dp),
                    )
                    .border(1.dp, Term.Ink, RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }

        if (overlayLost) {
            HaltBanner(
                title = "OVERLAY REVOKED — FEED CUT",
                body = stringResource(R.string.dashboard_banner_body),
                actionLabel = "GRANT",
                onAction = openOverlaySettings,
            )
            Spacer(Modifier.height(10.dp))
        }

        // ── hero quote ────────────────────────────────────────────
        Panel(title = "LKUP / DOOMSCROLL INDEX", right = "25Hz · ON-DEVICE") {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${conf.roundToInt()}",
                    fontFamily = Term.Mono,
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 52.sp,
                    color = Term.Ink,
                )
                Column(modifier = Modifier.padding(start = 10.dp, bottom = 6.dp)) {
                    Text(
                        (if (delta >= 0) "▲ +" else "▼ ") + "%.2f".format(kotlin.math.abs(delta)),
                        fontFamily = Term.Mono,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = tapeColor,
                    )
                    Text(
                        "$zone · H ${high.roundToInt()} / L ${low.roundToInt()}",
                        fontFamily = Term.Mono,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        color = Term.Dim,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (overlayActive) "BAR:ON" else if (running) "BAR:ARMED" else "BAR:DEMO",
                    fontFamily = Term.Mono,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = if (overlayActive) tapeColor else Term.Faint,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            ConfidenceBar(conf)
            Spacer(Modifier.height(8.dp))
            Text(
                if (running) "live — ${conf.roundToInt()}% · the real bar only strikes while you walk heads-down." else "demo tape — arm protection for your own candles.",
                fontFamily = Term.Mono,
                fontSize = 10.sp,
                color = Term.Faint,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "❝ ${oracleLine(conf)} ❞",
                fontSize = 13.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                color = Term.Dim,
            )
        }
        Spacer(Modifier.height(10.dp))

        // ── quote cells: the evidence book ────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuoteCell(
                label = "WALK/BID",
                valuePct = "${(snapshot.walkingScore * 100).roundToInt()}%",
                fraction = snapshot.walkingScore,
                sub = "cad ${snapshot.cadenceSpm.roundToInt()}spm",
                modifier = Modifier.weight(1f),
            )
            QuoteCell(
                label = "VIEW/ASK",
                valuePct = "${(snapshot.phoneInViewScore * 100).roundToInt()}%",
                fraction = snapshot.phoneInViewScore,
                sub = "tilt ${snapshot.tiltDeg.roundToInt()}°",
                modifier = Modifier.weight(1f),
            )
            QuoteCell(
                label = "BAR/SPREAD",
                valuePct = if (overlayActive) "ON" else if (running) "ARM" else "OFF",
                fraction = conf / 100f,
                sub = "gate 40/30",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))

        // ── live tape ─────────────────────────────────────────────
        Panel(title = "LIVE TAPE — CONFIDENCE", right = if (running) "LIVE" else "DEMO · 11tps") {
            ConfidenceTape(points = tape)
            Spacer(Modifier.height(6.dp))
            Row {
                Text("LAST ${conf.roundToInt()}", fontFamily = Term.Mono, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Term.Ink, modifier = Modifier.weight(1f))
                Text("HIGH ${high.roundToInt()}", fontFamily = Term.Mono, fontSize = 10.sp, color = Term.Faint)
                Spacer(Modifier.width(10.dp))
                Text("LOW ${low.roundToInt()}", fontFamily = Term.Mono, fontSize = 10.sp, color = Term.Faint)
                Spacer(Modifier.width(10.dp))
                Text("SHOW 40 / HIDE 30 / PULSE 70", fontFamily = Term.Mono, fontSize = 10.sp, color = Term.Faint)
            }
        }
        Spacer(Modifier.height(10.dp))

        // ── motion floor ──────────────────────────────────────────
        Panel(title = "MOTION FLOOR — ACCEL DEV", right = "10s WINDOW") {
            AccelFloor(snapshot = snapshot)
            Spacer(Modifier.height(6.dp))
            Text(
                "white line: deviation from gravity · amber dash: step threshold · ticks: detected steps",
                fontFamily = Term.Mono,
                fontSize = 9.sp,
                color = Term.Faint,
            )
        }
        Spacer(Modifier.height(10.dp))

        // ── order book: fusion inputs ─────────────────────────────
        Panel(title = "ORDER BOOK — FUSION INPUTS", right = "BID × ASK") {
            Row {
                StatCell("CADENCE", "${snapshot.cadenceSpm.roundToInt()} spm", Modifier.weight(1f))
                StatCell("TILT", "${snapshot.tiltDeg.roundToInt()}°", Modifier.weight(1f))
                StatCell("SCREEN", if (snapshot.screenOn) "ON" else "OFF", Modifier.weight(1f))
            }
            Row {
                StatCell(
                    "PROX",
                    when (snapshot.proximityNear) { true -> "NEAR"; false -> "FAR"; null -> "—" },
                    Modifier.weight(1f),
                )
                StatCell("THRESH", "%.2f".format(snapshot.calibratedThreshold), Modifier.weight(1f))
                StatCell(
                    "BASE",
                    if (snapshot.baselineCadenceSpm > 0f) "${snapshot.baselineCadenceSpm.roundToInt()} spm" else "—",
                    Modifier.weight(1f),
                )
            }
            SectionRow("STEPS LEARNED", "${snapshot.stepCount}")
            SectionRow("SERVICE", if (running) "RUNNING — MONITORING" else "HALTED")
            SectionRow(
                "BAR",
                when {
                    !running -> "OFF"
                    overlayActive -> "VISIBLE NOW"
                    else -> "HIDDEN — STRIKES ON DISTRACTION"
                },
            )
        }
        Spacer(Modifier.height(10.dp))

        // ── master switch ─────────────────────────────────────────
        Panel(title = "PROTECTION — MASTER SWITCH", right = "PERSISTS REBOOT") {
            Text(
                stringResource(R.string.dashboard_protection_body),
                fontSize = 13.sp,
                color = Term.Dim,
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    if (running) {
                        SettingsRepository.systemEnabled = false
                        OverlayService.stop(context)
                    } else {
                        if (overlayGranted) {
                            SettingsRepository.systemEnabled = true
                            OverlayService.start(context)
                        } else {
                            openOverlaySettings()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Term.InvertBg,
                    contentColor = Term.InvertInk,
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (running) "■  HALT THE FEED" else "▶  GO LIVE",
                    fontFamily = Term.Mono,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                )
            }
            if (!overlayGranted) {
                Spacer(Modifier.height(8.dp))
                HaltBanner(
                    title = "NO OVERLAY — NO BAR",
                    body = stringResource(R.string.dashboard_overlay_missing),
                    actionLabel = "GRANT",
                    onAction = openOverlaySettings,
                )
            }
            if (!batteryExempt) {
                Spacer(Modifier.height(8.dp))
                HaltBanner(
                    title = "BATTERY SAVER WILL KILL US",
                    body = stringResource(R.string.dashboard_battery_warning),
                    actionLabel = "FIX",
                    onAction = openBatteryExemption,
                    glyph = "[ zZ ]",
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        // ── gain ──────────────────────────────────────────────────
        Panel(title = "GAIN — SENSITIVITY", right = "TAPES TUNED LIVE") {
            GainRow(
                title = stringResource(R.string.dashboard_motion_title),
                description = stringResource(R.string.dashboard_motion_desc),
                value = settings.motionSensitivity,
                label = stringResource(sensitivityLabelRes(settings.motionSensitivity)),
                onChange = SettingsRepository::setMotionSensitivity,
            )
            Spacer(Modifier.height(10.dp))
            GainRow(
                title = stringResource(R.string.dashboard_look_title),
                description = stringResource(R.string.dashboard_look_desc),
                value = settings.lookSensitivity,
                label = stringResource(sensitivityLabelRes(settings.lookSensitivity)),
                onChange = SettingsRepository::setLookSensitivity,
            )
        }
        Spacer(Modifier.height(10.dp))

        // ── cal ───────────────────────────────────────────────────
        Panel(title = "CAL — SELF-CALIBRATION", right = "LEARNS YOUR GAIT") {
            if (running) {
                Text(
                    stringResource(
                        R.string.dashboard_calibration_running,
                        snapshot.calibratedThreshold,
                        snapshot.baselineCadenceSpm.roundToInt(),
                        pluralStringResource(R.plurals.dashboard_steps_learned, snapshot.stepCount, snapshot.stepCount),
                    ),
                    fontFamily = Term.Mono,
                    fontSize = 11.sp,
                    color = Term.Dim,
                )
            } else {
                Text(
                    stringResource(R.string.dashboard_calibration_idle),
                    fontSize = 13.sp,
                    color = Term.Dim,
                )
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                enabled = running,
                onClick = {
                    context.startService(
                        Intent(context, OverlayService::class.java).setAction(OverlayService.ACTION_RESET_CALIBRATION),
                    )
                },
            ) {
                Text(
                    stringResource(R.string.dashboard_reset),
                    fontFamily = Term.Mono,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = if (running) Term.Ink else Term.Faint,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "lookup · sidewalk oracle · on-device only · nothing leaves your phone · v0.2.0",
            fontFamily = Term.Mono,
            fontSize = 9.sp,
            letterSpacing = 1.sp,
            color = Term.Faint,
            modifier = Modifier.padding(bottom = 18.dp),
        )
    }
}

@Composable
private fun GainRow(title: String, description: String, value: Float, label: String, onChange: (Float) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title.uppercase(), fontFamily = Term.Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Term.Ink, modifier = Modifier.weight(1f))
            Text(label.uppercase(), fontFamily = Term.Mono, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Term.Ink)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = Term.Ink,
                activeTrackColor = Term.Ink,
                inactiveTrackColor = Term.Hairline,
                activeTickColor = Term.Bg,
                inactiveTickColor = Term.Faint,
            ),
        )
        Text(description, fontSize = 12.sp, color = Term.Faint)
    }
}

private fun sensitivityLabelRes(value: Float): Int = when {
    value < 0.34f -> R.string.dashboard_level_low
    value < 0.67f -> R.string.dashboard_level_medium
    else -> R.string.dashboard_level_high
}
