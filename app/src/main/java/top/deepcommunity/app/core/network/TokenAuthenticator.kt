package top.deepcommunity.app.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import top.deepcommunity.app.core.common.Constants
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenManager: TokenManager,
    private val cookieJar: PersistentCookieJar,
    private val okHttpClientProvider: Provider<OkHttpClient>
) : Authenticator {

    @Synchronized
    override fun authenticate(route: Route?, response: Response): Request? {
        val originalRequest = response.request
        val path = originalRequest.url.encodedPath

        // 避免死循环：若刷新接口或登录接口本身返回 401，不再尝试刷新
        if (path.contains("/api/auth/refresh") || path.contains("/api/auth/login")) {
            tokenManager.clearAll()
            cookieJar.clearAllCookies()
            return null
        }

        // 检查原请求携带的 token 是否已经过时（可能其他并发线程已经成功刷新过了）
        val currentToken = tokenManager.getAccessToken()
        val authHeader = originalRequest.header("Authorization")
        if (authHeader != null && authHeader != "Bearer $currentToken" && !currentToken.isNullOrBlank()) {
            // 直接换用最新的 token 重试
            return originalRequest.newBuilder()
                .header("Authorization", "Bearer $currentToken")
                .build()
        }

        // 发起同步刷新请求 POST /api/auth/refresh (利用 CookieJar 自动携带 HttpOnly Cookie)
        val refreshClient = okHttpClientProvider.get().newBuilder()
            .authenticator(Authenticator.NONE) // 刷新本身不加 authenticator
            .build()

        val refreshRequest = Request.Builder()
            .url(Constants.BASE_URL + "api/auth/refresh")
            .post("{}".toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        return try {
            val refreshResponse = refreshClient.newCall(refreshRequest).execute()
            if (refreshResponse.isSuccessful) {
                val responseBody = refreshResponse.body?.string()
                if (!responseBody.isNullOrBlank()) {
                    val json = Json.parseToJsonElement(responseBody).jsonObject
                    val newToken = json["access_token"]?.jsonPrimitive?.content
                    if (!newToken.isNullOrBlank()) {
                        tokenManager.saveAccessToken(newToken)
                        return originalRequest.newBuilder()
                            .header("Authorization", "Bearer $newToken")
                            .build()
                    }
                }
            }
            // 刷新失败，凭据彻底失效，清理本地登录态
            tokenManager.clearAll()
            cookieJar.clearAllCookies()
            null
        } catch (e: Exception) {
            null
        }
    }
}
