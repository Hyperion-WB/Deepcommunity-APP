package top.deepcommunity.app.core.common

object Constants {
    const val BASE_URL = "https://deepcommunity.top/"
    const val WS_URL = "wss://deepcommunity.top/api/chat/ws"
    
    // 分页默认大小
    const val DEFAULT_PAGE_SIZE = 20
    
    // 本地加密存储文件名
    const val PREFS_SECURE_AUTH = "deep_secure_auth_prefs"
    const val KEY_ACCESS_TOKEN = "key_access_token"
    const val KEY_CURRENT_USER_JSON = "key_current_user_json"
    
    // Cookie 持久化文件名
    const val COOKIE_PREFS_NAME = "deep_persistent_cookies"
}
