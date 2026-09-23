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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lookup.R
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
            Text(stringResource(R.string.dashboard_title), style = MaterialTheme.typography.displaySmall)
            Text(
                stringResource(R.string.dashboard_tagline),
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
                            stringResource(R.string.dashboard_banner_title),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            stringResource(R.string.dashboard_banner_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = openOverlaySettings) { Text(stringResource(R.string.dashboard_banner_action)) }
                }
            }
        }

        Card {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.dashboard_protection_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.dashboard_protection_body),
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
                    label = stringResource(R.string.dashboard_status_service),
                    value = if (running) {
                        stringResource(R.string.dashboard_status_running)
                    } else {
                        stringResource(R.string.dashboard_status_stopped)
                    },
                    ok = running,
                )
                StatusRow(
                    label = stringResource(R.string.dashboard_status_bar),
                    value = when {
                        !running -> stringResource(R.string.dashboard_bar_off)
                        overlayActive -> stringResource(R.string.dashboard_bar_visible)
                        else -> stringResource(R.string.dashboard_bar_hidden)
                    },
                    ok = !running || overlayActive,
                )
                if (!overlayGranted) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.dashboard_overlay_missing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = openOverlaySettings) { Text(stringResource(R.string.dashboard_grant)) }
                    }
                }
                if (!batteryExempt) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.dashboard_battery_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = openBatteryExemption) { Text(stringResource(R.string.dashboard_fix)) }
                    }
                }
            }
        }

        Card {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(stringResource(R.string.dashboard_sensitivity_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                SliderRow(
                    title = stringResource(R.string.dashboard_motion_title),
                    description = stringResource(R.string.dashboard_motion_desc),
                    value = settings.motionSensitivity,
                    label = stringResource(sensitivityLabelRes(settings.motionSensitivity)),
                    onChange = SettingsRepository::setMotionSensitivity,
                )
                SliderRow(
                    title = stringResource(R.string.dashboard_look_title),
                    description = stringResource(R.string.dashboard_look_desc),
                    value = settings.lookSensitivity,
                    label = stringResource(sensitivityLabelRes(settings.lookSensitivity)),
                    onChange = SettingsRepository::setLookSensitivity,
                )
            }
        }

        Card {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(stringResource(R.string.dashboard_preview_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                ConfidenceBar(previewConfidence)
                Spacer(Modifier.height(12.dp))
                Text(
                    if (running) {
                        stringResource(
                            R.string.dashboard_preview_live,
                            previewConfidence.roundToInt(),
                        )
                    } else {
                        stringResource(R.string.dashboard_preview_demo)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(stringResource(R.string.dashboard_calibration_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                if (running) {
                    Text(
                        stringResource(
                            R.string.dashboard_calibration_running,
                            snapshot.calibratedThreshold,
                            snapshot.baselineCadenceSpm.roundToInt(),
                            pluralStringResource(
                                R.plurals.dashboard_steps_learned,
                                snapshot.stepCount,
                                snapshot.stepCount,
                            ),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        stringResource(R.string.dashboard_calibration_idle),
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
                    Text(stringResource(R.string.dashboard_reset))
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

private fun sensitivityLabelRes(value: Float): Int = when {
    value < 0.34f -> R.string.dashboard_level_low
    value < 0.67f -> R.string.dashboard_level_medium
    else -> R.string.dashboard_level_high
}
