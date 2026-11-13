package top.deepcommunity.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.deepcommunity.app.core.common.Resource
import top.deepcommunity.app.data.model.ConversationDto
import top.deepcommunity.app.data.model.GroupRoomDto
import top.deepcommunity.app.data.repository.ChatRepository
import javax.inject.Inject
import kotlin.math.max

data class ConversationListUiState(
    val selectedTab: Int = 0, // 0: 私聊, 1: 群聊
    val conversations: List<ConversationDto> = emptyList(),
    val groups: List<GroupRoomDto> = emptyList(),
    val totalUnreadCount: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val privateUnreadCount: Int
        get() = conversations.sumOf { it.unreadCount }

    val groupUnreadCount: Int
        get() = groups.sumOf { it.unreadCount }

    val effectiveTotalUnread: Int
        get() {
            val sum = privateUnreadCount + groupUnreadCount
            return if (totalUnreadCount > 0) max(totalUnreadCount, sum) else sum
        }
}

@HiltViewModel
class ConversationListViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConversationListUiState())
    val uiState: StateFlow<ConversationListUiState> = _uiState.asStateFlow()

    init {
        refreshAll()
        listenRealtime()
    }

    fun selectTab(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab) }
        if (tab == 0) loadConversations()
        else if (tab == 1) loadGroups()
    }

    fun refreshAll() {
        loadConversations()
        loadGroups()
        loadUnreadCount()
    }

    fun loadUnreadCount() {
        viewModelScope.launch {
            chatRepository.getChatUnreadCount().collect { result ->
                if (result is Resource.Success) {
                    _uiState.update { it.copy(totalUnreadCount = result.data) }
                }
            }
        }
    }

    fun loadConversations() {
        viewModelScope.launch {
            chatRepository.getConversations().collect { result ->
                when (result) {
                    is Resource.Loading -> _uiState.update { it.copy(isLoading = true) }
                    is Resource.Success -> {
                        _uiState.update { it.copy(conversations = result.data, isLoading = false) }
                        loadUnreadCount()
                    }
                    is Resource.Error -> _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
            }
        }
    }

    fun loadGroups() {
        viewModelScope.launch {
            chatRepository.getGroups().collect { result ->
                when (result) {
                    is Resource.Loading -> _uiState.update { it.copy(isLoading = true) }
                    is Resource.Success -> {
                        _uiState.update { it.copy(groups = result.data, isLoading = false) }
                        loadUnreadCount()
                    }
                    is Resource.Error -> _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    private fun listenRealtime() {
        viewModelScope.launch {
            chatRepository.incomingRealtime.collect {
                refreshAll()
            }
        }
    }
}
