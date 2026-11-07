package top.deepcommunity.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import top.deepcommunity.app.core.common.AppThemeMode
import top.deepcommunity.app.core.common.ThemeManager
import top.deepcommunity.app.core.designsystem.theme.DeepCommunityTheme
import top.deepcommunity.app.core.network.TokenManager
import top.deepcommunity.app.core.notification.DeepNotificationHelper
import top.deepcommunity.app.ui.navigation.AppNavHost
import top.deepcommunity.app.ui.navigation.Screen
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by ThemeManager.themeMode.collectAsState()
            val isSystemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                AppThemeMode.FOLLOW_SYSTEM -> isSystemDark
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }

            DeepCommunityTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val isLoggedIn by tokenManager.isLoggedIn.collectAsState()
                    val startDestination = if (isLoggedIn) Screen.Main.route else Screen.Login.route
                    val navController = rememberNavController()

                    LaunchedEffect(intent) {
                        val currentIntent = intent ?: return@LaunchedEffect
                        val targetId = currentIntent.getStringExtra(DeepNotificationHelper.EXTRA_TARGET_ID)
                        val action = currentIntent.action

                        if (isLoggedIn) {
                            when {
                                !targetId.isNullOrBlank() -> {
                                    navController.navigate(Screen.PostDetail.createRoute(targetId))
                                }
                                action == "top.deepcommunity.app.ACTION_CREATE_POST" || action == android.content.Intent.ACTION_SEND -> {
                                    navController.navigate(Screen.CreatePost.route)
                                }
                                action == "top.deepcommunity.app.ACTION_CHAT" -> {
                                    navController.navigate(Screen.Main.route)
                                }
                            }
                        }
                    }

                    AppNavHost(
                        navController = navController,
                        startDestination = startDestination
                    )
                }
            }
        }
    }
}
