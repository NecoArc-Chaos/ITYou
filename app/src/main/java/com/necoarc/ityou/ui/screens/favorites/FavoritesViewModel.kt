package com.necoarc.ityou.ui.screens.favorites

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.necoarc.ityou.ITYouApplicationSingleton
import com.necoarc.ityou.data.model.FavoriteArticle
import com.necoarc.ityou.data.repository.FavoriteRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** 收藏页状态契约。 */
@Immutable
data class FavoritesUiState(
    val favorites: List<FavoriteArticle> = emptyList()
) {
    val isEmpty: Boolean get() = favorites.isEmpty()
}

/** 收藏页用户意图。 */
sealed interface FavoritesUiIntent {
    /** 移除一条收藏。 */
    data class RemoveFavorite(val articleId: String) : FavoritesUiIntent

    /** 清空全部收藏。 */
    data object ClearAll : FavoritesUiIntent
}

/** 收藏页一次性副作用。 */
sealed interface FavoritesUiEffect {
    /** 展示一条提示。 */
    data class ShowMessage(val message: String) : FavoritesUiEffect
}

/**
 * 收藏页 ViewModel。
 *
 * 与 [com.necoarc.ityou.ui.screens.settings.SettingsViewModel] 一致，
 * 通过应用级单例获取上下文，从而保持无参构造、可直接用 `viewModel()` 创建。
 */
class FavoritesViewModel(
    private val repository: FavoriteRepository = FavoriteRepository.getInstance(
        checkNotNull(ITYouApplicationSingleton.appContext) {
            "ITYouApplication context must be initialized"
        }
    )
) : ViewModel() {

    /**
     * 状态直接从仓库的 StateFlow 派生，**不复制一份到本地**。
     *
     * 这样在详情页取消收藏后回到本页，列表会自动同步，
     * 无需手动刷新或依赖生命周期回调。
     */
    val uiState: StateFlow<FavoritesUiState> = repository.favorites
        .map { FavoritesUiState(favorites = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = FavoritesUiState()
        )

    private val _uiEffect = MutableSharedFlow<FavoritesUiEffect>()
    val uiEffect: SharedFlow<FavoritesUiEffect> = _uiEffect.asSharedFlow()

    fun sendIntent(intent: FavoritesUiIntent) {
        when (intent) {
            is FavoritesUiIntent.RemoveFavorite -> {
                repository.removeFavorite(intent.articleId)
                _uiEffect.tryEmit(FavoritesUiEffect.ShowMessage("已取消收藏"))
            }

            FavoritesUiIntent.ClearAll -> {
                repository.clearAll()
                _uiEffect.tryEmit(FavoritesUiEffect.ShowMessage("已清空全部收藏"))
            }
        }
    }

    private companion object {
        /** 订阅停止后保留状态的时长，避免旋屏等短暂分离导致状态重建。 */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
