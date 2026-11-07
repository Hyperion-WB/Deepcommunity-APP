package top.deepcommunity.app.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import top.deepcommunity.app.core.common.Resource
import top.deepcommunity.app.data.api.PostApi
import top.deepcommunity.app.data.model.CategoryDto
import top.deepcommunity.app.data.model.CommentDto
import top.deepcommunity.app.data.model.CreateCommentRequest
import top.deepcommunity.app.data.model.CreatePostRequest
import top.deepcommunity.app.data.model.DraftItemDto
import top.deepcommunity.app.data.model.ErrorResponse
import top.deepcommunity.app.data.model.NotificationDto
import top.deepcommunity.app.data.model.PollCreateRequest
import top.deepcommunity.app.data.model.PollDetailDto
import top.deepcommunity.app.data.model.PollOptionCreateDto
import top.deepcommunity.app.data.model.PollVoteRequest
import top.deepcommunity.app.data.model.PostItemDto
import top.deepcommunity.app.data.model.SaveDraftRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PostRepository @Inject constructor(
    private val postApi: PostApi,
    private val json: Json
) {
    fun getCategories(): Flow<Resource<List<CategoryDto>>> = flow {
        emit(Resource.Loading)
        try {
            val response = postApi.getCategories()
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error("加载分类失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    fun getPosts(
        page: Int = 1,
        size: Int = 20,
        sortBy: String = "latest",
        categoryId: Int? = null,
        query: String? = null
    ): Flow<Resource<List<PostItemDto>>> = flow {
        emit(Resource.Loading)
        try {
            val response = postApi.getPosts(
                page = page,
                size = size,
                sortBy = sortBy,
                categoryId = categoryId,
                query = query
            )
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error(parseError(response.errorBody()?.string())))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "加载动态失败"))
        }
    }.flowOn(Dispatchers.IO)

    fun getPostDetail(id: String): Flow<Resource<PostItemDto>> = flow {
        emit(Resource.Loading)
        try {
            val response = postApi.getPostDetail(id)
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error(parseError(response.errorBody()?.string())))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "获取文章详情失败"))
        }
    }.flowOn(Dispatchers.IO)

    fun toggleLike(postId: String): Flow<Resource<Unit>> = flow {
        try {
            val response = postApi.toggleLike(postId)
            if (response.isSuccessful) {
                emit(Resource.Success(Unit))
            } else {
                emit(Resource.Error(parseError(response.errorBody()?.string())))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "操作失败"))
        }
    }.flowOn(Dispatchers.IO)

    fun toggleCollect(postId: String): Flow<Resource<Unit>> = flow {
        try {
            val response = postApi.toggleCollect(postId)
            if (response.isSuccessful) {
                emit(Resource.Success(Unit))
            } else {
                emit(Resource.Error(parseError(response.errorBody()?.string())))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "操作失败"))
        }
    }.flowOn(Dispatchers.IO)

    fun createPost(request: CreatePostRequest): Flow<Resource<PostItemDto>> = flow {
        emit(Resource.Loading)
        try {
            val response = postApi.createPost(request)
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error(parseError(response.errorBody()?.string())))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "发布失败"))
        }
    }.flowOn(Dispatchers.IO)

    fun uploadImage(filePart: okhttp3.MultipartBody.Part): Flow<Resource<String>> = flow {
        emit(Resource.Loading)
        try {
            val response = postApi.uploadImage(filePart)
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!.finalUrl))
            } else {
                emit(Resource.Error(parseError(response.errorBody()?.string())))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "上传图片失败"))
        }
    }.flowOn(Dispatchers.IO)

    // 随帖投票
    fun getPostPoll(postId: String): Flow<Resource<PollDetailDto>> = flow {
        emit(Resource.Loading)
        try {
            val response = postApi.getPostPoll(postId)
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error("暂无投票或加载失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "加载投票失败"))
        }
    }.flowOn(Dispatchers.IO)

    fun createPoll(
        postId: String,
        title: String,
        options: List<String>,
        isMultiple: Boolean = false,
        maxChoices: Int = 1
    ): Flow<Resource<PollDetailDto>> = flow {
        emit(Resource.Loading)
        try {
            val req = PollCreateRequest(
                postId = postId,
                title = title,
                options = options.map { PollOptionCreateDto(optionText = it.trim()) },
                isMultiple = isMultiple,
                maxChoices = if (isMultiple) maxChoices else 1
            )
            val response = postApi.createPoll(req)
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error(parseError(response.errorBody()?.string())))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "创建投票失败"))
        }
    }.flowOn(Dispatchers.IO)

    fun votePoll(postId: String, pollId: String, optionIds: List<Int>): Flow<Resource<PollDetailDto>> = flow {
        emit(Resource.Loading)
        try {
            val response = postApi.votePoll(pollId, PollVoteRequest(optionIds))
            if (response.isSuccessful) {
                // 投票成功后立即拉取最新的投票统计数据
                val detailRes = postApi.getPostPoll(postId)
                if (detailRes.isSuccessful && detailRes.body() != null) {
                    emit(Resource.Success(detailRes.body()!!))
                } else {
                    emit(Resource.Error("投票成功，但刷新统计失败"))
                }
            } else {
                emit(Resource.Error(parseError(response.errorBody()?.string())))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "投票失败"))
        }
    }.flowOn(Dispatchers.IO)

    // 草稿
    fun getDrafts(): Flow<Resource<List<DraftItemDto>>> = flow {
        emit(Resource.Loading)
        try {
            val response = postApi.getDrafts()
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error("加载草稿失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    fun saveDraft(request: SaveDraftRequest): Flow<Resource<DraftItemDto>> = flow {
        emit(Resource.Loading)
        try {
            val response = postApi.saveDraft(request)
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error(parseError(response.errorBody()?.string())))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "保存草稿失败"))
        }
    }.flowOn(Dispatchers.IO)

    fun toggleCommentLike(commentId: String): Flow<Resource<Unit>> = flow {
        try {
            val response = postApi.toggleCommentLike(commentId)
            if (response.isSuccessful) {
                emit(Resource.Success(Unit))
            } else {
                emit(Resource.Error(parseError(response.errorBody()?.string())))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "操作失败"))
        }
    }.flowOn(Dispatchers.IO)

    fun deleteComment(commentId: String): Flow<Resource<Unit>> = flow {
        try {
            val response = postApi.deleteComment(commentId)
            if (response.isSuccessful) {
                emit(Resource.Success(Unit))
            } else {
                emit(Resource.Error(parseError(response.errorBody()?.string())))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "删除失败"))
        }
    }.flowOn(Dispatchers.IO)

    fun getComments(postId: String, page: Int = 1, size: Int = 20): Flow<Resource<List<CommentDto>>> = flow {
        emit(Resource.Loading)
        try {
            val response = postApi.getComments(postId, page, size)
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error(parseError(response.errorBody()?.string())))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "加载评论失败"))
        }
    }.flowOn(Dispatchers.IO)

    fun createComment(
        postId: String,
        content: String,
        parentId: String? = null,
        imageUrl: String? = null,
        imageKind: String? = null
    ): Flow<Resource<CommentDto>> = flow {
        emit(Resource.Loading)
        try {
            val response = postApi.createComment(
                CreateCommentRequest(
                    postId = postId,
                    content = content,
                    parentId = parentId,
                    imageUrl = imageUrl,
                    imageKind = imageKind
                )
            )
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error(parseError(response.errorBody()?.string())))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "发表评论失败"))
        }
    }.flowOn(Dispatchers.IO)

    fun getNotifications(page: Int = 1, size: Int = 20): Flow<Resource<List<NotificationDto>>> = flow {
        emit(Resource.Loading)
        try {
            val response = postApi.getNotifications(page, size)
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error(parseError(response.errorBody()?.string())))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "加载通知失败"))
        }
    }.flowOn(Dispatchers.IO)

    fun markAllNotificationsAsRead(): Flow<Resource<Unit>> = flow {
        try {
            val response = postApi.markAllNotificationsAsRead()
            if (response.isSuccessful) {
                emit(Resource.Success(Unit))
            } else {
                emit(Resource.Error("标记已读失败"))
            }
        } catch (_: Exception) {
            emit(Resource.Error("标记失败"))
        }
    }.flowOn(Dispatchers.IO)

    fun markSingleNotificationRead(id: String): Flow<Resource<Unit>> = flow {
        try {
            val response = postApi.markSingleNotificationRead(id)
            if (response.isSuccessful) {
                emit(Resource.Success(Unit))
            } else {
                emit(Resource.Error("标记已读失败"))
            }
        } catch (_: Exception) {
            emit(Resource.Error("网络异常"))
        }
    }.flowOn(Dispatchers.IO)

    fun deleteSingleNotification(id: String): Flow<Resource<Unit>> = flow {
        try {
            val response = postApi.deleteSingleNotification(id)
            if (response.isSuccessful) {
                emit(Resource.Success(Unit))
            } else {
                emit(Resource.Error("删除通知失败"))
            }
        } catch (_: Exception) {
            emit(Resource.Error("删除异常"))
        }
    }.flowOn(Dispatchers.IO)

    fun clearAllNotifications(): Flow<Resource<Unit>> = flow {
        try {
            val response = postApi.clearAllNotifications()
            if (response.isSuccessful) {
                emit(Resource.Success(Unit))
            } else {
                emit(Resource.Error("清空通知失败"))
            }
        } catch (_: Exception) {
            emit(Resource.Error("清空异常"))
        }
    }.flowOn(Dispatchers.IO)

    private fun parseError(errorBody: String?): String {
        return try {
            if (!errorBody.isNullOrBlank()) {
                json.decodeFromString<ErrorResponse>(errorBody).detail ?: "服务开小差了"
            } else "服务开小差了"
        } catch (_: Exception) {
            "服务开小差了"
        }
    }
}
