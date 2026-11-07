package top.deepcommunity.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
import top.deepcommunity.app.ui.auth.LoginScreen
import top.deepcommunity.app.ui.chat.ChatScreen
import top.deepcommunity.app.ui.main.MainScreen
import top.deepcommunity.app.ui.post.CreatePostScreen
import top.deepcommunity.app.ui.post.PostDetailScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(300)
            )
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(300)
            )
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(300)
            )
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(300)
            )
        }
    ) {
        // 登录页面
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        // 主容器页面（内嵌悬浮浮岛导航栏 + 首页/发现/通知/我的）
        composable(Screen.Main.route) {
            MainScreen(
                onNavigateToPostDetail = { postId ->
                    navController.navigate(Screen.PostDetail.createRoute(postId))
                },
                onNavigateToCreatePost = {
                    navController.navigate(Screen.CreatePost.route)
                },
                onNavigateToChatDetail = { convId ->
                    navController.navigate(Screen.ChatDetail.createRoute(convId))
                },
                onLogoutSuccess = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Main.route) { inclusive = true }
                    }
                }
            )
        }

        // 帖子详情页面
        composable(
            route = Screen.PostDetail.route,
            arguments = listOf(navArgument("postId") { type = NavType.StringType })
        ) {
            PostDetailScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        // 发帖页面
        composable(Screen.CreatePost.route) {
            CreatePostScreen(
                onBackClick = { navController.popBackStack() },
                onPostSuccess = { navController.popBackStack() }
            )
        }

        // 私信会话页面
        composable(
            route = Screen.ChatDetail.route,
            arguments = listOf(navArgument("convId") { type = NavType.StringType })
        ) {
            ChatScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
