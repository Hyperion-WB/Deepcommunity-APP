package top.deepcommunity.app.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.deepcommunity.app.core.common.Resource
import top.deepcommunity.app.data.local.SearchHistoryManager
import top.deepcommunity.app.data.model.CategoryDto
import top.deepcommunity.app.data.model.PostItemDto
import top.deepcommunity.app.data.model.SystemAnnouncementDto
import top.deepcommunity.app.data.repository.PostRepository
import top.deepcommunity.app.data.repository.UserRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class HomeUiState(
    val posts: List<PostItemDto> = emptyList(),
    val categories: List<CategoryDto> = emptyList(),
    val selectedCategoryId: Int? = null,
    val feedType: String = "recommend", // "recommend" | "following"
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val isEndReached: Boolean = false,
    val selectedFilter: String = "latest", // latest, hot, comments
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val isCheckedIn: Boolean = false,
    val isCheckingIn: Boolean = false,
    val announcement: SystemAnnouncementDto? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val userRepository: UserRepository,
    private val searchHistoryManager: SearchHistoryManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val postRepo: PostRepository get() = postRepository
    val userRepo: UserRepository get() = userRepository

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val searchHistory: StateFlow<List<String>> = searchHistoryManager.historyFlow

    private var currentPage = 1
    private val pageSize = 20
    private var searchJob: Job? = null

    init {
        // 读取本地打卡日期记忆，跨进程、重启应用保持打卡状态
        checkTodayCheckinStatus()
        loadCategories()
        loadAnnouncement()
        loadPosts(isRefresh = true)
    }

    fun loadAnnouncement() {
        viewModelScope.launch {
            postRepository.getCurrentAnnouncement().collect { result ->
                if (result is Resource.Success) {
                    _uiState.update { it.copy(announcement = result.data) }
                }
            }
        }
    }

    private fun checkTodayCheckinStatus() {
        try {
            val prefs = context.getSharedPreferences("app_checkin_prefs", Context.MODE_PRIVATE)
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val lastCheckinDate = prefs.getString("last_checkin_date", null)
            if (lastCheckinDate == today) {
                _uiState.update { it.copy(isCheckedIn = true) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadCategories() {
        viewModelScope.launch {
            postRepository.getCategories().collect { result ->
                if (result is Resource.Success) {
                    _uiState.update { it.copy(categories = result.data) }
                }
            }
        }
    }

    fun selectFeedType(feedType: String) {
        if (_uiState.value.feedType != feedType) {
            _uiState.update { it.copy(feedType = feedType) }
            loadPosts(isRefresh = true)
        }
    }

    fun selectCategory(categoryId: Int?) {
        if (_uiState.value.selectedCategoryId != categoryId) {
            _uiState.update { it.copy(selectedCategoryId = categoryId) }
            loadPosts(isRefresh = true)
        }
    }

    fun selectFilter(filter: String) {
        if (_uiState.value.selectedFilter != filter) {
            _uiState.update { it.copy(selectedFilter = filter) }
            loadPosts(isRefresh = true)
        }
    }

    fun toggleSearch() {
        val newActive = !_uiState.value.isSearchActive
        _uiState.update { it.copy(isSearchActive = newActive) }
        if (!newActive && _uiState.value.searchQuery.isNotEmpty()) {
            _uiState.update { it.copy(searchQuery = "") }
            loadPosts(isRefresh = true)
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(350)
            if (query.trim().isNotBlank()) {
                searchHistoryManager.addSearchQuery(query.trim())
            }
            loadPosts(isRefresh = true)
        }
    }

    fun commitSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isNotBlank()) {
            searchHistoryManager.addSearchQuery(trimmed)
        }
        _uiState.update { it.copy(searchQuery = trimmed) }
        loadPosts(isRefresh = true)
    }

    fun removeSearchHistory(query: String) {
        searchHistoryManager.removeSearchQuery(query)
    }

    fun clearSearchHistory() {
        searchHistoryManager.clearHistory()
    }

    fun loadPosts(isRefresh: Boolean = false) {
        if (isRefresh) {
            currentPage = 1
            _uiState.update { it.copy(isEndReached = false) }
            loadAnnouncement()
            checkTodayCheckinStatus()
        }

        viewModelScope.launch {
            val query = _uiState.value.searchQuery.trim().takeIf { it.isNotEmpty() }
            val isFollowingMode = _uiState.value.feedType == "following"
            val flow = if (isFollowingMode) {
                postRepository.getFollowingFeed(page = currentPage, size = pageSize)
            } else {
                postRepository.getPosts(
                    page = currentPage,
                    size = pageSize,
                    sortBy = _uiState.value.selectedFilter,
                    categoryId = _uiState.value.selectedCategoryId,
                    query = query
                )
            }

            flow.collect { result ->
                when (result) {
                    is Resource.Loading -> {
                        _uiState.update {
                            if (isRefresh) it.copy(isRefreshing = true) else it.copy(isLoading = true)
                        }
                    }
                    is Resource.Success -> {
                        val newPosts = result.data
                        val updatedList = if (isRefresh) newPosts else _uiState.value.posts + newPosts
                        val endReached = newPosts.size < pageSize

                        _uiState.update {
                            it.copy(
                                posts = updatedList,
                                isLoading = false,
                                isRefreshing = false,
                                isLoadingMore = false,
                                isEndReached = endReached,
                                errorMessage = null
                            )
                        }
                    }
                    is Resource.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                isLoadingMore = false,
                                errorMessage = result.message
                            )
                        }
                    }
                }
            }
        }
    }

    fun loadNextPage() {
        if (_uiState.value.isLoading || _uiState.value.isLoadingMore || _uiState.value.isEndReached) return
        currentPage++
        _uiState.update { it.copy(isLoadingMore = true) }
        loadPosts(isRefresh = false)
    }

    fun toggleLike(postId: String) {
        val currentPosts = _uiState.value.posts
        val updated = currentPosts.map { post ->
            if (post.id == postId) {
                val newLiked = !post.isLiked
                val newCount = if (newLiked) post.likeCount + 1 else (post.likeCount - 1).coerceAtLeast(0)
                post.copy(isLiked = newLiked, likeCount = newCount)
            } else post
        }
        _uiState.update { it.copy(posts = updated) }

        viewModelScope.launch {
            postRepository.toggleLike(postId).collect { result ->
                when (result) {
                    is Resource.Success -> {
                        val likeResp = result.data
                        if (likeResp != null) {
                            val calibrated = _uiState.value.posts.map {
                                if (it.id == postId) {
                                    it.copy(isLiked = likeResp.isLiked, likeCount = likeResp.likeCount)
                                } else it
                            }
                            _uiState.update { it.copy(posts = calibrated) }
                        }
                    }
                    is Resource.Error -> {
                        _uiState.update { it.copy(posts = currentPosts) }
                    }
                    else -> {}
                }
            }
        }
    }

    /**
     * 极客一键签到打卡（智能识别状态并持久化记录，防止重复提醒）
     */
    fun checkIn(onResult: (Boolean, String) -> Unit) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        if (_uiState.value.isCheckedIn) {
            onResult(true, "今日已打卡，明日再来获取经验吧~")
            return
        }
        if (_uiState.value.isCheckingIn) return

        _uiState.update { it.copy(isCheckingIn = true) }
        viewModelScope.launch {
            userRepository.checkIn().collect { result ->
                when (result) {
                    is Resource.Success -> {
                        // 标记并保存今日日期
                        try {
                            context.getSharedPreferences("app_checkin_prefs", Context.MODE_PRIVATE)
                                .edit().putString("last_checkin_date", today).apply()
                        } catch (e: Exception) { e.printStackTrace() }

                        _uiState.update { it.copy(isCheckingIn = false, isCheckedIn = true) }
                        onResult(true, result.data)
                    }
                    is Resource.Error -> {
                        val msg = result.message
                        // 如果后端告知今日已经打过卡，则自动校正为“已打卡”状态，避免用户再次误点
                        if (msg.contains("已经") || msg.contains("已签到") || msg.contains("完成")) {
                            try {
                                context.getSharedPreferences("app_checkin_prefs", Context.MODE_PRIVATE)
                                    .edit().putString("last_checkin_date", today).apply()
                            } catch (e: Exception) { e.printStackTrace() }
                            _uiState.update { it.copy(isCheckingIn = false, isCheckedIn = true) }
                            onResult(true, "✓ 今日已经完成打卡")
                        } else {
                            _uiState.update { it.copy(isCheckingIn = false) }
                            onResult(false, msg)
                        }
                    }
                    is Resource.Loading -> {}
                }
            }
        }
    }
}
