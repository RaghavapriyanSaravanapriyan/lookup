package dev.lookup.ui

import android.content.Context
import android.os.PowerManager

/**
 * True when the app is exempt from Doze/App-Standby killing its background
 * service. Checked in onboarding and on the dashboard; the user grants it via
 * Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS.
 */
internal fun Context.isIgnoringBatteryOptimizations(): Boolean {
    val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
    return powerManager.isIgnoringBatteryOptimizations(packageName)
}
