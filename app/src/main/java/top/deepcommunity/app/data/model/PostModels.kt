package top.deepcommunity.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CategoryDto(
    val id: Int,
    val name: String,
    val description: String? = null,
    val icon: String? = null
)

@Serializable
data class PostAuthorDto(
    val id: String? = null,
    val username: String? = null,
    val nickname: String? = null,
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    @SerialName("avatar")
    val avatar: String? = null,
    val level: Int = 1
) {
    val finalAvatarUrl: String?
        get() = avatarUrl?.takeIf { it.isNotBlank() } ?: avatar?.takeIf { it.isNotBlank() }
}

@Serializable
data class PostItemDto(
    val id: String,
    val title: String,
    @SerialName("content_md")
    val contentMd: String? = null,
    @SerialName("content_html")
    val contentHtml: String? = null,
    @SerialName("cover_image")
    val coverImage: String? = null,
    @SerialName("image_urls")
    val imageUrls: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    @SerialName("category_id")
    val categoryId: Int? = null,
    val author: PostAuthorDto? = null,
    @SerialName("author_username")
    val authorUsername: String? = null,
    @SerialName("author_nickname")
    val authorNickname: String? = null,
    @SerialName("author_avatar")
    val authorAvatar: String? = null,
    @SerialName("author_avatar_url")
    val authorAvatarUrl: String? = null,
    @SerialName("author_level")
    val authorLevel: Int = 1,
    @SerialName("view_count")
    val viewCount: Int = 0,
    @SerialName("like_count")
    val likeCount: Int = 0,
    @SerialName("comment_count")
    val commentCount: Int = 0,
    @SerialName("is_liked")
    val isLiked: Boolean = false,
    @SerialName("is_collected")
    val isCollected: Boolean = false,
    @SerialName("poll_id")
    val pollId: String? = null,
    @SerialName("created_at")
    val createdAt: String
) {
    val finalAvatarUrl: String?
        get() = authorAvatar?.takeIf { it.isNotBlank() }
            ?: authorAvatarUrl?.takeIf { it.isNotBlank() }
            ?: author?.avatarUrl
}

@Serializable
data class CreatePostRequest(
    val title: String,
    @SerialName("content_md")
    val contentMd: String,
    @SerialName("category_id")
    val categoryId: Int = 1,
    @SerialName("post_type")
    val postType: String = "post",
    val status: String = "published",
    @SerialName("comment_policy")
    val commentPolicy: String = "open",
    val tags: List<String> = emptyList(),
    @SerialName("image_urls")
    val imageUrls: List<String> = emptyList()
)

// 随帖投票
@Serializable
data class PollOptionDto(
    val id: Int,
    @SerialName("option_text")
    val optionText: String,
    @SerialName("vote_count")
    val voteCount: Int = 0,
    val percentage: Float = 0f,
    @SerialName("is_voted")
    val isVoted: Boolean = false
)

@Serializable
data class PollDetailDto(
    val id: String,
    val title: String,
    @SerialName("is_multiple")
    val isMultiple: Boolean = false,
    @SerialName("max_choices")
    val maxChoices: Int = 1,
    @SerialName("is_expired")
    val isExpired: Boolean = false,
    @SerialName("total_votes")
    val totalVotes: Int = 0,
    @SerialName("has_voted")
    val hasVoted: Boolean = false,
    val options: List<PollOptionDto> = emptyList()
)

@Serializable
data class PollOptionCreateDto(
    @SerialName("option_text")
    val optionText: String
)

@Serializable
data class PollCreateRequest(
    @SerialName("post_id")
    val postId: String,
    val title: String,
    val options: List<PollOptionCreateDto>,
    @SerialName("is_multiple")
    val isMultiple: Boolean = false,
    @SerialName("max_choices")
    val maxChoices: Int = 1,
    @SerialName("expires_in_days")
    val expiresInDays: Int = 7
)

@Serializable
data class PollVoteRequest(
    @SerialName("option_ids")
    val optionIds: List<Int>
)

// 草稿
@Serializable
data class DraftItemDto(
    val id: String,
    val title: String? = null,
    @SerialName("content_md")
    val contentMd: String? = null,
    @SerialName("category_name")
    val categoryName: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("char_count")
    val charCount: Int = 0
)

@Serializable
data class SaveDraftRequest(
    @SerialName("draft_id")
    val draftId: String? = null,
    val title: String = "",
    @SerialName("content_md")
    val contentMd: String = "",
    @SerialName("category_id")
    val categoryId: Int? = null,
    val tags: List<String> = emptyList(),
    @SerialName("image_urls")
    val imageUrls: List<String> = emptyList()
)

@Serializable
data class CommentDto(
    val id: String,
    @SerialName("post_id")
    val postId: String,
    val content: String,
    @SerialName("parent_id")
    val parentId: String? = null,
    @SerialName("image_url")
    val imageUrl: String? = null,
    val author: PostAuthorDto? = null,
    @SerialName("author_username")
    val authorUsername: String? = null,
    @SerialName("author_nickname")
    val authorNickname: String? = null,
    @SerialName("author_avatar")
    val authorAvatar: String? = null,
    @SerialName("author_level")
    val authorLevel: Int? = null,
    @SerialName("is_anonymous")
    val isAnonymous: Boolean = false,
    @SerialName("anonymous_alias")
    val anonymousAlias: String? = null,
    @SerialName("like_count")
    val likeCount: Int = 0,
    @SerialName("is_liked")
    val isLiked: Boolean = false,
    val replies: List<CommentDto> = emptyList(),
    @SerialName("created_at")
    val createdAt: String = ""
) {
    val displayAuthorName: String
        get() = if (isAnonymous) (anonymousAlias ?: "匿名居民")
        else (authorNickname?.ifBlank { null } ?: authorUsername ?: author?.nickname ?: author?.username ?: "社区居民")

    val displayAuthorAvatar: String?
        get() = if (isAnonymous) null else (authorAvatar ?: author?.avatarUrl)
}

@Serializable
data class CreateCommentRequest(
    @SerialName("post_id")
    val postId: String,
    val content: String,
    @SerialName("parent_id")
    val parentId: String? = null,
    @SerialName("reply_to_user_id")
    val replyToUserId: String? = null,
    @SerialName("is_anonymous")
    val isAnonymous: Boolean = false,
    @SerialName("image_url")
    val imageUrl: String? = null,
    @SerialName("image_kind")
    val imageKind: String? = null
)

@Serializable
data class LikeResponse(
    @SerialName("is_liked")
    val isLiked: Boolean = false,
    @SerialName("like_count")
    val likeCount: Int = 0
)

@Serializable
data class CollectResponse(
    @SerialName("is_collected")
    val isCollected: Boolean = false,
    @SerialName("collect_count")
    val collectCount: Int = 0
)

@Serializable
data class UploadImageResponse(
    val url: String? = null,
    @SerialName("avatar_url")
    val avatarUrl: String? = null
) {
    val finalUrl: String
        get() = url ?: avatarUrl ?: ""
}

@Serializable
data class NotificationDto(
    val id: String,
    @SerialName("action_type")
    val actionType: String,
    val brief: String,
    @SerialName("target_id")
    val targetId: String? = null,
    @SerialName("comment_id")
    val commentId: String? = null,
    @SerialName("is_read")
    val isRead: Boolean = false,
    @SerialName("created_at")
    val createdAt: String
)

@Serializable
data class UnreadCountDto(
    val count: Int = 0
)
