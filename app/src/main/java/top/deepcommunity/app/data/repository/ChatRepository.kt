package top.deepcommunity.app.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import top.deepcommunity.app.core.common.Resource
import top.deepcommunity.app.core.network.WebSocketManager
import top.deepcommunity.app.data.api.ChatApi
import top.deepcommunity.app.data.model.ChatMessageDto
import top.deepcommunity.app.data.model.CollectStickerRequest
import top.deepcommunity.app.data.model.ConversationDto
import top.deepcommunity.app.data.model.ErrorResponse
import top.deepcommunity.app.data.model.FortuneResultDto
import top.deepcommunity.app.data.model.FortuneStatusDto
import top.deepcommunity.app.data.model.GroupApplyRequestDto
import top.deepcommunity.app.data.model.GroupCheckinStatusDto
import top.deepcommunity.app.data.model.GroupMemberDto
import top.deepcommunity.app.data.model.GroupRoomDto
import top.deepcommunity.app.data.model.IdiomAnswerRequest
import top.deepcommunity.app.data.model.IdiomAnswerResponse
import top.deepcommunity.app.data.model.IdiomChainStateDto
import top.deepcommunity.app.data.model.KickMemberRequest
import top.deepcommunity.app.data.model.MuteMemberRequest
import top.deepcommunity.app.data.model.SendMessageRequest
import top.deepcommunity.app.data.model.StickerListResponse
import top.deepcommunity.app.data.model.UpdateGroupRoomRequest
import top.deepcommunity.app.data.model.UpdateMemberRoleRequest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatApi: ChatApi,
    private val webSocketManager: WebSocketManager,
    private val json: Json
) {
    // 监听 WebSocket 推送消息
    val incomingRealtime: Flow<String> = webSocketManager.incomingMessages

    fun connectWebSocket() {
        webSocketManager.connect()
    }

    // 私聊
    fun getConversations(): Flow<Resource<List<ConversationDto>>> = flow {
        emit(Resource.Loading)
        try {
            val response = chatApi.getConversations()
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error("加载会话列表失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    fun createOrGetConversation(targetUserId: String): Flow<Resource<ConversationDto>> = flow {
        emit(Resource.Loading)
        try {
            val response = chatApi.createOrGetConversation(targetUserId)
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error("发起会话失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    fun getMessages(convId: String, page: Int = 1, size: Int = 50): Flow<Resource<List<ChatMessageDto>>> = flow {
        emit(Resource.Loading)
        try {
            val response = chatApi.getMessages(convId, page, size)
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error("加载聊天记录失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    fun sendMessage(
        convId: String,
        content: String,
        replyToMessageId: String? = null
    ): Flow<Resource<ChatMessageDto>> = flow {
        val clientMsgId = UUID.randomUUID().toString()
        val request = SendMessageRequest(
            content = content,
            clientMsgId = clientMsgId,
            replyToMessageId = replyToMessageId
        )
        try {
            val response = chatApi.sendMessage(convId, request)
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error(parseError(response.errorBody()?.string())))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "发送异常"))
        }
    }.flowOn(Dispatchers.IO)

    fun recallMessage(msgId: String): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading)
        try {
            val response = chatApi.recallMessage(msgId)
            if (response.isSuccessful) {
                emit(Resource.Success(Unit))
            } else {
                emit(Resource.Error(parseError(response.errorBody()?.string()) ?: "撤回失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "撤回异常"))
        }
    }.flowOn(Dispatchers.IO)

    fun getChatUnreadCount(): Flow<Resource<Int>> = flow {
        try {
            val response = chatApi.getChatUnreadCount()
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!.effectiveCount))
            } else {
                emit(Resource.Error("获取未读数失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    fun markConversationRead(convId: String): Flow<Resource<Unit>> = flow {
        try {
            chatApi.markConversationRead(convId)
            emit(Resource.Success(Unit))
        } catch (_: Exception) {
            emit(Resource.Error("标记失败"))
        }
    }.flowOn(Dispatchers.IO)

    fun markGroupRead(roomId: String): Flow<Resource<Unit>> = flow {
        try {
            chatApi.markGroupRead(roomId)
            emit(Resource.Success(Unit))
        } catch (_: Exception) {
            emit(Resource.Error("标记群已读失败"))
        }
    }.flowOn(Dispatchers.IO)

    // 群聊
    fun getGroups(): Flow<Resource<List<GroupRoomDto>>> = flow {
        emit(Resource.Loading)
        try {
            val response = chatApi.getGroups()
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error("获取群聊失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    fun getGroupMessages(roomId: String, page: Int = 1, size: Int = 50): Flow<Resource<List<ChatMessageDto>>> = flow {
        emit(Resource.Loading)
        try {
            val response = chatApi.getGroupMessages(roomId, page, size)
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error("获取群消息失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    fun sendGroupMessage(
        roomId: String,
        content: String,
        replyToMessageId: String? = null
    ): Flow<Resource<ChatMessageDto>> = flow {
        val clientMsgId = UUID.randomUUID().toString()
        val request = SendMessageRequest(
            content = content,
            clientMsgId = clientMsgId,
            replyToMessageId = replyToMessageId
        )
        try {
            val response = chatApi.sendGroupMessage(roomId, request)
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error(parseError(response.errorBody()?.string())))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "发送异常"))
        }
    }.flowOn(Dispatchers.IO)

    fun recallGroupMessage(roomId: String, msgId: String): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading)
        try {
            val response = chatApi.recallGroupMessage(roomId, msgId)
            if (response.isSuccessful) {
                emit(Resource.Success(Unit))
            } else {
                emit(Resource.Error(parseError(response.errorBody()?.string()) ?: "撤回失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "撤回异常"))
        }
    }.flowOn(Dispatchers.IO)

    fun getGroupMembers(roomId: String): Flow<Resource<List<GroupMemberDto>>> = flow {
        emit(Resource.Loading)
        try {
            val response = chatApi.getGroupMembers(roomId)
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error("获取群成员失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    fun leaveGroup(roomId: String): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading)
        try {
            val response = chatApi.leaveGroup(roomId)
            if (response.isSuccessful) {
                emit(Resource.Success(Unit))
            } else {
                emit(Resource.Error(parseError(response.errorBody()?.string()) ?: "退出群聊失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    fun kickGroupMember(roomId: String, userId: String): Flow<Resource<String>> = flow {
        emit(Resource.Loading)
        try {
            val response = chatApi.kickGroupMember(roomId, KickMemberRequest(userId))
            if (response.isSuccessful) {
                emit(Resource.Success(response.body()?.message ?: "移出群聊成功"))
            } else {
                emit(Resource.Error(parseError(response.errorBody()?.string()) ?: "操作失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    fun updateMemberRole(roomId: String, targetUserId: String, role: String): Flow<Resource<String>> = flow {
        emit(Resource.Loading)
        try {
            val response = chatApi.updateMemberRole(roomId, targetUserId, UpdateMemberRoleRequest(role))
            if (response.isSuccessful) {
                emit(Resource.Success(response.body()?.message ?: "设置成功"))
            } else {
                emit(Resource.Error(parseError(response.errorBody()?.string()) ?: "操作失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    fun updateGroupRoom(roomId: String, name: String?, description: String?, announcement: String?): Flow<Resource<GroupRoomDto>> = flow {
        emit(Resource.Loading)
        try {
            val response = chatApi.updateGroupRoom(roomId, UpdateGroupRoomRequest(name, description, announcement))
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error(parseError(response.errorBody()?.string()) ?: "更新失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    // 表情包
    fun getMyStickers(): Flow<Resource<StickerListResponse>> = flow {
        emit(Resource.Loading)
        try {
            val response = chatApi.getMyStickers()
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error("加载表情包失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    fun collectSticker(url: String): Flow<Resource<String>> = flow {
        emit(Resource.Loading)
        try {
            val response = chatApi.collectSticker(CollectStickerRequest(url))
            if (response.isSuccessful) {
                emit(Resource.Success(response.body()?.message ?: "已收藏到「我的表情」"))
            } else {
                emit(Resource.Error(parseError(response.errorBody()?.string()) ?: "收藏失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    // 群内摇骰子
    fun rollDice(roomId: String): Flow<Resource<ChatMessageDto>> = flow {
        emit(Resource.Loading)
        try {
            val response = chatApi.rollDice(roomId)
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error(parseError(response.errorBody()?.string()) ?: "掷骰子失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    // 群签到状态
    fun getGroupCheckinStatus(roomId: String): Flow<Resource<GroupCheckinStatusDto>> = flow {
        emit(Resource.Loading)
        try {
            val response = chatApi.getGroupCheckinStatus(roomId)
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error("获取签到状态失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    // 禁言与解禁
    fun muteMember(roomId: String, targetUserId: String, durationMinutes: Int = 60): Flow<Resource<String>> = flow {
        emit(Resource.Loading)
        try {
            val response = chatApi.muteMember(roomId, targetUserId, MuteMemberRequest(durationMinutes))
            if (response.isSuccessful) {
                emit(Resource.Success(response.body()?.message ?: "禁言成功"))
            } else {
                emit(Resource.Error(parseError(response.errorBody()?.string()) ?: "操作失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    fun unmuteMember(roomId: String, targetUserId: String): Flow<Resource<String>> = flow {
        emit(Resource.Loading)
        try {
            val response = chatApi.unmuteMember(roomId, targetUserId)
            if (response.isSuccessful) {
                emit(Resource.Success(response.body()?.message ?: "已解除禁言"))
            } else {
                emit(Resource.Error(parseError(response.errorBody()?.string()) ?: "操作失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    // 删除表情包
    fun deleteSticker(stickerId: String): Flow<Resource<String>> = flow {
        emit(Resource.Loading)
        try {
            val response = chatApi.deleteSticker(stickerId)
            if (response.isSuccessful) {
                emit(Resource.Success(response.body()?.message ?: "已移除表情"))
            } else {
                emit(Resource.Error(parseError(response.errorBody()?.string()) ?: "删除失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    // 入群审批
    fun getGroupRequests(roomId: String): Flow<Resource<List<GroupApplyRequestDto>>> = flow {
        emit(Resource.Loading)
        try {
            val response = chatApi.getGroupRequests(roomId)
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error("获取审批列表失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    fun approveGroupRequest(roomId: String, requestId: String): Flow<Resource<String>> = flow {
        emit(Resource.Loading)
        try {
            val response = chatApi.approveGroupRequest(roomId, requestId)
            if (response.isSuccessful) {
                emit(Resource.Success(response.body()?.message ?: "已批准入群"))
            } else {
                emit(Resource.Error(parseError(response.errorBody()?.string()) ?: "操作失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    fun rejectGroupRequest(roomId: String, requestId: String): Flow<Resource<String>> = flow {
        emit(Resource.Loading)
        try {
            val response = chatApi.rejectGroupRequest(roomId, requestId)
            if (response.isSuccessful) {
                emit(Resource.Success(response.body()?.message ?: "已拒绝入群申请"))
            } else {
                emit(Resource.Error(parseError(response.errorBody()?.string()) ?: "操作失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    fun getFortuneStatus(roomId: String): Flow<Resource<FortuneStatusDto>> = flow {
        emit(Resource.Loading)
        try {
            val response = chatApi.getFortuneStatus(roomId)
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error(parseError(response.errorBody()?.string()) ?: "获取求签状态失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    fun drawFortune(roomId: String): Flow<Resource<FortuneResultDto>> = flow {
        emit(Resource.Loading)
        try {
            val response = chatApi.drawFortune(roomId)
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error(parseError(response.errorBody()?.string()) ?: "求签失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    fun getIdiomChainState(roomId: String): Flow<Resource<IdiomChainStateDto>> = flow {
        emit(Resource.Loading)
        try {
            val response = chatApi.getIdiomChainState(roomId)
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error(parseError(response.errorBody()?.string()) ?: "获取成语接龙状态失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    fun startIdiomChain(roomId: String): Flow<Resource<IdiomChainStateDto>> = flow {
        emit(Resource.Loading)
        try {
            val response = chatApi.startIdiomChain(roomId)
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error(parseError(response.errorBody()?.string()) ?: "开局失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    fun answerIdiomChain(roomId: String, word: String): Flow<Resource<IdiomAnswerResponse>> = flow {
        emit(Resource.Loading)
        try {
            val response = chatApi.answerIdiomChain(roomId, IdiomAnswerRequest(word))
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error(parseError(response.errorBody()?.string()) ?: "接龙提交失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    fun endIdiomChain(roomId: String): Flow<Resource<String>> = flow {
        emit(Resource.Loading)
        try {
            val response = chatApi.endIdiomChain(roomId)
            if (response.isSuccessful) {
                emit(Resource.Success(response.body()?.message ?: "接龙已结束"))
            } else {
                emit(Resource.Error(parseError(response.errorBody()?.string()) ?: "结束接龙失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    private fun parseError(errorJson: String?): String {
        if (errorJson.isNullOrBlank()) return "请求失败"
        return try {
            val err = json.decodeFromString<ErrorResponse>(errorJson)
            err.detail ?: err.message ?: "操作未成功"
        } catch (_: Exception) {
            "服务开小差了，请稍后再试"
        }
    }
}
