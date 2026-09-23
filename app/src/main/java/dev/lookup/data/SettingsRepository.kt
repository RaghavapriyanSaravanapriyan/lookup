package dev.lookup.data

import android.content.Context
import android.content.SharedPreferences
import dev.lookup.detection.DetectionSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Small persisted settings store. All writes happen on the main thread
 * (Compose sliders), so a simple SharedPreferences-backed StateFlow suffices.
 */
object SettingsRepository {
    private const val PREFS_NAME = "lookup_settings"
    private const val KEY_MOTION_SENSITIVITY = "motion_sensitivity"
    private const val KEY_LOOK_SENSITIVITY = "look_sensitivity"
    private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"

    private lateinit var prefs: SharedPreferences

    private val _settings = MutableStateFlow(DetectionSettings())
    val settings: StateFlow<DetectionSettings> = _settings

    fun init(context: Context) {
        prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _settings.value = DetectionSettings(
            motionSensitivity = prefs.getFloat(KEY_MOTION_SENSITIVITY, 0.5f),
            lookSensitivity = prefs.getFloat(KEY_LOOK_SENSITIVITY, 0.5f),
        )
    }

    fun setMotionSensitivity(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        prefs.edit().putFloat(KEY_MOTION_SENSITIVITY, clamped).apply()
        _settings.value = _settings.value.copy(motionSensitivity = clamped)
    }

    fun setLookSensitivity(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        prefs.edit().putFloat(KEY_LOOK_SENSITIVITY, clamped).apply()
        _settings.value = _settings.value.copy(lookSensitivity = clamped)
    }

    var onboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, value).apply()
        }
}
