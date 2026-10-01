package com.necoarc.ityou.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import com.necoarc.ityou.data.model.BackAnimationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class UserSettings(
    val dynamicColorEnabled: Boolean = true,
    val highQualityImage: Boolean = true,
    val backAnimation: BackAnimationType = BackAnimationType.SPRING_SLIDE,
    val customFontName: String? = null,
    val customFontPath: String? = null,
    val customFontSizeBytes: Long = 0L
)

class SettingsRepository private constructor(private val context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("ityou_settings", Context.MODE_PRIVATE)

    private val fontDir = File(context.filesDir, "custom_fonts").apply { mkdirs() }

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    private fun loadSettings(): UserSettings {
        val animName = prefs.getString(KEY_BACK_ANIMATION, BackAnimationType.SPRING_SLIDE.name)
        val backAnim = try {
            BackAnimationType.valueOf(animName ?: BackAnimationType.SPRING_SLIDE.name)
        } catch (_: Exception) {
            BackAnimationType.SPRING_SLIDE
        }

        val fontPath = prefs.getString(KEY_CUSTOM_FONT_PATH, null)
        val fontName = prefs.getString(KEY_CUSTOM_FONT_NAME, null)
        val fontFile = fontPath?.let { File(it) }
        val validFontPath = if (fontFile != null && fontFile.exists() && fontFile.length() > 0) fontPath else null
        val fontSize = if (validFontPath != null) fontFile?.length() ?: 0L else 0L

        return UserSettings(
            dynamicColorEnabled = prefs.getBoolean(KEY_DYNAMIC_COLOR, true),
            highQualityImage = prefs.getBoolean(KEY_HIGH_QUALITY_IMG, true),
            backAnimation = backAnim,
            customFontName = if (validFontPath != null) fontName else null,
            customFontPath = validFontPath,
            customFontSizeBytes = fontSize
        )
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
        _settings.value = _settings.value.copy(dynamicColorEnabled = enabled)
    }

    fun setHighQualityImage(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HIGH_QUALITY_IMG, enabled).apply()
        _settings.value = _settings.value.copy(highQualityImage = enabled)
    }

    fun setBackAnimation(type: BackAnimationType) {
        prefs.edit().putString(KEY_BACK_ANIMATION, type.name).apply()
        _settings.value = _settings.value.copy(backAnimation = type)
    }

    suspend fun installCustomFont(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            var fileName = "custom_font.ttf"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    val name = cursor.getString(nameIndex)
                    if (!name.isNullOrBlank()) {
                        fileName = name
                    }
                }
            }

            // 清理既有旧字体文件
            fontDir.listFiles()?.forEach { it.delete() }

            val targetFile = File(fontDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(Exception("无法读取字体文件"))

            // 预验证字体是否能够被 Android 原生 Typeface 解析
            android.graphics.Typeface.createFromFile(targetFile)

            prefs.edit()
                .putString(KEY_CUSTOM_FONT_PATH, targetFile.absolutePath)
                .putString(KEY_CUSTOM_FONT_NAME, fileName)
                .apply()

            _settings.value = _settings.value.copy(
                customFontName = fileName,
                customFontPath = targetFile.absolutePath,
                customFontSizeBytes = targetFile.length()
            )

            Result.success(fileName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun clearCustomFont() {
        fontDir.listFiles()?.forEach { it.delete() }
        prefs.edit()
            .remove(KEY_CUSTOM_FONT_PATH)
            .remove(KEY_CUSTOM_FONT_NAME)
            .apply()
        _settings.value = _settings.value.copy(
            customFontName = null,
            customFontPath = null,
            customFontSizeBytes = 0L
        )
    }

    companion object {
        private const val KEY_DYNAMIC_COLOR = "key_dynamic_color"
        private const val KEY_HIGH_QUALITY_IMG = "key_high_quality_img"
        private const val KEY_BACK_ANIMATION = "key_back_animation"
        private const val KEY_CUSTOM_FONT_PATH = "key_custom_font_path"
        private const val KEY_CUSTOM_FONT_NAME = "key_custom_font_name"

        @Volatile
        private var INSTANCE: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsRepository(context).also { INSTANCE = it }
            }
        }
    }
}
