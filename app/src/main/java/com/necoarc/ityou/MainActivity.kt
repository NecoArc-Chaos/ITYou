package com.necoarc.ityou

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.necoarc.ityou.data.repository.SettingsRepository
import com.necoarc.ityou.ui.screens.detail.DetailScreen
import com.necoarc.ityou.ui.screens.home.HomeScreen
import com.necoarc.ityou.ui.screens.settings.SettingsScreen
import com.necoarc.ityou.ui.theme.ITYouTheme
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val settingsRepo = SettingsRepository.getInstance(context)
            val settings by settingsRepo.settings.collectAsState()

            ITYouTheme(
                dynamicColor = settings.dynamicColorEnabled,
                fontFamily = settings.selectedFont.fontFamily
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ITYouNavApp()
                }
            }
        }
    }
}

@Composable
fun ITYouNavApp() {
    val navController = rememberNavController()

    // 物理弹簧规范：LowBouncy + StiffnessMediumLow
    val navSpring = spring<IntOffset>(
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
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = navSpring
                ) + fadeIn()
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = navSpring
                ) + fadeOut()
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = navSpring
                ) + fadeIn()
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
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = navSpring
                ) + fadeIn()
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = navSpring
                ) + fadeOut()
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = navSpring
                ) + fadeOut()
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
                }
            )
        }

        composable(
            route = "settings",
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = navSpring
                ) + fadeIn()
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = navSpring
                ) + fadeOut()
            }
        ) {
            SettingsScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}
