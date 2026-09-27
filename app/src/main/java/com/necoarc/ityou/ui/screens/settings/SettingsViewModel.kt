package com.necoarc.ityou.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.necoarc.ityou.data.repository.SettingsRepository
import com.necoarc.ityou.data.repository.UserSettings
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository.getInstance(application)
    val settings: StateFlow<UserSettings> = repository.settings

    fun setDynamicColor(enabled: Boolean) {
        repository.setDynamicColor(enabled)
    }

    fun setHighQualityImage(enabled: Boolean) {
        repository.setHighQualityImage(enabled)
    }
}
