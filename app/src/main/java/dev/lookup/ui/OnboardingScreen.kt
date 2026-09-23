package dev.lookup.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.lookup.R
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 3

/**
 * Three-page onboarding: what the app does, how detection works, and the
 * permission rationale + grants (overlay is required, notifications where
 * applicable). The last page's primary action starts protection immediately.
 *
 * POST_NOTIFICATIONS references are runtime-gated to API 33+; the constant is
 * compile-time inlined so referencing it on older releases is safe.
 */
@SuppressLint("InlinedApi")
@Composable
fun OnboardingScreen(onDone: (startProtection: Boolean) -> Unit) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()

    var overlayGranted by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }
    val needsNotifications = Build.VERSION.SDK_INT >= 33
    var notificationsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var batteryExempt by remember {
        mutableStateOf(context.isIgnoringBatteryOptimizations())
    }

    // Re-check permissions every time the app comes back to the front (the
    // overlay grant happens in system settings, not in-app).
    LifecycleResumeEffect(Unit) {
        overlayGranted = Settings.canDrawOverlays(context)
        if (needsNotifications) {
            notificationsGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        }
        batteryExempt = context.isIgnoringBatteryOptimizations()
        onPauseOrDispose { }
    }

    val overlaySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        overlayGranted = Settings.canDrawOverlays(context)
    }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationsGranted = granted }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            when (page) {
                0 -> IntroPage()
                1 -> HowItWorksPage()
                else -> PermissionsPage(
                    overlayGranted = overlayGranted,
                    onGrantOverlay = openOverlaySettings,
                    needsNotifications = needsNotifications,
                    notificationsGranted = notificationsGranted,
                    onGrantNotifications = {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                    batteryExempt = batteryExempt,
                    onGrantBatteryExemption = openBatteryExemption,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(PAGE_COUNT) { index ->
                val selected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (selected) 10.dp else 8.dp)
                        .background(
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = CircleShape,
                        ),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (pagerState.currentPage > 0) {
                TextButton(
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    },
                ) { Text(stringResource(R.string.onboarding_back)) }
            }
            Spacer(Modifier.weight(1f))
            if (pagerState.currentPage < PAGE_COUNT - 1) {
                Button(
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    },
                ) { Text(stringResource(R.string.onboarding_next)) }
            } else {
                TextButton(onClick = { onDone(false) }) { Text(stringResource(R.string.onboarding_not_now)) }
                Spacer(Modifier.width(12.dp))
                Button(
                    enabled = overlayGranted,
                    onClick = { onDone(true) },
                ) { Text(stringResource(R.string.onboarding_start)) }
            }
        }
        if (pagerState.currentPage == PAGE_COUNT - 1 && !overlayGranted) {
            Text(
                stringResource(R.string.onboarding_overlay_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun IntroPage() {
    val demo = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            demo.animateTo(100f, tween(2600, easing = FastOutSlowInEasing))
            demo.animateTo(0f, tween(1400, easing = FastOutSlowInEasing))
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.onboarding_intro_title), style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.onboarding_intro_body),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(32.dp))
        ConfidenceBar(demo.value)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.onboarding_intro_demo_caption),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HowItWorksPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.onboarding_how_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        NumberedStep(
            number = 1,
            title = stringResource(R.string.onboarding_step1_title),
            body = stringResource(R.string.onboarding_step1_body),
        )
        NumberedStep(
            number = 2,
            title = stringResource(R.string.onboarding_step2_title),
            body = stringResource(R.string.onboarding_step2_body),
        )
        NumberedStep(
            number = 3,
            title = stringResource(R.string.onboarding_step3_title),
            body = stringResource(R.string.onboarding_step3_body),
        )
    }
}

@Composable
private fun NumberedStep(number: Int, title: String, body: String) {
    Row(modifier = Modifier.padding(vertical = 12.dp)) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PermissionsPage(
    overlayGranted: Boolean,
    onGrantOverlay: () -> Unit,
    needsNotifications: Boolean,
    notificationsGranted: Boolean,
    onGrantNotifications: () -> Unit,
    batteryExempt: Boolean,
    onGrantBatteryExemption: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.onboarding_permissions_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(20.dp))
        PermissionCard(
            title = stringResource(R.string.onboarding_overlay_title),
            body = stringResource(R.string.onboarding_overlay_body),
            granted = overlayGranted,
            actionLabel = if (overlayGranted) null else stringResource(R.string.onboarding_overlay_action),
            onAction = onGrantOverlay,
        )
        Spacer(Modifier.height(16.dp))
        PermissionCard(
            title = if (needsNotifications) {
                stringResource(R.string.onboarding_notifications_title)
            } else {
                stringResource(R.string.onboarding_notifications_title_not_needed)
            },
            body = if (needsNotifications) {
                stringResource(R.string.onboarding_notifications_body)
            } else {
                stringResource(R.string.onboarding_notifications_body_not_needed)
            },
            granted = !needsNotifications || notificationsGranted,
            actionLabel = when {
                !needsNotifications -> null
                notificationsGranted -> null
                else -> stringResource(R.string.onboarding_notifications_action)
            },
            onAction = onGrantNotifications,
        )
        Spacer(Modifier.height(16.dp))
        PermissionCard(
            title = stringResource(R.string.onboarding_battery_title),
            body = stringResource(R.string.onboarding_battery_body),
            granted = batteryExempt,
            actionLabel = if (batteryExempt) null else stringResource(R.string.onboarding_battery_action),
            onAction = onGrantBatteryExemption,
        )
    }
}

@Composable
private fun PermissionCard(
    title: String,
    body: String,
    granted: Boolean,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                PermissionStatus(granted = granted)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actionLabel != null) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun PermissionStatus(granted: Boolean) {
    val icon: ImageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.Warning
    val tint = if (granted) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.tertiary
    }
    Icon(
        icon,
        contentDescription = if (granted) {
            stringResource(R.string.onboarding_status_granted)
        } else {
            stringResource(R.string.onboarding_status_not_granted)
        },
        tint = tint,
    )
}
