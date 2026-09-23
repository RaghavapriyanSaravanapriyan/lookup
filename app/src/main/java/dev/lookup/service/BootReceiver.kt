package dev.lookup.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import dev.lookup.data.SettingsRepository

/**
 * Restarts the always-on detection service after boot (or app update) when
 * the user had the system enabled — the watch must survive reboots without
 * the user remembering to open the app.
 *
 * Overlay permission is re-checked: if it was revoked while the system was
 * off, the service is not started and the dashboard banner state is armed so
 * the next app open explains what happened.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> Unit
            else -> return
        }
        if (!SettingsRepository.systemEnabled) return
        if (!Settings.canDrawOverlays(context)) {
            DetectionBus.overlayPermissionLost.value = true
            return
        }
        OverlayService.start(context)
    }
}
