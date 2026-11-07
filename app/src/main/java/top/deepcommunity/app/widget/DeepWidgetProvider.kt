package top.deepcommunity.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import top.deepcommunity.app.MainActivity
import top.deepcommunity.app.R
import top.deepcommunity.app.core.common.Constants
import top.deepcommunity.app.core.network.TokenManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class DeepWidgetProvider : AppWidgetProvider() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val ACTION_REFRESH_WIDGET = "top.deepcommunity.app.widget.ACTION_REFRESH"
        const val ACTION_CHECKIN_WIDGET = "top.deepcommunity.app.widget.ACTION_CHECKIN"

        fun updateAllWidgets(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val thisWidget = ComponentName(context, DeepWidgetProvider::class.java)
                val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
                val intent = Intent(context, DeepWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, allWidgetIds)
                }
                context.sendBroadcast(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // 1. 同步瞬间渲染：确保 Launcher 添加小组件时 0 毫秒完成加载，绝不报“载入出错”
        val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        for (widgetId in appWidgetIds) {
            try {
                val defaultViews = buildRemoteViews(
                    context = context,
                    title = "正在探索极客世界，点击查看最新社区讨论…",
                    author = "@深度社区 · 极客日常",
                    timeText = "$currentTime 同步"
                )
                appWidgetManager.updateAppWidget(widgetId, defaultViews)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. 异步网络增量更新：拉取后端最新动态并平滑刷新
        scope.launch {
            try {
                val latestPost = fetchLatestPostSummary()
                if (latestPost != null) {
                    val updateTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                    for (widgetId in appWidgetIds) {
                        val views = buildRemoteViews(
                            context = context,
                            title = latestPost.first,
                            author = "@${latestPost.second} · 极客热议",
                            timeText = "$updateTime 更新"
                        )
                        appWidgetManager.updateAppWidget(widgetId, views)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_WIDGET) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val thisWidget = ComponentName(context, DeepWidgetProvider::class.java)
                val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
                onUpdate(context, appWidgetManager, allWidgetIds)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else if (intent.action == ACTION_CHECKIN_WIDGET) {
            scope.launch {
                val resultMsg = performWidgetCheckin(context)
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, resultMsg, android.widget.Toast.LENGTH_SHORT).show()
                }
                updateAllWidgets(context)
            }
        }
    }

    private fun buildRemoteViews(
        context: Context,
        title: String,
        author: String,
        timeText: String
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_deep_community)
        views.setTextViewText(R.id.widget_post_title, title)
        views.setTextViewText(R.id.widget_post_author, author)
        views.setTextViewText(R.id.widget_update_time, timeText)

        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        // 一键签到打卡
        val checkinIntent = Intent(context, DeepWidgetProvider::class.java).apply {
            action = ACTION_CHECKIN_WIDGET
        }
        val checkinPendingIntent = PendingIntent.getBroadcast(context, 4, checkinIntent, flag)
        views.setOnClickPendingIntent(R.id.widget_btn_checkin, checkinPendingIntent)

        // 主体卡片点击：打开应用主页
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mainPendingIntent = PendingIntent.getActivity(context, 0, mainIntent, flag)
        views.setOnClickPendingIntent(R.id.widget_card_body, mainPendingIntent)

        // 快捷发帖点击：打开应用并发帖
        val composeIntent = Intent(context, MainActivity::class.java).apply {
            action = "top.deepcommunity.app.ACTION_CREATE_POST"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val composePendingIntent = PendingIntent.getActivity(context, 1, composeIntent, flag)
        views.setOnClickPendingIntent(R.id.widget_btn_compose, composePendingIntent)

        // 社群按钮点击：打开主页
        val chatIntent = Intent(context, MainActivity::class.java).apply {
            action = "top.deepcommunity.app.ACTION_CHAT"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val chatPendingIntent = PendingIntent.getActivity(context, 2, chatIntent, flag)
        views.setOnClickPendingIntent(R.id.widget_btn_chat, chatPendingIntent)

        // 刷新按钮点击：触发更新广播
        val refreshIntent = Intent(context, DeepWidgetProvider::class.java).apply {
            action = ACTION_REFRESH_WIDGET
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(context, 3, refreshIntent, flag)
        views.setOnClickPendingIntent(R.id.widget_btn_refresh, refreshPendingIntent)

        return views
    }

    private fun fetchLatestPostSummary(): Pair<String, String>? {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
            val url = "${Constants.BASE_URL.trimEnd('/')}/api/posts?page=1&size=1&sort_by=latest"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val list = json.optJSONArray("items") ?: json.optJSONArray("list") ?: json.optJSONArray("data")
                if (list != null && list.length() > 0) {
                    val item = list.getJSONObject(0)
                    val title = item.optString("title", "最新动态")
                    val author = item.optString("author_nickname", item.optString("author_username", "极客居民"))
                    Pair(title, author)
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun tokenManager(): TokenManager
    }

    private fun performWidgetCheckin(context: Context): String {
        return try {
            val appContext = context.applicationContext
            // 使用 EncryptedSharedPreferences 管理器获取解密后的真实 Token
            val token = try {
                val entryPoint = EntryPointAccessors.fromApplication(appContext, WidgetEntryPoint::class.java)
                entryPoint.tokenManager().getAccessToken()
            } catch (e: Exception) {
                TokenManager(appContext).getAccessToken()
            }

            if (token.isNullOrBlank()) {
                return "请先打开深度社区登录账号"
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(6, TimeUnit.SECONDS)
                .build()
            val url = "${Constants.BASE_URL.trimEnd('/')}/api/users/checkin"
            val emptyBody = okhttp3.RequestBody.create(null, ByteArray(0))
            val request = Request.Builder()
                .url(url)
                .post(emptyBody)
                .header("Authorization", "Bearer $token")
                .build()

            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                try {
                    appContext.getSharedPreferences("app_checkin_prefs", Context.MODE_PRIVATE)
                        .edit().putString("last_checkin_date", todayStr).apply()
                } catch (e: Exception) { e.printStackTrace() }
                "⚡ 今日签到成功！经验 +5"
            } else {
                val errorBody = response.body?.string() ?: ""
                if (errorBody.contains("已经完成") || errorBody.contains("今日已经") || response.code == 400) {
                    try {
                        appContext.getSharedPreferences("app_checkin_prefs", Context.MODE_PRIVATE)
                            .edit().putString("last_checkin_date", todayStr).apply()
                    } catch (e: Exception) { e.printStackTrace() }
                    "✓ 今日已经完成签到打卡"
                } else if (response.code == 401) {
                    "登录已过期，请打开应用重新登录"
                } else {
                    "今日已签到或打卡异常"
                }
            }
        } catch (e: Exception) {
            "打卡网络连接异常"
        }
    }
}
