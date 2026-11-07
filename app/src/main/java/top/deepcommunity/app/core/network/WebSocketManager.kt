package top.deepcommunity.app.core.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import top.deepcommunity.app.core.common.Constants
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val tokenManager: TokenManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var webSocket: WebSocket? = null
    private var isConnected = false

    private val _incomingMessages = MutableSharedFlow<String>()
    val incomingMessages: SharedFlow<String> = _incomingMessages.asSharedFlow()

    fun connect() {
        if (isConnected) return
        val token = tokenManager.getAccessToken() ?: return

        // 接口文档规定：通过 WebSocket 子协议头传递鉴权，不要把令牌放在 URL Query 里
        // Sec-WebSocket-Protocol: access_token, <access_token>
        val request = Request.Builder()
            .url(Constants.WS_URL)
            .header("Sec-WebSocket-Protocol", "access_token, $token")
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                startHeartbeat()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch {
                    _incomingMessages.emit(text)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                // 断线重试
                scope.launch {
                    delay(3000)
                    connect()
                }
            }
        })
    }

    private fun startHeartbeat() {
        scope.launch {
            while (isActive && isConnected) {
                delay(30000) // 30秒心跳
                webSocket?.send("""{"type":"ping"}""")
            }
        }
    }

    fun sendMessage(json: String): Boolean {
        return webSocket?.send(json) ?: false
    }

    fun disconnect() {
        isConnected = false
        webSocket?.close(1000, "Normal closure")
        webSocket = null
    }
}
