package top.deepcommunity.app.ui.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import top.deepcommunity.app.core.common.Resource
import top.deepcommunity.app.data.model.ChatMessageDto
import top.deepcommunity.app.data.model.GroupCheckinStatusDto
import top.deepcommunity.app.data.model.GroupMemberDto
import top.deepcommunity.app.data.model.StickerItemDto
import top.deepcommunity.app.data.repository.AuthRepository
import top.deepcommunity.app.data.repository.ChatRepository
import top.deepcommunity.app.data.repository.PostRepository
import top.deepcommunity.app.data.repository.UserRepository
import java.io.InputStream
import javax.inject.Inject

data class ChatUiState(
    val messages: List<ChatMessageDto> = emptyList(),
    val currentUserId: String? = null,
    val isGroupChat: Boolean = false,
    val chatTitle: String = "会话详情",
    val inputText: String = "",
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val isUploadingImage: Boolean = false,
    // 引用回复
    val replyingToMessage: ChatMessageDto? = null,
    // 表情面板（0: Emoji, 1: 自选表情包）
    val showStickerPanel: Boolean = false,
    val stickerTab: Int = 0,
    val stickers: List<StickerItemDto> = emptyList(),
    val isLoadingStickers: Boolean = false,
    // 群设置抽屉
    val showGroupInfoSheet: Boolean = false,
    val groupMembers: List<GroupMemberDto> = emptyList(),
    val groupAnnouncement: String = "",
    val myRole: String? = null,
    val isLoadingGroupMembers: Boolean = false,
    val isRollingDice: Boolean = false,
    val groupCheckinStatus: GroupCheckinStatusDto? = null
)

sealed interface ChatEvent {
    data class ShowToast(val message: String) : ChatEvent
    data object ScrollToBottom : ChatEvent
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val postRepository: PostRepository,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val convId: String = checkNotNull(savedStateHandle["convId"])

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<ChatEvent>()
    val eventFlow: SharedFlow<ChatEvent> = _eventFlow.asSharedFlow()

    init {
        val currentUser = authRepository.getSavedUser()
        _uiState.update { it.copy(currentUserId = currentUser?.id) }

        chatRepository.connectWebSocket()
        loadHistory()
        listenRealtime()
    }

    fun onInputTextChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun appendText(text: String) {
        _uiState.update { it.copy(inputText = it.inputText + text) }
    }

    // 引用回复
    fun setReplyMessage(message: ChatMessageDto?) {
        _uiState.update { it.copy(replyingToMessage = message) }
    }

    // 表情面板控制
    fun toggleStickerPanel(show: Boolean? = null) {
        val current = _uiState.value.showStickerPanel
        val target = show ?: !current
        _uiState.update { it.copy(showStickerPanel = target) }
        if (target && _uiState.value.stickers.isEmpty()) {
            loadStickers()
        }
    }

    fun setStickerTab(tab: Int) {
        _uiState.update { it.copy(stickerTab = tab) }
        if (tab == 1 && _uiState.value.stickers.isEmpty()) {
            loadStickers()
        }
    }

    fun loadStickers() {
        viewModelScope.launch {
            chatRepository.getMyStickers().collect { result ->
                when (result) {
                    is Resource.Loading -> _uiState.update { it.copy(isLoadingStickers = true) }
                    is Resource.Success -> _uiState.update { it.copy(stickers = result.data.items, isLoadingStickers = false) }
                    is Resource.Error -> {
                        _uiState.update { it.copy(isLoadingStickers = false) }
                    }
                }
            }
        }
    }

    // 群设置抽屉
    fun toggleGroupInfoSheet(show: Boolean) {
        _uiState.update { it.copy(showGroupInfoSheet = show) }
        if (show && _uiState.value.isGroupChat) {
            loadGroupMembers()
        }
    }

    fun loadGroupMembers() {
        viewModelScope.launch {
            chatRepository.getGroupMembers(convId).collect { result ->
                when (result) {
                    is Resource.Loading -> _uiState.update { it.copy(isLoadingGroupMembers = true) }
                    is Resource.Success -> {
                        val members = result.data
                        val myRole = members.find { it.userId == _uiState.value.currentUserId }?.role
                        _uiState.update {
                            it.copy(
                                groupMembers = members,
                                myRole = myRole,
                                isLoadingGroupMembers = false
                            )
                        }
                    }
                    is Resource.Error -> _uiState.update { it.copy(isLoadingGroupMembers = false) }
                }
            }
        }
    }

    fun kickMember(userId: String) {
        viewModelScope.launch {
            chatRepository.kickGroupMember(convId, userId).collect { result ->
                when (result) {
                    is Resource.Success -> {
                        _eventFlow.emit(ChatEvent.ShowToast(result.data))
                        loadGroupMembers()
                    }
                    is Resource.Error -> _eventFlow.emit(ChatEvent.ShowToast(result.message))
                    else -> {}
                }
            }
        }
    }

    fun updateMemberRole(userId: String, newRole: String) {
        viewModelScope.launch {
            chatRepository.updateMemberRole(convId, userId, newRole).collect { result ->
                when (result) {
                    is Resource.Success -> {
                        _eventFlow.emit(ChatEvent.ShowToast(result.data))
                        loadGroupMembers()
                    }
                    is Resource.Error -> _eventFlow.emit(ChatEvent.ShowToast(result.message))
                    else -> {}
                }
            }
        }
    }

    fun leaveCurrentGroup(onSuccess: () -> Unit) {
        viewModelScope.launch {
            chatRepository.leaveGroup(convId).collect { result ->
                when (result) {
                    is Resource.Success -> onSuccess()
                    is Resource.Error -> _eventFlow.emit(ChatEvent.ShowToast(result.message))
                    else -> {}
                }
            }
        }
    }

    fun blockUser(username: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            userRepository.blockUser(username).collect { result ->
                when (result) {
                    is Resource.Success -> {
                        _eventFlow.emit(ChatEvent.ShowToast("已将 @$username 加入黑名单"))
                        onSuccess()
                    }
                    is Resource.Error -> _eventFlow.emit(ChatEvent.ShowToast(result.message))
                    else -> {}
                }
            }
        }
    }

    // 消息撤回
    fun recallMessage(message: ChatMessageDto) {
        viewModelScope.launch {
            val flow = if (_uiState.value.isGroupChat) {
                chatRepository.recallGroupMessage(convId, message.id)
            } else {
                chatRepository.recallMessage(message.id)
            }
            flow.collect { result ->
                when (result) {
                    is Resource.Loading -> {}
                    is Resource.Success -> {
                        // 本地乐观更新为已撤回
                        val updatedList = _uiState.value.messages.map {
                            if (it.id == message.id) it.copy(isRecalled = true) else it
                        }
                        _uiState.update { it.copy(messages = updatedList) }
                        _eventFlow.emit(ChatEvent.ShowToast("已撤回一条消息"))
                    }
                    is Resource.Error -> {
                        _eventFlow.emit(ChatEvent.ShowToast(result.message))
                    }
                }
            }
        }
    }

    // 发送图片消息（支持文字+图片图文混排）
    fun uploadAndSendImage(uri: Uri) {
        viewModelScope.launch {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes() ?: return@launch
                val requestBody = bytes.toRequestBody("image/*".toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("file", "chat_${System.currentTimeMillis()}.jpg", requestBody)

                postRepository.uploadImage(part).collect { result ->
                    when (result) {
                        is Resource.Loading -> _uiState.update { it.copy(isUploadingImage = true) }
                        is Resource.Success -> {
                            _uiState.update { it.copy(isUploadingImage = false) }
                            val currentInput = _uiState.value.inputText.trim()
                            val imageUrl = result.data
                            _uiState.update { it.copy(inputText = "") }
                            // 如果用户输入了文字，则将文字与图片路径组装为图文消息
                            val finalContent = if (currentInput.isNotBlank()) "$currentInput\n$imageUrl" else imageUrl
                            doSend(finalContent)
                        }
                        is Resource.Error -> {
                            _uiState.update { it.copy(isUploadingImage = false) }
                            _eventFlow.emit(ChatEvent.ShowToast("图片上传失败: ${result.message}"))
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isUploadingImage = false) }
                _eventFlow.emit(ChatEvent.ShowToast("读取图片失败"))
            }
        }
    }

    // 摇骰子
    fun rollDice() {
        if (!_uiState.value.isGroupChat || _uiState.value.isRollingDice) return
        viewModelScope.launch {
            chatRepository.rollDice(convId).collect { result ->
                when (result) {
                    is Resource.Loading -> _uiState.update { it.copy(isRollingDice = true) }
                    is Resource.Success -> {
                        _uiState.update { state ->
                            state.copy(
                                messages = state.messages + result.data,
                                isRollingDice = false
                            )
                        }
                        _eventFlow.emit(ChatEvent.ScrollToBottom)
                    }
                    is Resource.Error -> {
                        _uiState.update { it.copy(isRollingDice = false) }
                        _eventFlow.emit(ChatEvent.ShowToast(result.message))
                    }
                }
            }
        }
    }

    // 收藏到表情
    fun collectSticker(url: String) {
        viewModelScope.launch {
            chatRepository.collectSticker(url).collect { result ->
                when (result) {
                    is Resource.Success -> {
                        _eventFlow.emit(ChatEvent.ShowToast("✓ ${result.data}"))
                        loadStickers()
                    }
                    is Resource.Error -> _eventFlow.emit(ChatEvent.ShowToast(result.message))
                    else -> {}
                }
            }
        }
    }

    // 禁言与解禁
    fun muteMember(targetUserId: String, durationMinutes: Int = 60) {
        viewModelScope.launch {
            chatRepository.muteMember(convId, targetUserId, durationMinutes).collect { result ->
                when (result) {
                    is Resource.Success -> {
                        _eventFlow.emit(ChatEvent.ShowToast(result.data))
                        loadGroupMembers()
                    }
                    is Resource.Error -> _eventFlow.emit(ChatEvent.ShowToast(result.message))
                    else -> {}
                }
            }
        }
    }

    fun unmuteMember(targetUserId: String) {
        viewModelScope.launch {
            chatRepository.unmuteMember(convId, targetUserId).collect { result ->
                when (result) {
                    is Resource.Success -> {
                        _eventFlow.emit(ChatEvent.ShowToast(result.data))
                        loadGroupMembers()
                    }
                    is Resource.Error -> _eventFlow.emit(ChatEvent.ShowToast(result.message))
                    else -> {}
                }
            }
        }
    }

    fun loadGroupCheckinStatus() {
        if (!_uiState.value.isGroupChat) return
        viewModelScope.launch {
            chatRepository.getGroupCheckinStatus(convId).collect { result ->
                if (result is Resource.Success) {
                    _uiState.update { it.copy(groupCheckinStatus = result.data) }
                }
            }
        }
    }

    private fun loadHistory() {
        viewModelScope.launch {
            chatRepository.getMessages(convId).collect { result ->
                when (result) {
                    is Resource.Loading -> _uiState.update { it.copy(isLoading = true) }
                    is Resource.Success -> {
                        _uiState.update {
                            it.copy(
                                messages = result.data,
                                isGroupChat = false,
                                chatTitle = "私信会话",
                                isLoading = false
                            )
                        }
                        _eventFlow.emit(ChatEvent.ScrollToBottom)
                    }
                    is Resource.Error -> {
                        loadGroupHistory()
                    }
                }
            }
        }
    }

    private fun loadGroupHistory() {
        viewModelScope.launch {
            chatRepository.getGroupMessages(convId).collect { result ->
                when (result) {
                    is Resource.Loading -> {}
                    is Resource.Success -> {
                        _uiState.update {
                            it.copy(
                                messages = result.data,
                                isGroupChat = true,
                                chatTitle = "社区群聊",
                                isLoading = false
                            )
                        }
                        loadGroupCheckinStatus()
                        _eventFlow.emit(ChatEvent.ScrollToBottom)
                    }
                    is Resource.Error -> {
                        _uiState.update { it.copy(isLoading = false) }
                    }
                }
            }
        }
    }

    private fun listenRealtime() {
        viewModelScope.launch {
            chatRepository.incomingRealtime.collect {
                loadHistory()
            }
        }
    }

    fun sendSticker(sticker: StickerItemDto) {
        _uiState.update { it.copy(showStickerPanel = false) }
        doSend(sticker.finalUrl)
    }

    fun sendMessage() {
        val content = _uiState.value.inputText.trim()
        if (content.isBlank()) return
        _uiState.update { it.copy(inputText = "") }
        doSend(content)
    }

    private fun doSend(content: String) {
        val replyId = _uiState.value.replyingToMessage?.id
        _uiState.update { it.copy(replyingToMessage = null) }

        viewModelScope.launch {
            val flow = if (_uiState.value.isGroupChat) {
                chatRepository.sendGroupMessage(convId, content, replyToMessageId = replyId)
            } else {
                chatRepository.sendMessage(convId, content, replyToMessageId = replyId)
            }

            flow.collect { result ->
                when (result) {
                    is Resource.Loading -> _uiState.update { it.copy(isSending = true) }
                    is Resource.Success -> {
                        _uiState.update { state ->
                            state.copy(
                                messages = state.messages + result.data,
                                isSending = false
                            )
                        }
                        _eventFlow.emit(ChatEvent.ScrollToBottom)
                    }
                    is Resource.Error -> {
                        _uiState.update { it.copy(isSending = false) }
                        _eventFlow.emit(ChatEvent.ShowToast(result.message))
                    }
                }
            }
        }
    }
}
