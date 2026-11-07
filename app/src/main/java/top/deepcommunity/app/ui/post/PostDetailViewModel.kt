package top.deepcommunity.app.ui.post

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
import top.deepcommunity.app.data.model.CommentDto
import top.deepcommunity.app.data.model.PollDetailDto
import top.deepcommunity.app.data.model.PostItemDto
import top.deepcommunity.app.data.model.StickerItemDto
import top.deepcommunity.app.data.repository.ChatRepository
import top.deepcommunity.app.data.repository.PostRepository
import java.io.InputStream
import javax.inject.Inject

data class PostDetailUiState(
    val post: PostItemDto? = null,
    val comments: List<CommentDto> = emptyList(),
    val poll: PollDetailDto? = null,
    val selectedOptionIds: Set<Int> = emptySet(),
    val isVoting: Boolean = false,
    val isLoading: Boolean = false,
    val isSendingComment: Boolean = false,
    val isUploadingCommentImage: Boolean = false,
    val commentInput: String = "",
    val replyingToCommentId: String? = null,
    val replyingToAuthorName: String? = null,
    val showStickerPanel: Boolean = false,
    val stickerTab: Int = 0,
    val stickers: List<StickerItemDto> = emptyList(),
    val attachedImageUrl: String? = null,
    val attachedImageKind: String? = null, // "photo" 或 "sticker"
    val errorMessage: String? = null
)

sealed interface PostDetailEvent {
    data class ShowToast(val message: String) : PostDetailEvent
}

@HiltViewModel
class PostDetailViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val chatRepository: ChatRepository,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val postId: String = checkNotNull(savedStateHandle["postId"])

    private val _uiState = MutableStateFlow(PostDetailUiState())
    val uiState: StateFlow<PostDetailUiState> = _uiState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<PostDetailEvent>()
    val eventFlow: SharedFlow<PostDetailEvent> = _eventFlow.asSharedFlow()

    init {
        loadPostDetail()
        loadComments()
        loadPoll()
    }

    fun loadPostDetail() {
        viewModelScope.launch {
            postRepository.getPostDetail(postId).collect { result ->
                when (result) {
                    is Resource.Loading -> _uiState.update { it.copy(isLoading = true) }
                    is Resource.Success -> _uiState.update { it.copy(post = result.data, isLoading = false) }
                    is Resource.Error -> _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
            }
        }
    }

    fun loadPoll() {
        viewModelScope.launch {
            postRepository.getPostPoll(postId).collect { result ->
                if (result is Resource.Success) {
                    _uiState.update { it.copy(poll = result.data) }
                }
            }
        }
    }

    fun toggleOptionSelect(optionId: Int) {
        val currentPoll = _uiState.value.poll ?: return
        if (currentPoll.hasVoted || currentPoll.isExpired) return

        val currentSet = _uiState.value.selectedOptionIds
        val newSet = if (currentPoll.isMultiple) {
            if (currentSet.contains(optionId)) currentSet - optionId else currentSet + optionId
        } else {
            setOf(optionId)
        }
        _uiState.update { it.copy(selectedOptionIds = newSet) }
    }

    fun submitVote() {
        val poll = _uiState.value.poll ?: return
        val selected = _uiState.value.selectedOptionIds.toList()
        if (selected.isEmpty()) return

        viewModelScope.launch {
            postRepository.votePoll(postId, poll.id, selected).collect { result ->
                when (result) {
                    is Resource.Loading -> _uiState.update { it.copy(isVoting = true) }
                    is Resource.Success -> {
                        _uiState.update { it.copy(poll = result.data, isVoting = false, selectedOptionIds = emptySet()) }
                        _eventFlow.emit(PostDetailEvent.ShowToast("投票成功"))
                    }
                    is Resource.Error -> {
                        _uiState.update { it.copy(isVoting = false) }
                        _eventFlow.emit(PostDetailEvent.ShowToast(result.message))
                    }
                }
            }
        }
    }

    fun loadComments() {
        viewModelScope.launch {
            postRepository.getComments(postId).collect { result ->
                if (result is Resource.Success) {
                    _uiState.update { it.copy(comments = result.data) }
                }
            }
        }
    }

    fun onCommentInputChange(text: String) {
        _uiState.update { it.copy(commentInput = text) }
    }

    fun appendText(text: String) {
        _uiState.update { it.copy(commentInput = it.commentInput + text) }
    }

    fun toggleLike() {
        val currentPost = _uiState.value.post ?: return
        val newIsLiked = !currentPost.isLiked
        val newLikeCount = if (newIsLiked) currentPost.likeCount + 1 else (currentPost.likeCount - 1).coerceAtLeast(0)

        _uiState.update { it.copy(post = currentPost.copy(isLiked = newIsLiked, likeCount = newLikeCount)) }

        viewModelScope.launch {
            postRepository.toggleLike(postId).collect { result ->
                if (result is Resource.Error) {
                    _uiState.update { it.copy(post = currentPost) }
                    _eventFlow.emit(PostDetailEvent.ShowToast("点赞失败"))
                }
            }
        }
    }

    fun toggleCollect() {
        val currentPost = _uiState.value.post ?: return
        val newIsCollected = !currentPost.isCollected

        _uiState.update { it.copy(post = currentPost.copy(isCollected = newIsCollected)) }

        viewModelScope.launch {
            postRepository.toggleCollect(postId).collect { result ->
                if (result is Resource.Error) {
                    _uiState.update { it.copy(post = currentPost) }
                    _eventFlow.emit(PostDetailEvent.ShowToast("收藏失败"))
                }
            }
        }
    }

    fun setReplyTo(commentId: String, authorName: String) {
        _uiState.update {
            it.copy(
                replyingToCommentId = commentId,
                replyingToAuthorName = authorName
            )
        }
    }

    fun cancelReply() {
        _uiState.update {
            it.copy(
                replyingToCommentId = null,
                replyingToAuthorName = null
            )
        }
    }

    fun toggleStickerPanel(show: Boolean? = null) {
        val next = show ?: !_uiState.value.showStickerPanel
        _uiState.update { it.copy(showStickerPanel = next) }
        if (next && _uiState.value.stickers.isEmpty()) {
            loadStickers()
        }
    }

    fun setStickerTab(tab: Int) {
        _uiState.update { it.copy(stickerTab = tab) }
    }

    fun loadStickers() {
        viewModelScope.launch {
            chatRepository.getMyStickers().collect { result ->
                if (result is Resource.Success) {
                    _uiState.update { it.copy(stickers = result.data.items) }
                }
            }
        }
    }

    fun appendEmoji(emoji: String) {
        _uiState.update { it.copy(commentInput = it.commentInput + emoji) }
    }

    fun attachSticker(sticker: StickerItemDto) {
        _uiState.update {
            it.copy(
                attachedImageUrl = sticker.url,
                attachedImageKind = "sticker",
                showStickerPanel = false
            )
        }
    }

    fun uploadCommentImage(uri: Uri) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isUploadingCommentImage = true) }
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes() ?: return@launch
                inputStream.close()

                val requestBody = bytes.toRequestBody("image/*".toMediaTypeOrNull())
                val filePart = MultipartBody.Part.createFormData(
                    "file",
                    "comment_img_${System.currentTimeMillis()}.jpg",
                    requestBody
                )

                postRepository.uploadImage(filePart).collect { result ->
                    when (result) {
                        is Resource.Loading -> _uiState.update { it.copy(isUploadingCommentImage = true) }
                        is Resource.Success -> {
                            _uiState.update {
                                it.copy(
                                    attachedImageUrl = result.data,
                                    attachedImageKind = "photo",
                                    isUploadingCommentImage = false,
                                    showStickerPanel = false
                                )
                            }
                        }
                        is Resource.Error -> {
                            _uiState.update { it.copy(isUploadingCommentImage = false) }
                            _eventFlow.emit(PostDetailEvent.ShowToast(result.message))
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isUploadingCommentImage = false) }
                _eventFlow.emit(PostDetailEvent.ShowToast(e.localizedMessage ?: "图片处理失败"))
            }
        }
    }

    fun removeAttachedImage() {
        _uiState.update {
            it.copy(
                attachedImageUrl = null,
                attachedImageKind = null
            )
        }
    }

    fun toggleCommentLike(commentId: String) {
        // 本地乐观更新点赞状态与点赞数
        val currentComments = _uiState.value.comments
        val updated = currentComments.map { comment ->
            if (comment.id == commentId) {
                val newLiked = !comment.isLiked
                val newCount = if (newLiked) comment.likeCount + 1 else maxOf(0, comment.likeCount - 1)
                comment.copy(isLiked = newLiked, likeCount = newCount)
            } else {
                val updatedReplies = comment.replies.map { reply ->
                    if (reply.id == commentId) {
                        val newLiked = !reply.isLiked
                        val newCount = if (newLiked) reply.likeCount + 1 else maxOf(0, reply.likeCount - 1)
                        reply.copy(isLiked = newLiked, likeCount = newCount)
                    } else reply
                }
                comment.copy(replies = updatedReplies)
            }
        }
        _uiState.update { it.copy(comments = updated) }

        viewModelScope.launch {
            postRepository.toggleCommentLike(commentId).collect { result ->
                if (result is Resource.Error) {
                    // 失败时回滚
                    loadComments()
                }
            }
        }
    }

    fun deleteComment(commentId: String) {
        viewModelScope.launch {
            postRepository.deleteComment(commentId).collect { result ->
                when (result) {
                    is Resource.Success -> {
                        _eventFlow.emit(PostDetailEvent.ShowToast("评论已删除"))
                        loadComments()
                    }
                    is Resource.Error -> _eventFlow.emit(PostDetailEvent.ShowToast(result.message))
                    else -> {}
                }
            }
        }
    }

    fun sendComment() {
        val content = _uiState.value.commentInput.trim()
        val attachedImg = _uiState.value.attachedImageUrl
        val imgKind = _uiState.value.attachedImageKind
        if (content.isBlank() && attachedImg == null) return

        val parentId = _uiState.value.replyingToCommentId
        val effectiveContent = content.ifBlank { if (imgKind == "photo") "[图片]" else "[表情]" }

        viewModelScope.launch {
            postRepository.createComment(
                postId = postId,
                content = effectiveContent,
                parentId = parentId,
                imageUrl = attachedImg,
                imageKind = imgKind ?: "sticker"
            ).collect { result ->
                when (result) {
                    is Resource.Loading -> _uiState.update { it.copy(isSendingComment = true) }
                    is Resource.Success -> {
                        _uiState.update {
                            it.copy(
                                isSendingComment = false,
                                commentInput = "",
                                replyingToCommentId = null,
                                replyingToAuthorName = null,
                                attachedImageUrl = null,
                                attachedImageKind = null,
                                showStickerPanel = false
                            )
                        }
                        loadComments()
                    }
                    is Resource.Error -> {
                        _uiState.update { it.copy(isSendingComment = false) }
                        _eventFlow.emit(PostDetailEvent.ShowToast(result.message))
                    }
                }
            }
        }
    }
}
