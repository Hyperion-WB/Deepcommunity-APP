package top.deepcommunity.app.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchHistoryManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("deep_search_history", Context.MODE_PRIVATE)
    private val keyHistory = "history_items"

    private val _historyFlow = MutableStateFlow(loadHistory())
    val historyFlow: StateFlow<List<String>> = _historyFlow.asStateFlow()

    private fun loadHistory(): List<String> {
        val raw = prefs.getString(keyHistory, "[]") ?: "[]"
        return try {
            val jsonArray = JSONArray(raw)
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.optString(i)
                if (item.isNotBlank()) list.add(item)
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveHistory(list: List<String>) {
        val jsonArray = JSONArray()
        list.forEach { jsonArray.put(it) }
        prefs.edit().putString(keyHistory, jsonArray.toString()).apply()
        _historyFlow.value = list
    }

    /**
     * 添加搜索历史（去重，最新搜索置顶，最多保留 20 条）
     */
    fun addSearchQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val current = _historyFlow.value.toMutableList()
        current.remove(trimmed)
        current.add(0, trimmed)
        val trimmedList = if (current.size > 20) current.subList(0, 20) else current
        saveHistory(trimmedList)
    }

    /**
     * 删除单条搜索历史
     */
    fun removeSearchQuery(query: String) {
        val current = _historyFlow.value.toMutableList()
        if (current.remove(query)) {
            saveHistory(current)
        }
    }

    /**
     * 清空全部搜索历史
     */
    fun clearHistory() {
        saveHistory(emptyList())
    }

    /**
     * 官方/极客推荐热门标签
     */
    fun getHotRecommendations(): List<String> {
        return listOf(
            "Jetpack Compose",
            "Kotlin",
            "液态玻璃",
            "Android 15",
            "开源动态",
            "技术架构",
            "极客日常",
            "UI/UX"
        )
    }
}
