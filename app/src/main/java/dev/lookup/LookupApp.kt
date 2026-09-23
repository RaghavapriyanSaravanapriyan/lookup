package dev.lookup

import android.app.Application
import dev.lookup.data.SettingsRepository

class LookupApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsRepository.init(this)
    }
}
