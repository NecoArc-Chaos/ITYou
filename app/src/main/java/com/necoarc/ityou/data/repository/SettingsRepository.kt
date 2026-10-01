package com.necoarc.ityou.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.necoarc.ityou.ui.theme.AppFontFamily
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserSettings(
    val dynamicColorEnabled: Boolean = true,
    val highQualityImage: Boolean = true,
    val selectedFont: AppFontFamily = AppFontFamily.SYSTEM_DEFAULT
)

class SettingsRepository private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("ityou_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(
        UserSettings(
            dynamicColorEnabled = prefs.getBoolean(KEY_DYNAMIC_COLOR, true),
            highQualityImage = prefs.getBoolean(KEY_HIGH_QUALITY_IMG, true),
            selectedFont = getSavedFont()
        )
    )
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    private fun getSavedFont(): AppFontFamily {
        val fontName = prefs.getString(KEY_SELECTED_FONT, AppFontFamily.SYSTEM_DEFAULT.name)
        return try {
            AppFontFamily.valueOf(fontName ?: AppFontFamily.SYSTEM_DEFAULT.name)
        } catch (_: Exception) {
            AppFontFamily.SYSTEM_DEFAULT
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
        _settings.value = _settings.value.copy(dynamicColorEnabled = enabled)
    }

    fun setHighQualityImage(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HIGH_QUALITY_IMG, enabled).apply()
        _settings.value = _settings.value.copy(highQualityImage = enabled)
    }

    fun setSelectedFont(font: AppFontFamily) {
        prefs.edit().putString(KEY_SELECTED_FONT, font.name).apply()
        _settings.value = _settings.value.copy(selectedFont = font)
    }

    companion object {
        private const val KEY_DYNAMIC_COLOR = "key_dynamic_color"
        private const val KEY_HIGH_QUALITY_IMG = "key_high_quality_img"
        private const val KEY_SELECTED_FONT = "key_selected_font"

        @Volatile
        private var INSTANCE: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsRepository(context).also { INSTANCE = it }
            }
        }
    }
}
