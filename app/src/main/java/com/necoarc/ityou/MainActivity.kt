package com.necoarc.ityou

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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
            ITYouTheme {
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

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(
                onArticleClick = { articleId, url ->
                    val encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8.toString())
                    navController.navigate("detail/$articleId?url=$encodedUrl")
                },
                onSettingsClick = {
                    navController.navigate("settings")
                }
            )
        }

        composable(
            route = "detail/{articleId}?url={url}",
            arguments = listOf(
                navArgument("articleId") { type = NavType.StringType },
                navArgument("url") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val articleId = backStackEntry.arguments?.getString("articleId").orEmpty()
            val rawUrl = backStackEntry.arguments?.getString("url").orEmpty()
            val decodedUrl = try {
                URLDecoder.decode(rawUrl, StandardCharsets.UTF_8.toString())
            } catch (_: Exception) {
                rawUrl
            }

            DetailScreen(
                articleId = articleId,
                articleUrl = decodedUrl,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

        composable("settings") {
            SettingsScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}
