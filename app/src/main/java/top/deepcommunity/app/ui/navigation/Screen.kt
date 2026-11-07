package top.deepcommunity.app.ui.navigation

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Login : Screen("login")
    data object Main : Screen("main")
    data object PostDetail : Screen("post_detail/{postId}") {
        fun createRoute(postId: String) = "post_detail/$postId"
    }
    data object CreatePost : Screen("create_post")
    data object ChatDetail : Screen("chat_detail/{convId}") {
        fun createRoute(convId: String) = "chat_detail/$convId"
    }
}
