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
import top.deepcommunity.app.data.model.AchievementDto
import top.deepcommunity.app.data.model.BlockActionResponse
import top.deepcommunity.app.data.model.BlockedUserDto
import top.deepcommunity.app.data.model.CheckinStatusDetailDto
import top.deepcommunity.app.data.model.FollowUserItemDto
import top.deepcommunity.app.data.model.MessageResponse
import top.deepcommunity.app.data.model.PostItemDto
import top.deepcommunity.app.data.model.PublicUserProfileDto
import top.deepcommunity.app.data.model.UpdateDndRequest
import top.deepcommunity.app.data.model.UpdatePrivacyRequest
import top.deepcommunity.app.data.model.UploadImageResponse
import top.deepcommunity.app.data.model.UserDto

interface UserApi {

    @GET("api/users/profile/{username}")
    suspend fun getUserProfile(@Path("username") username: String): Response<UserDto>

    @PUT("api/users/me")
    suspend fun updateProfile(@Body profile: Map<String, String>): Response<UserDto>

    @Multipart
    @POST("api/users/me/avatar")
    suspend fun updateAvatar(@Part file: MultipartBody.Part): Response<UploadImageResponse>

    @POST("api/users/{username}/follow")
    suspend fun followUser(@Path("username") username: String): Response<MessageResponse>

    @DELETE("api/users/{username}/follow")
    suspend fun unfollowUser(@Path("username") username: String): Response<MessageResponse>

    @POST("api/users/checkin")
    suspend fun checkIn(): Response<MessageResponse>

    @GET("api/users/checkin/status")
    suspend fun getCheckinStatus(): Response<CheckinStatusDetailDto>

    @POST("api/users/checkin/makeup")
    suspend fun makeupCheckin(@Body payload: Map<String, String> = emptyMap()): Response<MessageResponse>

    @GET("api/achievements/me")
    suspend fun getMyAchievements(): Response<List<AchievementDto>>

    @POST("api/users/{username}/block")
    suspend fun blockUser(@Path("username") username: String): Response<BlockActionResponse>

    @DELETE("api/users/{username}/block")
    suspend fun unblockUser(@Path("username") username: String): Response<BlockActionResponse>

    @GET("api/users/me/blocks")
    suspend fun getMyBlocks(): Response<List<BlockedUserDto>>

    @GET("api/users/me/posts")
    suspend fun getMyPosts(
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20
    ): Response<List<PostItemDto>>

    @GET("api/users/me/collections")
    suspend fun getMyCollections(
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20
    ): Response<List<PostItemDto>>

    @GET("api/users/{username}/followers")
    suspend fun getFollowers(@Path("username") username: String): Response<List<FollowUserItemDto>>

    @GET("api/users/{username}/following")
    suspend fun getFollowing(@Path("username") username: String): Response<List<FollowUserItemDto>>

    @POST("api/users/{username}/follow")
    suspend fun followUser(@Path("username") username: String): Response<MessageResponse>

    @DELETE("api/users/{username}/follow")
    suspend fun unfollowUser(@Path("username") username: String): Response<MessageResponse>

    @GET("api/users/profile/{username}")
    suspend fun getUserPublicProfile(@Path("username") username: String): Response<PublicUserProfileDto>

    @PUT("api/users/me/privacy")
    suspend fun updatePrivacy(@Body request: UpdatePrivacyRequest): Response<MessageResponse>

    @PUT("api/users/me/dnd")
    suspend fun updateDnd(@Body request: UpdateDndRequest): Response<MessageResponse>
}
