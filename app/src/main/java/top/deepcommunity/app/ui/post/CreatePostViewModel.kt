package top.deepcommunity.app.ui.post

import android.content.Context
import android.net.Uri
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
import top.deepcommunity.app.data.model.CreatePostRequest
import top.deepcommunity.app.data.model.DraftItemDto
import top.deepcommunity.app.data.model.SaveDraftRequest
import top.deepcommunity.app.data.repository.PostRepository
import java.io.InputStream
import javax.inject.Inject

data class CreatePostUiState(
    val title: String = "",
    val content: String = "",
    val imageUrls: List<String> = emptyList(),
    val currentDraftId: String? = null,
    val drafts: List<DraftItemDto> = emptyList(),
    val showDraftsSheet: Boolean = false,
    val isUploadingImage: Boolean = false,
    val isSavingDraft: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    // 随帖投票配置
    val attachPoll: Boolean = false,
    val pollTitle: String = "",
    val pollOptions: List<String> = listOf("", ""),
    val pollIsMultiple: Boolean = false,
    val pollMaxChoices: Int = 2
)

sealed interface CreatePostEvent {
    data object PostSuccess : CreatePostEvent
    data class ShowToast(val message: String) : CreatePostEvent
}

@HiltViewModel
class CreatePostViewModel @Inject constructor(
    private val postRepository: PostRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreatePostUiState())
    val uiState: StateFlow<CreatePostUiState> = _uiState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<CreatePostEvent>()
    val eventFlow: SharedFlow<CreatePostEvent> = _eventFlow.asSharedFlow()

    fun onTitleChange(text: String) {
        _uiState.update { it.copy(title = text) }
    }

    fun onContentChange(text: String) {
        _uiState.update { it.copy(content = text) }
    }

    // 随帖投票逻辑
    fun toggleAttachPoll(enable: Boolean) {
        _uiState.update { it.copy(attachPoll = enable) }
    }

    fun onPollTitleChange(title: String) {
        _uiState.update { it.copy(pollTitle = title) }
    }

    fun onPollOptionChange(index: Int, text: String) {
        val currentList = _uiState.value.pollOptions.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = text
            _uiState.update { it.copy(pollOptions = currentList) }
        }
    }

    fun addPollOption() {
        val currentList = _uiState.value.pollOptions.toMutableList()
        if (currentList.size < 8) {
            currentList.add("")
            _uiState.update { it.copy(pollOptions = currentList) }
        }
    }

    fun removePollOption(index: Int) {
        val currentList = _uiState.value.pollOptions.toMutableList()
        if (currentList.size > 2 && index in currentList.indices) {
            currentList.removeAt(index)
            _uiState.update { it.copy(pollOptions = currentList) }
        }
    }

    fun togglePollIsMultiple(isMultiple: Boolean) {
        _uiState.update { it.copy(pollIsMultiple = isMultiple) }
    }

    fun toggleDraftsSheet(show: Boolean) {
        _uiState.update { it.copy(showDraftsSheet = show) }
        if (show) loadDrafts()
    }

    fun loadDrafts() {
        viewModelScope.launch {
            postRepository.getDrafts().collect { result ->
                if (result is Resource.Success) {
                    _uiState.update { it.copy(drafts = result.data) }
                }
            }
        }
    }

    fun saveAsDraft() {
        val state = _uiState.value
        if (state.title.isBlank() && state.content.isBlank()) {
            viewModelScope.launch { _eventFlow.emit(CreatePostEvent.ShowToast("内容为空，无需存为草稿")) }
            return
        }

        viewModelScope.launch {
            val req = SaveDraftRequest(
                draftId = state.currentDraftId,
                title = state.title,
                contentMd = state.content,
                imageUrls = state.imageUrls
            )
            postRepository.saveDraft(req).collect { result ->
                when (result) {
                    is Resource.Loading -> _uiState.update { it.copy(isSavingDraft = true) }
                    is Resource.Success -> {
                        _uiState.update { it.copy(currentDraftId = result.data.id, isSavingDraft = false) }
                        _eventFlow.emit(CreatePostEvent.ShowToast("草稿保存成功"))
                    }
                    is Resource.Error -> {
                        _uiState.update { it.copy(isSavingDraft = false) }
                        _eventFlow.emit(CreatePostEvent.ShowToast(result.message))
                    }
                }
            }
        }
    }

    fun applyDraft(draft: DraftItemDto) {
        _uiState.update {
            it.copy(
                currentDraftId = draft.id,
                title = draft.title ?: "",
                content = draft.contentMd ?: "",
                showDraftsSheet = false
            )
        }
    }

    fun removeImage(index: Int) {
        val list = _uiState.value.imageUrls.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            _uiState.update { it.copy(imageUrls = list) }
        }
    }

    fun uploadSelectedImage(uri: Uri) {
        viewModelScope.launch {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes() ?: return@launch
                val requestBody = bytes.toRequestBody("image/*".toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("file", "image_${System.currentTimeMillis()}.jpg", requestBody)

                postRepository.uploadImage(part).collect { result ->
                    when (result) {
                        is Resource.Loading -> _uiState.update { it.copy(isUploadingImage = true) }
                        is Resource.Success -> {
                            val currentList = _uiState.value.imageUrls.toMutableList()
                            currentList.add(result.data)
                            _uiState.update { it.copy(imageUrls = currentList, isUploadingImage = false) }
                        }
                        is Resource.Error -> {
                            _uiState.update { it.copy(isUploadingImage = false) }
                            _eventFlow.emit(CreatePostEvent.ShowToast(result.message))
                        }
                    }
                }
            } catch (e: Exception) {
                _eventFlow.emit(CreatePostEvent.ShowToast("读取图片失败"))
            }
        }
    }

    fun submitPost() {
        val state = _uiState.value
        if (state.title.isBlank()) {
            viewModelScope.launch { _eventFlow.emit(CreatePostEvent.ShowToast("请输入标题")) }
            return
        }
        if (state.content.isBlank()) {
            viewModelScope.launch { _eventFlow.emit(CreatePostEvent.ShowToast("请输入动态正文")) }
            return
        }

        // 校验投票项
        val validPollOptions = state.pollOptions.map { it.trim() }.filter { it.isNotBlank() }
        if (state.attachPoll && validPollOptions.size < 2) {
            viewModelScope.launch { _eventFlow.emit(CreatePostEvent.ShowToast("随帖投票请至少填写两个有效选项")) }
            return
        }

        viewModelScope.launch {
            val request = CreatePostRequest(
                title = state.title.trim(),
                contentMd = state.content.trim(),
                imageUrls = state.imageUrls
            )
            postRepository.createPost(request).collect { result ->
                when (result) {
                    is Resource.Loading -> _uiState.update { it.copy(isSubmitting = true) }
                    is Resource.Success -> {
                        val createdPost = result.data
                        if (state.attachPoll) {
                            val pollTitle = state.pollTitle.ifBlank { state.title.trim() }
                            postRepository.createPoll(
                                postId = createdPost.id,
                                title = pollTitle,
                                options = validPollOptions,
                                isMultiple = state.pollIsMultiple,
                                maxChoices = if (state.pollIsMultiple) state.pollMaxChoices else 1
                            ).collect { pollResult ->
                                _uiState.update { it.copy(isSubmitting = false) }
                                when (pollResult) {
                                    is Resource.Success -> {
                                        _eventFlow.emit(CreatePostEvent.PostSuccess)
                                    }
                                    is Resource.Error -> {
                                        _eventFlow.emit(CreatePostEvent.ShowToast("帖子已发布，但附加投票创建失败: ${pollResult.message}"))
                                        _eventFlow.emit(CreatePostEvent.PostSuccess)
                                    }
                                    else -> {}
                                }
                            }
                        } else {
                            _uiState.update { it.copy(isSubmitting = false) }
                            _eventFlow.emit(CreatePostEvent.PostSuccess)
                        }
                    }
                    is Resource.Error -> {
                        _uiState.update { it.copy(isSubmitting = false) }
                        _eventFlow.emit(CreatePostEvent.ShowToast(result.message))
                    }
                }
            }
        }
    }
}
