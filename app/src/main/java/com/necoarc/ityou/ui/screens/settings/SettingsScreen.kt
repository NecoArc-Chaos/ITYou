package com.necoarc.ityou.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MotionPhotosOn
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.necoarc.ityou.BuildConfig
import com.necoarc.ityou.data.model.BackAnimationType
import com.necoarc.ityou.data.model.ThemeMode
import com.necoarc.ityou.data.repository.AuthRepository
import com.necoarc.ityou.ui.components.VersionTimelineSheet
import com.necoarc.ityou.ui.theme.BUNDLED_FONT_CREDIT
import com.necoarc.ityou.ui.theme.BUNDLED_FONT_NAME
import com.necoarc.ityou.ui.theme.ShapeCache
import com.necoarc.ityou.ui.theme.resolveFontFamily
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLoginClick: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()
    val authRepository = remember(context) { AuthRepository.getInstance(context) }
    val authState by authRepository.authState.collectAsStateWithLifecycle()

    // `rememberModalBottomSheetState` 已废弃，官方替代：
    // rememberBottomSheetState(initialValue = Hidden, enabledValues = ...)
    // 原 `skipPartiallyExpanded = true` 对应「不得停留在半展开」，
    // 即 enabledValues 里去掉 PartiallyExpanded。
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )

    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.sendIntent(SettingsUiIntent.InstallCustomFont(it))
        }
    }

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is SettingsUiEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                is SettingsUiEffect.HapticFeedback -> {}
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        text = "设置",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBackClick,
                        shape = ShapeCache.smoothPill,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 0. IT之家账号与互动会话
            SectionHeader(
                title = "IT之家账号",
                icon = Icons.Outlined.AccountCircle,
                badge = if (authState.isLoggedIn) "已登录" else "未登录"
            )

            Card(
                shape = ShapeCache.smooth24,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (authState.isLoggedIn && !authState.avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = authState.avatarUrl,
                                contentDescription = authState.userNick,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(ShapeCache.smoothPill)
                            )
                        } else {
                            Surface(
                                shape = ShapeCache.smoothPill,
                                color = if (authState.isLoggedIn) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.AccountCircle,
                                        contentDescription = null,
                                        tint = if (authState.isLoggedIn) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (authState.isLoggedIn) authState.userNick else "未登录 IT之家账号",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (authState.isLoggedIn) {
                                    "会话已通过 Android Keystore AES-GCM 加密托管"
                                } else {
                                    "登录后可发表评论、回复楼中楼、传图与赞/踩互动"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (authState.isLoggedIn) {
                            FilledTonalButton(
                                onClick = {
                                    coroutineScope.launch {
                                        val res = authRepository.refreshLoginState()
                                        Toast.makeText(
                                            context,
                                            if (res.isSuccess) "账号状态已同步" else "会话校验失败：${res.exceptionOrNull()?.localizedMessage ?: ""}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                enabled = !authState.isChecking,
                                shape = ShapeCache.smoothPill,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (authState.isChecking) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("刷新状态", style = MaterialTheme.typography.labelLarge)
                            }

                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        authRepository.logout()
                                        Toast.makeText(context, "已退出登录", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = ShapeCache.smoothPill
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.Logout,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("退出登录", style = MaterialTheme.typography.labelLarge)
                            }
                        } else {
                            Button(
                                onClick = onLoginClick,
                                shape = ShapeCache.smoothPill,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AccountCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("立即登录 IT之家", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 1. 外观与表现力
            SectionHeader(title = "外观与表现力", icon = Icons.Outlined.Palette)

            Card(
                shape = ShapeCache.smooth24,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 主题外观选择（MD3E Connected ButtonGroup）
                    Text(
                        text = "主题模式",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val connectedLeadingShapes = ButtonGroupDefaults.connectedLeadingButtonShapes()
                    val connectedMiddleShapes = ButtonGroupDefaults.connectedMiddleButtonShapes()
                    val connectedTrailingShapes = ButtonGroupDefaults.connectedTrailingButtonShapes()

                    ButtonGroup(
                        overflowIndicator = { menuState ->
                            ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
                        },
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            val shapes = when (index) {
                                0 -> connectedLeadingShapes
                                ThemeMode.entries.lastIndex -> connectedTrailingShapes
                                else -> connectedMiddleShapes
                            }
                            customItem(
                                buttonGroupContent = {
                                    val btnInteraction = remember { MutableInteractionSource() }
                                    FilledTonalToggleButton(
                                        checked = uiState.themeMode == mode,
                                        onCheckedChange = {
                                            viewModel.sendIntent(SettingsUiIntent.SetThemeMode(mode))
                                        },
                                        shapes = shapes,
                                        interactionSource = btnInteraction,
                                        modifier = Modifier
                                            .weight(1f)
                                            .animateWidth(btnInteraction)
                                    ) {
                                        Text(
                                            text = mode.title,
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 1
                                        )
                                    }
                                },
                                menuContent = { menuState ->
                                    DropdownMenuItem(
                                        text = { Text(mode.title) },
                                        onClick = {
                                            viewModel.sendIntent(SettingsUiIntent.SetThemeMode(mode))
                                            menuState.dismiss()
                                        }
                                    )
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "动态色彩 (Material You)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "基于壁纸色谱自动生成 MD3 Expressive 主题色",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Switch(
                            checked = uiState.dynamicColorEnabled,
                            onCheckedChange = { viewModel.sendIntent(SettingsUiIntent.SetDynamicColor(it)) }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "高清图片模式",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "文章流与正文优先拉取原分辨率高清大图（关闭则省流压缩）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Switch(
                            checked = uiState.highQualityImage,
                            onCheckedChange = { viewModel.sendIntent(SettingsUiIntent.SetHighQualityImage(it)) }
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // 正文阅读字号缩放（MD3E Connected ButtonGroup）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "正文阅读字号",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Surface(
                            shape = ShapeCache.smoothPill,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "${(uiState.articleFontScale * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    ButtonGroup(
                        overflowIndicator = { menuState ->
                            ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
                        },
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FONT_SCALE_PRESETS.forEachIndexed { index, (label, scaleValue) ->
                            val shapes = when (index) {
                                0 -> connectedLeadingShapes
                                FONT_SCALE_PRESETS.lastIndex -> connectedTrailingShapes
                                else -> connectedMiddleShapes
                            }
                            val isSelected = kotlin.math.abs(uiState.articleFontScale - scaleValue) < 0.03f
                            customItem(
                                buttonGroupContent = {
                                    val btnInteraction = remember { MutableInteractionSource() }
                                    FilledTonalToggleButton(
                                        checked = isSelected,
                                        onCheckedChange = {
                                            viewModel.sendIntent(SettingsUiIntent.SetArticleFontScale(scaleValue))
                                        },
                                        shapes = shapes,
                                        interactionSource = btnInteraction,
                                        modifier = Modifier
                                            .weight(1f)
                                            .animateWidth(btnInteraction)
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 1
                                        )
                                    }
                                },
                                menuContent = { menuState ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            viewModel.sendIntent(SettingsUiIntent.SetArticleFontScale(scaleValue))
                                            menuState.dismiss()
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 2. 预见式预测返回与转场动画
            SectionHeader(
                title = "预见式返回与转场动效",
                icon = Icons.Outlined.MotionPhotosOn,
                badge = "${BackAnimationType.entries.size} 种风格"
            )

            Card(
                shape = ShapeCache.smooth24,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BackAnimationType.entries.forEach { animOption ->
                        val isSelected = uiState.backAnimation == animOption
                        SelectableMotionTile(
                            title = animOption.title,
                            summary = animOption.summary,
                            badge = animOption.badge,
                            isSelected = isSelected,
                            onClick = {
                                viewModel.sendIntent(SettingsUiIntent.SetBackAnimation(animOption))
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 3. 字体设置与自定义字体
            SectionHeader(
                title = "字体排版与个性化",
                icon = Icons.Outlined.FontDownload,
                badge = if (uiState.useSystemFont) "系统字体" else if (uiState.customFontPath != null) "外部字体" else "内置默认"
            )

            Card(
                shape = ShapeCache.smooth24,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // “使用系统字体”独立开关
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "使用系统字体",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "开启后停用内置圆润字体，使用系统自带中文字型",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Switch(
                            checked = uiState.useSystemFont,
                            onCheckedChange = { viewModel.sendIntent(SettingsUiIntent.SetUseSystemFont(it)) }
                        )
                    }

                    // 当前生效字体状态卡片
                    Surface(
                        shape = ShapeCache.smooth16,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = ShapeCache.smooth12,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                val activeFontTitle = when {
                                    uiState.useSystemFont -> "系统默认字体"
                                    uiState.customFontPath != null -> uiState.customFontName ?: "已激活外部字体"
                                    else -> BUNDLED_FONT_NAME
                                }
                                val activeFontSubtitle = when {
                                    uiState.useSystemFont -> "跟随 Android 系统全局字体渲染"
                                    uiState.customFontPath != null -> "大小：${formatFileSize(uiState.customFontSizeBytes)} · 外部文件"
                                    else -> "内置 MD3 Expressive 圆润中文字型 · 默认激活"
                                }

                                Text(
                                    text = activeFontTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = activeFontSubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }

                    // 内置字体致谢：仅在真正使用内置字体时展示，避免与其他字体状态混淆。
                    // 字体以 SIL OFL 1.1 授权分发，这里给出出处既是致谢，也方便用户自行获取。
                    if (!uiState.useSystemFont && uiState.customFontPath == null) {
                        Surface(
                            shape = ShapeCache.smooth12,
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.FavoriteBorder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = BUNDLED_FONT_CREDIT,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = {
                                        uriHandler.openUri(FONT_SOURCE_URL)
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "字体主页",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }

                    // 动态即时排版字型展示盒
                    val previewFontFamily: FontFamily = remember(uiState.useSystemFont, uiState.customFontPath) {
                        resolveFontFamily(
                            context = context,
                            useSystemFont = uiState.useSystemFont,
                            customFontPath = uiState.customFontPath
                        )
                    }

                    Surface(
                        shape = ShapeCache.smooth16,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "字体实时排版预览",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "ITYou · 典雅纯净排版体验",
                                fontFamily = previewFontFamily,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "The quick brown fox jumps over the lazy dog. 0123456789",
                                fontFamily = previewFontFamily,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 操作按钮组
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                fontPickerLauncher.launch(
                                    arrayOf("font/*", "application/x-font-ttf", "application/x-font-opentype", "application/octet-stream", "*/*")
                                )
                            },
                            shape = ShapeCache.smoothPill,
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.isInstallingFont
                        ) {
                            if (uiState.isInstallingFont) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("正在安装...", style = MaterialTheme.typography.labelLarge)
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.FileUpload,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (uiState.customFontPath != null) "更换外部字体" else "安装外部字体",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }

                        if (uiState.customFontPath != null) {
                            OutlinedButton(
                                onClick = { viewModel.sendIntent(SettingsUiIntent.ClearCustomFont) },
                                shape = ShapeCache.smoothPill
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "移除外部", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 4. 关于与版本跟踪
            SectionHeader(title = "关于与版本跟踪", icon = Icons.Outlined.History)

            Card(
                shape = ShapeCache.smooth24,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "ITYou",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Material 3 Expressive 架构版",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        Surface(
                            shape = ShapeCache.smoothPill,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "v${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "基于 Android 14+ 预见式预测返回、内置馬路口圓體与纯 Compose 渲染构建的现代科技新闻阅读器。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { viewModel.sendIntent(SettingsUiIntent.SetTimelineSheetVisible(true)) },
                            shape = ShapeCache.smoothPill,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.History,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "版本演进日志",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        FilledTonalButton(
                            onClick = {
                                uriHandler.openUri("https://github.com/NecoArc-Chaos/ITYou")
                            },
                            shape = ShapeCache.smoothPill,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Code,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "开源仓库",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (uiState.showTimelineSheet) {
        VersionTimelineSheet(
            sheetState = sheetState,
            onDismissRequest = { viewModel.sendIntent(SettingsUiIntent.SetTimelineSheetVisible(false)) }
        )
    }
}

/**
 * 分区标题小组件
 */
@Composable
private fun SectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badge: String? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        badge?.let {
            Surface(
                shape = ShapeCache.smoothPill,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

/**
 * 具有 PixelPlayer 质感的表现型动效选择磁贴
 */
@Composable
private fun SelectableMotionTile(
    title: String,
    summary: String,
    badge: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "TileScale"
    )

    // 纯粹通过容器色阶表达选中态层级，完全不使用硬投影
    Surface(
        shape = ShapeCache.smooth16,
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(ShapeCache.smooth16)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = ShapeCache.smoothPill,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        }
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            RadioButton(
                selected = isSelected,
                onClick = onClick
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) {
        String.format(Locale.US, "%.1f MB", mb)
    } else {
        String.format(Locale.US, "%.1f KB", kb)
    }
}

/** 正文阅读字号预设档位 */
private val FONT_SCALE_PRESETS = listOf(
    "小" to 0.85f,
    "标准" to 1.00f,
    "大" to 1.15f,
    "特大" to 1.30f
)

/** 内置字体（馬路口圓體）的项目主页。 */
private const val FONT_SOURCE_URL = "https://github.com/max32002/maruko-gothic"
