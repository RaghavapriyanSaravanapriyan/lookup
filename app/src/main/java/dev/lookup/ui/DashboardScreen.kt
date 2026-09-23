package dev.lookup.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lookup.data.SettingsRepository
import dev.lookup.service.DetectionBus
import dev.lookup.service.OverlayService
import kotlin.math.roundToInt

/**
 * The dashboard: one master toggle for the whole system, live service and
 * warning-bar status, sensitivity settings, an overlay preview, and the
 * engine's self-calibration readout. The app works in the background — this
 * screen is only checked into occasionally.
 */
@Composable
fun DashboardScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings by SettingsRepository.settings.collectAsStateWithLifecycle()
    val running by DetectionBus.running.collectAsStateWithLifecycle()
    val overlayActive by DetectionBus.overlayActive.collectAsStateWithLifecycle()
    val overlayLost by DetectionBus.overlayPermissionLost.collectAsStateWithLifecycle()
    val snapshot by DetectionBus.snapshot.collectAsStateWithLifecycle()
    var overlayGranted by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }
    var batteryExempt by remember {
        mutableStateOf(context.isIgnoringBatteryOptimizations())
    }

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
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:${context.packageName}".toUri(),
            ),
        )
    }
    val openBatteryExemption = {
        batteryExemptionLauncher.launch(
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                "package:${context.packageName}".toUri(),
            ),
        )
    }

    // Demo sweep drives the preview while protection is off.
    val demo = remember { Animatable(0f) }
    LaunchedEffect(running) {
        if (!running) {
            demo.snapTo(0f)
            while (true) {
                demo.animateTo(100f, tween(2400, easing = FastOutSlowInEasing))
                demo.animateTo(0f, tween(1200, easing = FastOutSlowInEasing))
            }
        }
    }
    val previewConfidence = if (running) snapshot.confidence else demo.value

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        Column {
            Text("lookup", style = MaterialTheme.typography.displaySmall)
            Text(
                "A background watch for heads-down walking.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (overlayLost) {
            Card {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Overlay permission was revoked",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            "Protection stopped. Re-grant overlay access to continue.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = openOverlaySettings) { Text("Grant") }
                }
            }
        }

        Card {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Protection", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Runs continuously, even after reboot. The bar appears " +
                                "only while you're walking heads-down.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Switch(
                        checked = running,
                        onCheckedChange = { checked ->
                            if (checked) {
                                if (overlayGranted) {
                                    SettingsRepository.systemEnabled = true
                                    OverlayService.start(context)
                                } else {
                                    openOverlaySettings()
                                }
                            } else {
                                SettingsRepository.systemEnabled = false
                                OverlayService.stop(context)
                            }
                        },
                    )
                }
                Spacer(Modifier.height(16.dp))
                StatusRow(
                    label = "Service",
                    value = if (running) "Running — monitoring sensors" else "Stopped",
                    ok = running,
                )
                StatusRow(
                    label = "Warning bar",
                    value = when {
                        !running -> "Off"
                        overlayActive -> "Visible right now"
                        else -> "Hidden — appears during distraction"
                    },
                    ok = !running || overlayActive,
                )
                if (!overlayGranted) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Overlay access is missing — the bar can't be drawn.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = openOverlaySettings) { Text("Grant") }
                    }
                }
                if (!batteryExempt) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Battery optimization is on — many phones will kill the " +
                                "service within minutes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = openBatteryExemption) { Text("Fix") }
                    }
                }
            }
        }

        Card {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Sensitivity", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                SliderRow(
                    title = "Step detection",
                    description = "How easily walking motion is recognized.",
                    value = settings.motionSensitivity,
                    label = sensitivityLabel(settings.motionSensitivity),
                    onChange = SettingsRepository::setMotionSensitivity,
                )
                SliderRow(
                    title = "Phone in view",
                    description = "How wide the \"screen up in front of you\" angle is.",
                    value = settings.lookSensitivity,
                    label = sensitivityLabel(settings.lookSensitivity),
                    onChange = SettingsRepository::setLookSensitivity,
                )
            }
        }

        Card {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Overlay preview", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                ConfidenceBar(previewConfidence)
                Spacer(Modifier.height(12.dp))
                Text(
                    if (running) {
                        "Live — confidence ${previewConfidence.roundToInt()}%. The real " +
                            "bar only appears while you're walking heads-down."
                    } else {
                        "Demo sweep — turn on protection for live values."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Self-calibration", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                if (running) {
                    Text(
                        "Step threshold ${"%.2f".format(snapshot.calibratedThreshold)} m/s\u00B2 " +
                            "\u00B7 baseline ${snapshot.baselineCadenceSpm.roundToInt()} spm " +
                            "\u00B7 ${snapshot.stepCount} steps learned",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "The engine tunes its step threshold and cadence baseline to " +
                            "your gait while it runs. Turn on protection to calibrate.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    enabled = running,
                    onClick = {
                        context.startService(
                            Intent(context, OverlayService::class.java)
                                .setAction(OverlayService.ACTION_RESET_CALIBRATION),
                        )
                    },
                ) {
                    Text("Reset calibration")
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StatusRow(label: String, value: String, ok: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (ok) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.tertiary
            },
            modifier = Modifier.padding(end = 12.dp),
        )
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SliderRow(
    title: String,
    description: String,
    value: Float,
    label: String,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0f..1f,
        )
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun sensitivityLabel(value: Float): String = when {
    value < 0.34f -> "Low"
    value < 0.67f -> "Medium"
    else -> "High"
}
