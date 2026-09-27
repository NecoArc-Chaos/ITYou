package com.necoarc.ityou.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserSettings(
    val dynamicColorEnabled: Boolean = true,
    val highQualityImage: Boolean = true
)

class SettingsRepository private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("ityou_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(
        UserSettings(
            dynamicColorEnabled = prefs.getBoolean(KEY_DYNAMIC_COLOR, true),
            highQualityImage = prefs.getBoolean(KEY_HIGH_QUALITY_IMG, true)
        )
    )
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
        _settings.value = _settings.value.copy(dynamicColorEnabled = enabled)
    }

    fun setHighQualityImage(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HIGH_QUALITY_IMG, enabled).apply()
        _settings.value = _settings.value.copy(highQualityImage = enabled)
    }

    companion object {
        private const val KEY_DYNAMIC_COLOR = "key_dynamic_color"
        private const val KEY_HIGH_QUALITY_IMG = "key_high_quality_img"

        @Volatile
        private var INSTANCE: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsRepository(context).also { INSTANCE = it }
            }
        }
    }
}
