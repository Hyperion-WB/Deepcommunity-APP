package top.deepcommunity.app.ui.notification

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.deepcommunity.app.core.common.Resource
import top.deepcommunity.app.core.notification.DeepNotificationHelper
import top.deepcommunity.app.data.model.NotificationDto
import top.deepcommunity.app.data.repository.PostRepository
import javax.inject.Inject

import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

data class NotificationUiState(
    val notifications: List<NotificationDto> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null
) {
    val unreadCount: Int
        get() = notifications.count { !it.isRead }
}

@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val postRepository: PostRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationUiState())
    val uiState: StateFlow<NotificationUiState> = _uiState.asStateFlow()

    private var lastNotifiedId: String? = null

    init {
        loadNotifications()
        startAutoSync()
    }

    private fun startAutoSync() {
        viewModelScope.launch {
            while (isActive) {
                delay(12000)
                loadNotifications(isSilent = true)
            }
        }
    }

    fun loadNotifications(isRefresh: Boolean = false, isSilent: Boolean = false) {
        viewModelScope.launch {
            postRepository.getNotifications().collect { result ->
                when (result) {
                    is Resource.Loading -> {
                        if (!isSilent) {
                            _uiState.update {
                                if (isRefresh) it.copy(isRefreshing = true) else it.copy(isLoading = true)
                            }
                        }
                    }
                    is Resource.Success -> {
                        val unreadList = result.data.filter { !it.isRead }
                        val latest = unreadList.firstOrNull()

                        // 仅当确实有新的未读通知到达时，触发系统状态栏原生通知
                        if (latest != null && latest.id != lastNotifiedId) {
                            lastNotifiedId = latest.id
                            DeepNotificationHelper.showNotification(
                                context = context,
                                title = "深度社区新提醒",
                                content = latest.brief,
                                targetId = latest.targetId
                            )
                        }

                        _uiState.update {
                            it.copy(
                                notifications = result.data,
                                isLoading = false,
                                isRefreshing = false,
                                errorMessage = null
                            )
                        }
                    }
                    is Resource.Error -> {
                        if (!isSilent) {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    isRefreshing = false,
                                    errorMessage = result.message
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            postRepository.markAllNotificationsAsRead().collect { result ->
                if (result is Resource.Success) {
                    val updated = _uiState.value.notifications.map { it.copy(isRead = true) }
                    _uiState.update { it.copy(notifications = updated) }
                }
            }
        }
    }

    fun markSingleAsRead(id: String) {
        // 乐观更新
        val updated = _uiState.value.notifications.map {
            if (it.id == id) it.copy(isRead = true) else it
        }
        _uiState.update { it.copy(notifications = updated) }

        viewModelScope.launch {
            postRepository.markSingleNotificationRead(id).collect {}
        }
    }

    fun deleteNotification(id: String) {
        // 立即从本地列表剔除（极其爽快流畅）
        val updated = _uiState.value.notifications.filterNot { it.id == id }
        _uiState.update { it.copy(notifications = updated) }

        viewModelScope.launch {
            postRepository.deleteSingleNotification(id).collect {}
        }
    }

    fun clearAllNotifications() {
        viewModelScope.launch {
            postRepository.clearAllNotifications().collect { result ->
                if (result is Resource.Success) {
                    _uiState.update { it.copy(notifications = emptyList()) }
                }
            }
        }
    }
}
