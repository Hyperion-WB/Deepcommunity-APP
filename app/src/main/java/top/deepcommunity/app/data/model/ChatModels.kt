package top.deepcommunity.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import top.deepcommunity.app.core.common.Constants

@Serializable
data class ConversationDto(
    val id: String,
    @SerialName("peer_user_id")
    val peerUserId: String? = null,
    @SerialName("peer_username")
    val peerUsername: String? = null,
    @SerialName("peer_nickname")
    val peerNickname: String? = null,
    @SerialName("peer_avatar")
    val peerAvatar: String? = null,
    @SerialName("target_user")
    val targetUser: UserDto? = null,
    @SerialName("last_message")
    val lastMessage: ChatMessageDto? = null,
    @SerialName("last_message_preview")
    val lastMessagePreview: String? = null,
    @SerialName("last_message_at")
    val lastMessageAt: String? = null,
    @SerialName("unread_count")
    val unreadCount: Int = 0,
    @SerialName("updated_at")
    val updatedAt: String? = null
) {
    val displayName: String
        get() = peerNickname?.takeIf { it.isNotBlank() }
            ?: targetUser?.nickname?.takeIf { it.isNotBlank() }
            ?: peerUsername
            ?: targetUser?.username
            ?: "用户"

    val finalAvatarUrl: String?
        get() {
            val raw = peerAvatar?.takeIf { it.isNotBlank() } ?: targetUser?.avatarUrl
            if (raw.isNullOrBlank()) return null
            return if (raw.startsWith("http://") || raw.startsWith("https://")) raw
            else Constants.BASE_URL.trimEnd('/') + "/" + raw.trimStart('/')
        }
}

@Serializable
data class GroupRoomDto(
    val id: String,
    val name: String,
    val description: String? = null,
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    @SerialName("is_public")
    val isPublic: Boolean = true,
    @SerialName("member_count")
    val memberCount: Int = 0,
    val announcement: String? = null,
    @SerialName("creator_username")
    val creatorUsername: String? = null,
    @SerialName("my_role")
    val myRole: String? = null,
    @SerialName("last_message")
    val lastMessage: ChatMessageDto? = null,
    @SerialName("last_message_preview")
    val lastMessagePreview: String? = null,
    @SerialName("last_message_at")
    val lastMessageAt: String? = null,
    @SerialName("unread_count")
    val unreadCount: Int = 0,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null
) {
    val finalAvatarUrl: String?
        get() {
            if (avatarUrl.isNullOrBlank()) return null
            return if (avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://")) avatarUrl
            else Constants.BASE_URL.trimEnd('/') + "/" + avatarUrl.trimStart('/')
        }
}

@Serializable
data class GroupMemberDto(
    @SerialName("user_id")
    val userId: String,
    val username: String,
    val nickname: String? = null,
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    val level: Int = 1,
    val role: String = "member", // owner / admin / member
    @SerialName("joined_at")
    val joinedAt: String = ""
) {
    val displayName: String
        get() = nickname?.takeIf { it.isNotBlank() } ?: username

    val finalAvatarUrl: String?
        get() {
            if (avatarUrl.isNullOrBlank()) return null
            return if (avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://")) avatarUrl
            else Constants.BASE_URL.trimEnd('/') + "/" + avatarUrl.trimStart('/')
        }
}

@Serializable
data class ChatMessageDto(
    val id: String,
    @SerialName("conversation_id")
    val conversationId: String? = null,
    @SerialName("room_id")
    val roomId: String? = null,
    @SerialName("sender_id")
    val senderId: String,
    val sender: UserDto? = null,
    @SerialName("sender_username")
    val senderUsername: String? = null,
    @SerialName("sender_nickname")
    val senderNickname: String? = null,
    @SerialName("sender_avatar")
    val senderAvatar: String? = null,
    @SerialName("sender_level")
    val senderLevel: Int = 1,
    val content: String,
    @SerialName("msg_type")
    val msgType: String = "text", // text / image / sticker
    @SerialName("client_msg_id")
    val clientMsgId: String? = null,
    @SerialName("reply_to_message_id")
    val replyToMessageId: String? = null,
    @SerialName("reply_to_id")
    val replyToId: String? = null,
    @SerialName("reply_sender")
    val replySender: String = "",
    @SerialName("reply_snippet")
    val replySnippet: String = "",
    @SerialName("is_recalled")
    val isRecalled: Boolean = false,
    @SerialName("created_at")
    val createdAt: String
) {
    val effectiveReplyId: String?
        get() = replyToId ?: replyToMessageId

    val displayName: String
        get() = senderNickname?.takeIf { it.isNotBlank() }
            ?: sender?.nickname?.takeIf { it.isNotBlank() }
            ?: senderUsername?.takeIf { it.isNotBlank() }
            ?: sender?.username
            ?: "用户"

    val finalAvatarUrl: String?
        get() {
            val raw = senderAvatar?.takeIf { it.isNotBlank() } ?: sender?.avatarUrl
            if (raw.isNullOrBlank()) return null
            return if (raw.startsWith("http://") || raw.startsWith("https://")) raw
            else Constants.BASE_URL.trimEnd('/') + "/" + raw.trimStart('/')
        }
}

@Serializable
data class SendMessageRequest(
    val content: String,
    @SerialName("client_msg_id")
    val clientMsgId: String,
    @SerialName("reply_to_message_id")
    val replyToMessageId: String? = null
)

@Serializable
data class StickerItemDto(
    val id: String,
    val url: String,
    @SerialName("thumb_url")
    val thumbUrl: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val size: Int = 0,
    @SerialName("is_animated")
    val isAnimated: Boolean = false,
    val source: String? = null
) {
    val finalUrl: String
        get() = if (url.startsWith("http://") || url.startsWith("https://")) url
        else Constants.BASE_URL.trimEnd('/') + "/" + url.trimStart('/')

    val finalThumbUrl: String
        get() {
            val target = thumbUrl?.takeIf { it.isNotBlank() } ?: url
            return if (target.startsWith("http://") || target.startsWith("https://")) target
            else Constants.BASE_URL.trimEnd('/') + "/" + target.trimStart('/')
        }
}

@Serializable
data class StickerListResponse(
    val items: List<StickerItemDto> = emptyList(),
    val used: Int = 0,
    val limit: Int = 30
)

@Serializable
data class KickMemberRequest(
    @SerialName("user_id")
    val userId: String
)

@Serializable
data class UpdateMemberRoleRequest(
    val role: String // admin / member
)

@Serializable
data class UpdateGroupRoomRequest(
    val name: String? = null,
    val description: String? = null,
    val announcement: String? = null
)

@Serializable
data class BlockedUserDto(
    val id: String,
    val username: String,
    val nickname: String? = null,
    val avatar: String? = null,
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    @SerialName("blocked_at")
    val blockedAt: String? = null
) {
    val displayName: String
        get() = nickname?.takeIf { it.isNotBlank() } ?: username

    val finalAvatarUrl: String?
        get() {
            val raw = avatar?.takeIf { it.isNotBlank() } ?: avatarUrl
            if (raw.isNullOrBlank()) return null
            return if (raw.startsWith("http://") || raw.startsWith("https://")) raw
            else Constants.BASE_URL.trimEnd('/') + "/" + raw.trimStart('/')
        }
}

@Serializable
data class BlockActionResponse(
    val message: String? = null,
    @SerialName("is_blocked")
    val isBlocked: Boolean = false
)

@Serializable
data class CollectStickerRequest(
    val url: String
)

@Serializable
data class MuteMemberRequest(
    @SerialName("duration_minutes")
    val durationMinutes: Int = 60
)

@Serializable
data class GroupCheckinStatusDto(
    @SerialName("is_checked_in")
    val isCheckedIn: Boolean = false,
    @SerialName("checkin_count")
    val checkinCount: Int = 0,
    @SerialName("checkin_word")
    val checkinWord: String = "打卡",
    @SerialName("group_level")
    val groupLevel: Int = 1,
    @SerialName("group_exp")
    val groupExp: Int = 0
)

@Serializable
data class GroupApplyRequestDto(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    val username: String,
    val nickname: String? = null,
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    val reason: String = "",
    @SerialName("created_at")
    val createdAt: String = ""
)
