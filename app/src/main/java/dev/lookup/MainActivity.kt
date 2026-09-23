package dev.lookup

import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.lookup.data.SettingsRepository
import dev.lookup.service.OverlayService
import dev.lookup.ui.HomeScreen
import dev.lookup.ui.OnboardingScreen
import dev.lookup.ui.theme.LookupTheme

private const val NAV_DURATION_MS = 250

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LookupTheme {
                LookupNav()
            }
        }
    }
}

@Composable
private fun LookupNav() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val startDestination =
        if (SettingsRepository.onboardingCompleted) "home" else "onboarding"
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            fadeIn(animationSpec = tween(NAV_DURATION_MS)) +
                scaleIn(initialScale = 0.97f, animationSpec = tween(NAV_DURATION_MS))
        },
        exitTransition = { fadeOut(animationSpec = tween(NAV_DURATION_MS)) },
        popEnterTransition = {
            fadeIn(animationSpec = tween(NAV_DURATION_MS)) +
                scaleIn(initialScale = 0.97f, animationSpec = tween(NAV_DURATION_MS))
        },
        popExitTransition = { fadeOut(animationSpec = tween(NAV_DURATION_MS)) },
    ) {
        composable("onboarding") {
            OnboardingScreen { startProtection ->
                SettingsRepository.onboardingCompleted = true
                if (startProtection && Settings.canDrawOverlays(context)) {
                    SettingsRepository.systemEnabled = true
                    OverlayService.start(context)
                }
                navController.navigate("home") {
                    popUpTo("onboarding") { inclusive = true }
                }
            }
        }
        composable("home") {
            HomeScreen()
        }
    }
}
