package com.necoarc.ityou.ui.screens.settings

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.necoarc.ityou.data.model.BackAnimationType
import com.necoarc.ityou.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class SettingsUiState(
    val dynamicColorEnabled: Boolean = true,
    val highQualityImage: Boolean = true,
    val backAnimation: BackAnimationType = BackAnimationType.SPRING_SLIDE,
    val customFontName: String? = null,
    val customFontPath: String? = null,
    val customFontSizeBytes: Long = 0L,
    val isInstallingFont: Boolean = false,
    val showTimelineSheet: Boolean = false
)

sealed interface SettingsUiIntent {
    data class SetDynamicColor(val enabled: Boolean) : SettingsUiIntent
    data class SetHighQualityImage(val enabled: Boolean) : SettingsUiIntent
    data class SetBackAnimation(val type: BackAnimationType) : SettingsUiIntent
    data class InstallCustomFont(val uri: Uri) : SettingsUiIntent
    data object ClearCustomFont : SettingsUiIntent
    data class SetTimelineSheetVisible(val visible: Boolean) : SettingsUiIntent
}

sealed interface SettingsUiEffect {
    data class ShowToast(val message: String) : SettingsUiEffect
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository.getInstance(application)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<SettingsUiEffect>()
    val uiEffect: SharedFlow<SettingsUiEffect> = _uiEffect.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.settings.collect { settings ->
                _uiState.update { current ->
                    current.copy(
                        dynamicColorEnabled = settings.dynamicColorEnabled,
                        highQualityImage = settings.highQualityImage,
                        backAnimation = settings.backAnimation,
                        customFontName = settings.customFontName,
                        customFontPath = settings.customFontPath,
                        customFontSizeBytes = settings.customFontSizeBytes
                    )
                }
            }
        }
    }

    fun sendIntent(intent: SettingsUiIntent) {
        when (intent) {
            is SettingsUiIntent.SetDynamicColor -> {
                repository.setDynamicColor(intent.enabled)
            }
            is SettingsUiIntent.SetHighQualityImage -> {
                repository.setHighQualityImage(intent.enabled)
            }
            is SettingsUiIntent.SetBackAnimation -> {
                repository.setBackAnimation(intent.type)
            }
            is SettingsUiIntent.InstallCustomFont -> {
                installFont(intent.uri)
            }
            is SettingsUiIntent.ClearCustomFont -> {
                repository.clearCustomFont()
                viewModelScope.launch {
                    _uiEffect.emit(SettingsUiEffect.ShowToast("已恢复系统默认字体"))
                }
            }
            is SettingsUiIntent.SetTimelineSheetVisible -> {
                _uiState.update { it.copy(showTimelineSheet = intent.visible) }
            }
        }
    }

    private fun installFont(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isInstallingFont = true) }
            val result = repository.installCustomFont(uri)
            _uiState.update { it.copy(isInstallingFont = false) }
            result.onSuccess { fileName ->
                _uiEffect.emit(SettingsUiEffect.ShowToast("已成功应用字体: $fileName"))
            }.onFailure { error ->
                _uiEffect.emit(SettingsUiEffect.ShowToast("字体安装失败: ${error.localizedMessage ?: "未知错误"}"))
            }
        }
    }
}
