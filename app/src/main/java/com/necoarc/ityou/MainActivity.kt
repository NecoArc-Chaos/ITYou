package com.necoarc.ityou

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.necoarc.ityou.data.model.BackAnimationType
import com.necoarc.ityou.data.repository.SettingsRepository
import com.necoarc.ityou.ui.screens.detail.DetailScreen
import com.necoarc.ityou.ui.screens.favorites.FavoritesScreen
import com.necoarc.ityou.ui.screens.home.HomeScreen
import com.necoarc.ityou.ui.screens.settings.SettingsScreen
import com.necoarc.ityou.ui.theme.ITYouTheme
import com.necoarc.ityou.ui.theme.resolveFontFamily
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val settingsRepo = remember(context) { SettingsRepository.getInstance(context) }

            // collectAsStateWithLifecycle：退到后台立即停止收集，避免回前台时集中重组
            val settings by settingsRepo.settings.collectAsStateWithLifecycle()

            // 性能要点：resolveFontFamily 需要读资产/文件并解析字体表，
            // 必须 remember，避免每次普通重组都重复构建 Typeface。
            // 默认加载工程内置资产（馬路口圓體），同时支持用户选择系统字体或自定义字体。
            val dynamicFontFamily = remember(settings.useSystemFont, settings.customFontPath) {
                resolveFontFamily(
                    context = context,
                    useSystemFont = settings.useSystemFont,
                    customFontPath = settings.customFontPath
                )
            }

            ITYouTheme(
                dynamicColor = settings.dynamicColorEnabled,
                fontFamily = dynamicFontFamily
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ITYouNavApp(backAnimation = settings.backAnimation)
                }
            }
        }
    }
}

@Composable
fun ITYouNavApp(backAnimation: BackAnimationType) {
    val navController = rememberNavController()

    // MD3E 物理阻尼弹簧预设
    val navSpring = spring<IntOffset>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
    val scaleSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable(
            route = "home",
            enterTransition = {
                when (backAnimation) {
                    BackAnimationType.SPRING_SLIDE -> slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, navSpring) + fadeIn()
                    BackAnimationType.CONTAINER_SCALE -> scaleIn(initialScale = 0.92f, animationSpec = scaleSpring) + fadeIn()
                    BackAnimationType.SUBTLE_FADE -> fadeIn(animationSpec = tween(220))
                    BackAnimationType.DRAWER_LIFT -> slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Down, navSpring) + fadeIn()
                }
            },
            exitTransition = {
                when (backAnimation) {
                    BackAnimationType.SPRING_SLIDE -> slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, navSpring) + fadeOut()
                    BackAnimationType.CONTAINER_SCALE -> scaleOut(targetScale = 1.08f, animationSpec = scaleSpring) + fadeOut()
                    BackAnimationType.SUBTLE_FADE -> fadeOut(animationSpec = tween(180))
                    BackAnimationType.DRAWER_LIFT -> slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Up, navSpring) + fadeOut()
                }
            },
            popEnterTransition = {
                when (backAnimation) {
                    BackAnimationType.SPRING_SLIDE -> slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, navSpring) + fadeIn()
                    BackAnimationType.CONTAINER_SCALE -> scaleIn(initialScale = 1.06f, animationSpec = scaleSpring) + fadeIn()
                    BackAnimationType.SUBTLE_FADE -> fadeIn(animationSpec = tween(220))
                    BackAnimationType.DRAWER_LIFT -> fadeIn(animationSpec = tween(200))
                }
            }
        ) {
            HomeScreen(
                onArticleClick = { articleId, url, title, author, pubTime ->
                    val encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8.toString())
                    val encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8.toString())
                    val encodedAuthor = URLEncoder.encode(author, StandardCharsets.UTF_8.toString())
                    val encodedPubTime = URLEncoder.encode(pubTime, StandardCharsets.UTF_8.toString())
                    navController.navigate("detail/$articleId?url=$encodedUrl&title=$encodedTitle&author=$encodedAuthor&pubTime=$encodedPubTime")
                },
                onSettingsClick = {
                    navController.navigate("settings")
                },
                onFavoritesClick = {
                    navController.navigate("favorites")
                }
            )
        }

        composable(
            route = "detail/{articleId}?url={url}&title={title}&author={author}&pubTime={pubTime}",
            arguments = listOf(
                navArgument("articleId") { type = NavType.StringType },
                navArgument("url") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("title") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("author") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("pubTime") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            ),
            enterTransition = {
                when (backAnimation) {
                    BackAnimationType.SPRING_SLIDE -> slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, navSpring) + fadeIn()
                    BackAnimationType.CONTAINER_SCALE -> scaleIn(initialScale = 0.90f, animationSpec = scaleSpring) + fadeIn()
                    BackAnimationType.SUBTLE_FADE -> fadeIn(animationSpec = tween(220))
                    BackAnimationType.DRAWER_LIFT -> slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Up, navSpring) + fadeIn()
                }
            },
            exitTransition = {
                when (backAnimation) {
                    BackAnimationType.SPRING_SLIDE -> slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, navSpring) + fadeOut()
                    BackAnimationType.CONTAINER_SCALE -> scaleOut(targetScale = 1.08f, animationSpec = scaleSpring) + fadeOut()
                    BackAnimationType.SUBTLE_FADE -> fadeOut(animationSpec = tween(180))
                    BackAnimationType.DRAWER_LIFT -> slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Up, navSpring) + fadeOut()
                }
            },
            popExitTransition = {
                when (backAnimation) {
                    BackAnimationType.SPRING_SLIDE -> slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, navSpring) + fadeOut()
                    BackAnimationType.CONTAINER_SCALE -> scaleOut(targetScale = 0.90f, animationSpec = scaleSpring) + fadeOut()
                    BackAnimationType.SUBTLE_FADE -> fadeOut(animationSpec = tween(180))
                    BackAnimationType.DRAWER_LIFT -> slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Down, navSpring) + fadeOut()
                }
            }
        ) { backStackEntry ->
            val articleId = backStackEntry.arguments?.getString("articleId").orEmpty()
            val rawUrl = backStackEntry.arguments?.getString("url").orEmpty()
            val rawTitle = backStackEntry.arguments?.getString("title").orEmpty()
            val rawAuthor = backStackEntry.arguments?.getString("author").orEmpty()
            val rawPubTime = backStackEntry.arguments?.getString("pubTime").orEmpty()

            val decodedUrl = try {
                URLDecoder.decode(rawUrl, StandardCharsets.UTF_8.toString())
            } catch (_: Exception) {
                rawUrl
            }
            val decodedTitle = try {
                URLDecoder.decode(rawTitle, StandardCharsets.UTF_8.toString())
            } catch (_: Exception) {
                rawTitle
            }
            val decodedAuthor = try {
                URLDecoder.decode(rawAuthor, StandardCharsets.UTF_8.toString())
            } catch (_: Exception) {
                rawAuthor
            }
            val decodedPubTime = try {
                URLDecoder.decode(rawPubTime, StandardCharsets.UTF_8.toString())
            } catch (_: Exception) {
                rawPubTime
            }

            DetailScreen(
                articleId = articleId,
                articleUrl = decodedUrl,
                previewTitle = decodedTitle,
                previewAuthor = decodedAuthor,
                previewPubTime = decodedPubTime,
                onBackClick = {
                    navController.popBackStack()
                },
                onRelatedArticleClick = { relId, relUrl, relTitle, relPubTime ->
                    val encodedRelUrl = URLEncoder.encode(relUrl, StandardCharsets.UTF_8.toString())
                    val encodedRelTitle = URLEncoder.encode(relTitle, StandardCharsets.UTF_8.toString())
                    val encodedRelPubTime = URLEncoder.encode(relPubTime, StandardCharsets.UTF_8.toString())
                    navController.navigate("detail/$relId?url=$encodedRelUrl&title=$encodedRelTitle&author=IT之家&pubTime=$encodedRelPubTime")
                }
            )
        }

        composable(
            route = "settings",
            enterTransition = {
                when (backAnimation) {
                    BackAnimationType.SPRING_SLIDE -> slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, navSpring) + fadeIn()
                    BackAnimationType.CONTAINER_SCALE -> scaleIn(initialScale = 0.90f, animationSpec = scaleSpring) + fadeIn()
                    BackAnimationType.SUBTLE_FADE -> fadeIn(animationSpec = tween(220))
                    BackAnimationType.DRAWER_LIFT -> slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Up, navSpring) + fadeIn()
                }
            },
            popExitTransition = {
                when (backAnimation) {
                    BackAnimationType.SPRING_SLIDE -> slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, navSpring) + fadeOut()
                    BackAnimationType.CONTAINER_SCALE -> scaleOut(targetScale = 0.90f, animationSpec = scaleSpring) + fadeOut()
                    BackAnimationType.SUBTLE_FADE -> fadeOut(animationSpec = tween(180))
                    BackAnimationType.DRAWER_LIFT -> slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Down, navSpring) + fadeOut()
                }
            }
        ) {
            SettingsScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = "favorites",
            enterTransition = {
                when (backAnimation) {
                    BackAnimationType.SPRING_SLIDE -> slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, navSpring) + fadeIn()
                    BackAnimationType.CONTAINER_SCALE -> scaleIn(initialScale = 0.90f, animationSpec = scaleSpring) + fadeIn()
                    BackAnimationType.SUBTLE_FADE -> fadeIn(animationSpec = tween(220))
                    BackAnimationType.DRAWER_LIFT -> slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Up, navSpring) + fadeIn()
                }
            },
            popExitTransition = {
                when (backAnimation) {
                    BackAnimationType.SPRING_SLIDE -> slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, navSpring) + fadeOut()
                    BackAnimationType.CONTAINER_SCALE -> scaleOut(targetScale = 0.90f, animationSpec = scaleSpring) + fadeOut()
                    BackAnimationType.SUBTLE_FADE -> fadeOut(animationSpec = tween(180))
                    BackAnimationType.DRAWER_LIFT -> slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Down, navSpring) + fadeOut()
                }
            }
        ) {
            FavoritesScreen(
                onBackClick = {
                    navController.popBackStack()
                },
                onArticleClick = { articleId, url, title, author, pubTime ->
                    // 复用与首页相同的详情路由，保证从收藏进入的详情页行为一致
                    val encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8.toString())
                    val encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8.toString())
                    val encodedAuthor = URLEncoder.encode(author, StandardCharsets.UTF_8.toString())
                    val encodedPubTime = URLEncoder.encode(pubTime, StandardCharsets.UTF_8.toString())
                    navController.navigate("detail/$articleId?url=$encodedUrl&title=$encodedTitle&author=$encodedAuthor&pubTime=$encodedPubTime")
                }
            )
        }
    }
}
