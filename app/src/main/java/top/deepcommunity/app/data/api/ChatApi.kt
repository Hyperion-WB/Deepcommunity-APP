package top.deepcommunity.app.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.PUT
import top.deepcommunity.app.data.model.ChatMessageDto
import top.deepcommunity.app.data.model.ConversationDto
import top.deepcommunity.app.data.model.GroupMemberDto
import top.deepcommunity.app.data.model.GroupRoomDto
import top.deepcommunity.app.data.model.KickMemberRequest
import top.deepcommunity.app.data.model.MessageResponse
import top.deepcommunity.app.data.model.SendMessageRequest
import top.deepcommunity.app.data.model.StickerListResponse
import top.deepcommunity.app.data.model.UnreadCountDto
import top.deepcommunity.app.data.model.UpdateGroupRoomRequest
import top.deepcommunity.app.data.model.UpdateMemberRoleRequest

interface ChatApi {

    @GET("api/chat/unread-count")
    suspend fun getChatUnreadCount(): Response<UnreadCountDto>

    // 私聊
    @GET("api/chat/conversations")
    suspend fun getConversations(): Response<List<ConversationDto>>

    @POST("api/chat/conversations")
    suspend fun createOrGetConversation(@Query("target_user_id") targetUserId: String): Response<ConversationDto>

    @GET("api/chat/{conv_id}/messages")
    suspend fun getMessages(
        @Path("conv_id") convId: String,
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 50
    ): Response<List<ChatMessageDto>>

    @POST("api/chat/{conv_id}/messages")
    suspend fun sendMessage(
        @Path("conv_id") convId: String,
        @Body request: SendMessageRequest
    ): Response<ChatMessageDto>

    @POST("api/chat/messages/{msg_id}/recall")
    suspend fun recallMessage(@Path("msg_id") msgId: String): Response<MessageResponse>

    @POST("api/chat/conversations/{conv_id}/read")
    suspend fun markConversationRead(@Path("conv_id") convId: String): Response<MessageResponse>

    @DELETE("api/chat/conversations/{conv_id}")
    suspend fun deleteConversation(@Path("conv_id") convId: String): Response<MessageResponse>

    // 群聊
    @GET("api/chat/groups")
    suspend fun getGroups(): Response<List<GroupRoomDto>>

    @GET("api/chat/groups/{room_id}/messages")
    suspend fun getGroupMessages(
        @Path("room_id") roomId: String,
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 50
    ): Response<List<ChatMessageDto>>

    @POST("api/chat/groups/{room_id}/messages")
    suspend fun sendGroupMessage(
        @Path("room_id") roomId: String,
        @Body request: SendMessageRequest
    ): Response<ChatMessageDto>

    @POST("api/chat/groups/{room_id}/messages/{msg_id}/recall")
    suspend fun recallGroupMessage(
        @Path("room_id") roomId: String,
        @Path("msg_id") msgId: String
    ): Response<MessageResponse>

    @GET("api/chat/groups/{room_id}/members")
    suspend fun getGroupMembers(@Path("room_id") roomId: String): Response<List<GroupMemberDto>>

    @POST("api/chat/groups/{room_id}/kick")
    suspend fun kickGroupMember(
        @Path("room_id") roomId: String,
        @Body request: KickMemberRequest
    ): Response<MessageResponse>

    @POST("api/chat/groups/{room_id}/members/{target_user_id}/role")
    suspend fun updateMemberRole(
        @Path("room_id") roomId: String,
        @Path("target_user_id") targetUserId: String,
        @Body request: UpdateMemberRoleRequest
    ): Response<MessageResponse>

    @PUT("api/chat/groups/{room_id}")
    suspend fun updateGroupRoom(
        @Path("room_id") roomId: String,
        @Body request: UpdateGroupRoomRequest
    ): Response<GroupRoomDto>

    @POST("api/chat/groups/{room_id}/leave")
    suspend fun leaveGroup(@Path("room_id") roomId: String): Response<MessageResponse>

    // 表情包
    @GET("api/stickers/mine")
    suspend fun getMyStickers(): Response<StickerListResponse>
}
