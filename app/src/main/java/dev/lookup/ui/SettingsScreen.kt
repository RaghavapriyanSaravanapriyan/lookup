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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import androidx.core.net.toUri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lookup.data.SettingsRepository
import dev.lookup.service.DetectionBus
import dev.lookup.service.OverlayService
import kotlin.math.roundToInt

/**
 * Main control screen: start/stop protection, sensitivity sliders with a live
 * overlay preview, and the engine's self-calibration readout.
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings by SettingsRepository.settings.collectAsStateWithLifecycle()
    val running by DetectionBus.running.collectAsStateWithLifecycle()
    val overlayLost by DetectionBus.overlayPermissionLost.collectAsStateWithLifecycle()
    val snapshot by DetectionBus.snapshot.collectAsStateWithLifecycle()
    var overlayGranted by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }

    LifecycleResumeEffect(Unit) {
        overlayGranted = Settings.canDrawOverlays(context)
        onPauseOrDispose { }
    }

    val overlaySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { overlayGranted = Settings.canDrawOverlays(context) }

    val openOverlaySettings = {
        overlaySettingsLauncher.launch(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
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
                Text("Protection", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (running) {
                        "Watching for distracted walking. The warning bar is live " +
                            "above your status bar."
                    } else {
                        "Off. Start the sensor service to get a warning bar over " +
                            "your status bar."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (overlayGranted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                        contentDescription = null,
                        tint = if (overlayGranted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (overlayGranted) "Overlay access granted"
                        else "Overlay access missing — needed to draw the bar",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.weight(1f))
                    if (running) {
                        OutlinedButton(onClick = { OverlayService.stop(context) }) {
                            Text("Stop")
                        }
                    } else {
                        Button(
                            onClick = {
                                if (overlayGranted) OverlayService.start(context)
                                else openOverlaySettings()
                            },
                        ) {
                            Text(if (overlayGranted) "Start" else "Grant access")
                        }
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
                        "Live — confidence ${previewConfidence.roundToInt()}%"
                    } else {
                        "Demo sweep — start protection for live values."
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
                            "your gait while it runs. Start protection to calibrate.",
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
