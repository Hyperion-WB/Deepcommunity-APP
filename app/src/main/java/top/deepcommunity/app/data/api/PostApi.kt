package top.deepcommunity.app.data.api

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import top.deepcommunity.app.data.model.CategoryDto
import top.deepcommunity.app.data.model.CollectResponse
import top.deepcommunity.app.data.model.CommentDto
import top.deepcommunity.app.data.model.CreateCommentRequest
import top.deepcommunity.app.data.model.CreatePostRequest
import top.deepcommunity.app.data.model.DraftItemDto
import top.deepcommunity.app.data.model.LikeResponse
import top.deepcommunity.app.data.model.MessageResponse
import top.deepcommunity.app.data.model.NotificationDto
import top.deepcommunity.app.data.model.PollCreateRequest
import top.deepcommunity.app.data.model.PollDetailDto
import top.deepcommunity.app.data.model.PollVoteRequest
import top.deepcommunity.app.data.model.PostItemDto
import top.deepcommunity.app.data.model.ReactionToggleRequest
import top.deepcommunity.app.data.model.SaveDraftRequest
import top.deepcommunity.app.data.model.SystemAnnouncementDto
import top.deepcommunity.app.data.model.UnreadCountDto
import top.deepcommunity.app.data.model.UploadImageResponse

interface PostApi {

    @GET("api/posts")
    suspend fun getPosts(
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20,
        @Query("sort_by") sortBy: String = "latest",
        @Query("category_id") categoryId: Int? = null,
        @Query("q") query: String? = null
    ): Response<List<PostItemDto>>

    @GET("api/posts/categories")
    suspend fun getCategories(): Response<List<CategoryDto>>

    @GET("api/posts/{id}")
    suspend fun getPostDetail(@Path("id") id: String): Response<PostItemDto>

    @POST("api/posts")
    suspend fun createPost(@Body request: CreatePostRequest): Response<PostItemDto>

    @POST("api/posts/{id}/like")
    suspend fun toggleLike(@Path("id") id: String): Response<LikeResponse>

    @POST("api/posts/{id}/collect")
    suspend fun toggleCollect(@Path("id") id: String): Response<CollectResponse>

    @Multipart
    @POST("api/posts/upload-image")
    suspend fun uploadImage(@Part file: MultipartBody.Part): Response<UploadImageResponse>

    // 随帖投票
    @POST("api/polls")
    suspend fun createPoll(@Body request: PollCreateRequest): Response<PollDetailDto>

    @GET("api/polls/post/{post_id}")
    suspend fun getPostPoll(@Path("post_id") postId: String): Response<PollDetailDto>

    @POST("api/polls/{poll_id}/vote")
    suspend fun votePoll(
        @Path("poll_id") pollId: String,
        @Body request: PollVoteRequest
    ): Response<MessageResponse>

    // 草稿
    @GET("api/posts/drafts")
    suspend fun getDrafts(): Response<List<DraftItemDto>>

    @POST("api/posts/draft/save")
    suspend fun saveDraft(@Body request: SaveDraftRequest): Response<DraftItemDto>

    // 评论相关
    @GET("api/comments/post/{post_id}")
    suspend fun getComments(
        @Path("post_id") postId: String,
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20
    ): Response<List<CommentDto>>

    @POST("api/comments")
    suspend fun createComment(@Body request: CreateCommentRequest): Response<CommentDto>

    @POST("api/comments/{id}/like")
    suspend fun toggleCommentLike(@Path("id") commentId: String): Response<LikeResponse>

    @DELETE("api/comments/{id}")
    suspend fun deleteComment(@Path("id") commentId: String): Response<MessageResponse>

    // 通知
    @GET("api/users/me/notifications")
    suspend fun getNotifications(
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20
    ): Response<List<NotificationDto>>

    @GET("api/users/me/notifications/unread-count")
    suspend fun getNotificationUnreadCount(): Response<UnreadCountDto>

    @PUT("api/users/me/notifications/read")
    suspend fun markAllNotificationsAsRead(): Response<okhttp3.ResponseBody>

    @PUT("api/users/me/notifications/{id}/read")
    suspend fun markSingleNotificationRead(@Path("id") id: String): Response<okhttp3.ResponseBody>

    @DELETE("api/users/me/notifications/{id}")
    suspend fun deleteSingleNotification(@Path("id") id: String): Response<okhttp3.ResponseBody>

    @DELETE("api/users/me/notifications")
    suspend fun clearAllNotifications(): Response<okhttp3.ResponseBody>

    // 表情回应 Reactions
    @POST("api/reactions/toggle")
    suspend fun toggleReaction(@Body request: ReactionToggleRequest): Response<okhttp3.ResponseBody>

    // 全站广播系统公告
    @GET("api/admin/announcement/current")
    suspend fun getCurrentAnnouncement(): Response<SystemAnnouncementDto>
}
