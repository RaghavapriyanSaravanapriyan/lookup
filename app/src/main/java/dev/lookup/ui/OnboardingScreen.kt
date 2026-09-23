package dev.lookup.ui

import android.Manifest
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 3

/**
 * Three-page onboarding: what the app does, how detection works, and the
 * permission rationale + grants (overlay is required, notifications where
 * applicable). The last page's primary action starts protection immediately.
 */
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

    val openOverlaySettings = {
        overlaySettingsLauncher.launch(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
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
                ) { Text("Back") }
            }
            Spacer(Modifier.weight(1f))
            if (pagerState.currentPage < PAGE_COUNT - 1) {
                Button(
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    },
                ) { Text("Next") }
            } else {
                TextButton(onClick = { onDone(false) }) { Text("Not now") }
                Spacer(Modifier.width(12.dp))
                Button(
                    enabled = overlayGranted,
                    onClick = { onDone(true) },
                ) { Text("Start protection") }
            }
        }
        if (pagerState.currentPage == PAGE_COUNT - 1 && !overlayGranted) {
            Text(
                "Grant overlay access to enable protection.",
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
        Text("Walk more.\nScroll less.", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(16.dp))
        Text(
            "Lookup senses when you're walking with your eyes on the screen and " +
                "raises a warning bar over your status bar — blue, then amber, then red " +
                "as the risk climbs.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(32.dp))
        ConfidenceBar(demo.value)
        Spacer(Modifier.height(8.dp))
        Text(
            "This is how the bar reacts as confidence grows.",
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
        Text("How detection works", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        NumberedStep(
            number = 1,
            title = "It hears your steps",
            body = "Your accelerometer reveals a walking rhythm. The engine learns " +
                "your gait over time and tunes itself to it.",
        )
        NumberedStep(
            number = 2,
            title = "It sees where the phone is",
            body = "The tilt of the screen and the proximity sensor tell the engine " +
                "whether the display is up in front of your face — or in your pocket.",
        )
        NumberedStep(
            number = 3,
            title = "It warns you in real time",
            body = "Walking while looking at the screen makes the bar turn red. " +
                "Everything runs on your device; nothing ever leaves your phone.",
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
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Two things to allow", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(20.dp))
        PermissionCard(
            title = "Draw over other apps",
            body = "The warning must appear above whatever app you're walking with. " +
                "Lookup draws only a thin bar at the top of the screen — nothing else.",
            granted = overlayGranted,
            actionLabel = if (overlayGranted) null else "Allow overlay",
            onAction = onGrantOverlay,
        )
        Spacer(Modifier.height(16.dp))
        PermissionCard(
            title = if (needsNotifications) "Notifications" else "Notifications (not needed)",
            body = if (needsNotifications) {
                "Android requires an ongoing notification while the sensor service " +
                    "runs in the background. It stays silent and never interrupts you."
            } else {
                "Your Android version shows the background notification without " +
                    "asking for permission."
            },
            granted = !needsNotifications || notificationsGranted,
            actionLabel = when {
                !needsNotifications -> null
                notificationsGranted -> null
                else -> "Allow notifications"
            },
            onAction = onGrantNotifications,
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
    Icon(icon, contentDescription = if (granted) "Granted" else "Not granted", tint = tint)
}
