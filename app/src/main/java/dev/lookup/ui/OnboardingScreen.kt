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
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.lookup.R
import dev.lookup.ui.terminal.Term
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 3

/**
 * onboarding, terminal edition: three clearance pages on pure black.
 * the demo bar is the only colour on screen — a preview of coming omens.
 */
@SuppressLint("InlinedApi")
@Composable
fun OnboardingScreen(onDone: (startProtection: Boolean) -> Unit) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()

    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    val needsNotifications = Build.VERSION.SDK_INT >= 33
    var notificationsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var batteryExempt by remember { mutableStateOf(context.isIgnoringBatteryOptimizations()) }

    LifecycleResumeEffect(Unit) {
        overlayGranted = Settings.canDrawOverlays(context)
        if (needsNotifications) {
            notificationsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        }
        batteryExempt = context.isIgnoringBatteryOptimizations()
        onPauseOrDispose { }
    }

    val overlaySettingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        overlayGranted = Settings.canDrawOverlays(context)
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsGranted = granted
    }
    val batteryExemptionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        batteryExempt = context.isIgnoringBatteryOptimizations()
    }

    val openOverlaySettings = {
        overlaySettingsLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri()))
    }
    val openBatteryExemption = {
        batteryExemptionLauncher.launch(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri()))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Term.Bg)
            .safeDrawingPadding()
            .padding(20.dp),
    ) {
        // masthead
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "LOOKUP // WALKING TERMINAL",
                fontFamily = Term.Mono,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = Term.Ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                "0${pagerState.currentPage + 1} / 03",
                fontFamily = Term.Mono,
                fontSize = 11.sp,
                color = Term.Faint,
            )
        }
        Spacer(Modifier.height(10.dp))
        // progress rail
        Box(modifier = Modifier.fillMaxWidth().background(Term.Grid, RoundedCornerShape(2.dp))) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((pagerState.currentPage + 1) / PAGE_COUNT.toFloat())
                    .background(Term.Ink, RoundedCornerShape(2.dp))
                    .padding(vertical = 2.dp),
            ) {}
        }

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            when (page) {
                0 -> IntroPage()
                1 -> HowItWorksPage()
                else -> PermissionsPage(
                    overlayGranted = overlayGranted,
                    onGrantOverlay = openOverlaySettings,
                    needsNotifications = needsNotifications,
                    notificationsGranted = notificationsGranted,
                    onGrantNotifications = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    batteryExempt = batteryExempt,
                    onGrantBatteryExemption = openBatteryExemption,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (pagerState.currentPage > 0) {
                TextButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } }) {
                    Text("← BACK", fontFamily = Term.Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Term.Dim)
                }
            }
            Spacer(Modifier.weight(1f))
            if (pagerState.currentPage < PAGE_COUNT - 1) {
                Button(
                    onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                    colors = ButtonDefaults.buttonColors(containerColor = Term.InvertBg, contentColor = Term.InvertInk),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text("NEXT →", fontFamily = Term.Mono, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
            } else {
                TextButton(onClick = { onDone(false) }) {
                    Text(stringResource(R.string.onboarding_not_now), fontFamily = Term.Mono, fontSize = 12.sp, color = Term.Dim)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = overlayGranted,
                    onClick = { onDone(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = Term.InvertBg, contentColor = Term.InvertInk, disabledContainerColor = Term.Panel2, disabledContentColor = Term.Faint),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text("▶ GO LIVE", fontFamily = Term.Mono, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
            }
        }
        if (pagerState.currentPage == PAGE_COUNT - 1 && !overlayGranted) {
            Text(
                stringResource(R.string.onboarding_overlay_hint),
                fontFamily = Term.Mono,
                fontSize = 10.sp,
                color = Term.Faint,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
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
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "DOOMSCROLL,",
            fontFamily = Term.Mono,
            fontSize = 38.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 38.sp,
            color = Term.Ink,
        )
        Text(
            "BUT MAKE IT",
            fontFamily = Term.Mono,
            fontSize = 38.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 38.sp,
            color = Term.Faint,
        )
        Text(
            "PEDESTRIAN.",
            fontFamily = Term.Mono,
            fontSize = 38.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 38.sp,
            color = Term.Ink,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.onboarding_intro_body),
            fontSize = 15.sp,
            color = Term.Dim,
        )
        Spacer(Modifier.height(20.dp))
        ConfidenceBar(demo.value)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.onboarding_intro_demo_caption) + " — blue whispers, amber warns, red begs.",
            fontFamily = Term.Mono,
            fontSize = 10.sp,
            color = Term.Faint,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "❝ the oracle does not judge. it merely tapes. ❞",
            fontSize = 13.sp,
            fontStyle = FontStyle.Italic,
            color = Term.Faint,
        )
    }
}

@Composable
private fun HowItWorksPage() {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "THREE OMENS",
            fontFamily = Term.Mono,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            color = Term.Ink,
        )
        Text(
            "how the oracle reads your walk",
            fontFamily = Term.Mono,
            fontSize = 10.sp,
            letterSpacing = 2.sp,
            color = Term.Faint,
        )
        Spacer(Modifier.height(18.dp))
        OmenRow("01", stringResource(R.string.onboarding_step1_title), stringResource(R.string.onboarding_step1_body))
        OmenRow("02", stringResource(R.string.onboarding_step2_title), stringResource(R.string.onboarding_step2_body))
        OmenRow("03", stringResource(R.string.onboarding_step3_title), stringResource(R.string.onboarding_step3_body))
        Spacer(Modifier.height(12.dp))
        Text(
            "everything runs on-device. the cloud never hears about your 2am kebab pilgrimage.",
            fontFamily = Term.Mono,
            fontSize = 10.sp,
            color = Term.Faint,
        )
    }
}

@Composable
private fun OmenRow(number: String, title: String, body: String) {
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(number, fontFamily = Term.Mono, fontSize = 12.sp, fontWeight = FontWeight.Black, color = Term.InvertInk,
                modifier = Modifier.background(Term.InvertBg, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
            Spacer(Modifier.width(12.dp))
            Text(title.uppercase(), fontFamily = Term.Mono, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Term.Ink)
        }
        Spacer(Modifier.height(6.dp))
        Text(body, fontSize = 14.sp, color = Term.Dim)
        Spacer(Modifier.height(10.dp))
        Box(modifier = Modifier.fillMaxWidth().background(Term.Hairline).padding(vertical = 0.5.dp)) {}
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
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "CLEARANCE",
            fontFamily = Term.Mono,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            color = Term.Ink,
        )
        Text(
            "three stamps. then you may doomscroll in motion.",
            fontFamily = Term.Mono,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            color = Term.Faint,
        )
        Spacer(Modifier.height(16.dp))
        ClearanceCard(
            granted = overlayGranted,
            title = stringResource(R.string.onboarding_overlay_title),
            body = stringResource(R.string.onboarding_overlay_body),
            actionLabel = if (overlayGranted) null else stringResource(R.string.onboarding_overlay_action),
            onAction = onGrantOverlay,
        )
        Spacer(Modifier.height(10.dp))
        ClearanceCard(
            granted = !needsNotifications || notificationsGranted,
            title = if (needsNotifications) stringResource(R.string.onboarding_notifications_title) else stringResource(R.string.onboarding_notifications_title_not_needed),
            body = if (needsNotifications) stringResource(R.string.onboarding_notifications_body) else stringResource(R.string.onboarding_notifications_body_not_needed),
            actionLabel = when {
                !needsNotifications -> null
                notificationsGranted -> null
                else -> stringResource(R.string.onboarding_notifications_action)
            },
            onAction = onGrantNotifications,
        )
        Spacer(Modifier.height(10.dp))
        ClearanceCard(
            granted = batteryExempt,
            title = stringResource(R.string.onboarding_battery_title),
            body = stringResource(R.string.onboarding_battery_body),
            actionLabel = if (batteryExempt) null else stringResource(R.string.onboarding_battery_action),
            onAction = onGrantBatteryExemption,
        )
    }
}

@Composable
private fun ClearanceCard(title: String, body: String, granted: Boolean, actionLabel: String?, onAction: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Term.Panel, RoundedCornerShape(10.dp))
            .border(1.dp, if (granted) Term.Hairline else Term.Ink, RoundedCornerShape(10.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title.uppercase(),
                fontFamily = Term.Mono,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = Term.Ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (granted) "[ OK ]" else "[ !! ]",
                fontFamily = Term.Mono,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = if (granted) Term.Faint else Term.Ink,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(body, fontSize = 13.sp, color = Term.Dim)
        if (actionLabel != null) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onAction,
                shape = RoundedCornerShape(6.dp),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Term.Ink),
                border = androidx.compose.foundation.BorderStroke(1.dp, Term.Ink),
            ) {
                Text(actionLabel.uppercase(), fontFamily = Term.Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
