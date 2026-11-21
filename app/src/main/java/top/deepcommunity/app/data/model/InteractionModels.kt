package top.deepcommunity.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ==============================
// 1. 表情反应聚合 (Reactions Summary & Batch)
// ==============================
@Serializable
data class ReactionSummaryDto(
    val counts: Map<String, Int> = emptyMap(),
    @SerialName("user_reactions")
    val userReactions: List<String> = emptyList()
)

@Serializable
data class ReactionBatchDto(
    val items: Map<String, ReactionSummaryDto> = emptyMap()
)

// ==============================
// 2. 群投票 (Group Polls)
// ==============================
@Serializable
data class GroupPollOptionDto(
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
data class GroupPollDto(
    val id: String,
    @SerialName("room_id")
    val roomId: String? = null,
    val title: String,
    val description: String? = null,
    @SerialName("is_multiple")
    val isMultiple: Boolean = false,
    @SerialName("max_choices")
    val maxChoices: Int = 1,
    @SerialName("total_votes")
    val totalVotes: Int = 0,
    @SerialName("has_voted")
    val hasVoted: Boolean = false,
    @SerialName("is_expired")
    val isExpired: Boolean = false,
    @SerialName("expires_at")
    val expiresAt: String? = null,
    val options: List<GroupPollOptionDto> = emptyList()
)

@Serializable
data class GroupPollListDto(
    val items: List<GroupPollDto> = emptyList()
)

@Serializable
data class GroupPollCreateRequest(
    val title: String,
    val description: String? = null,
    val options: List<String>,
    @SerialName("is_multiple")
    val isMultiple: Boolean = false,
    @SerialName("max_choices")
    val maxChoices: Int = 1,
    @SerialName("duration_hours")
    val durationHours: Int = 72
)

@Serializable
data class GroupPollVoteRequest(
    @SerialName("option_ids")
    val optionIds: List<Int>
)

// ==============================
// 3. 棋类即时对战 (Battles)
// ==============================
@Serializable
data class BattleMatchDto(
    val id: String,
    @SerialName("game_code")
    val gameCode: String, // gomoku / tictactoe / xiangqi
    @SerialName("inviter_id")
    val inviterId: String,
    @SerialName("invitee_id")
    val inviteeId: String,
    val status: String, // pending / active / finished / surrendered / expired
    @SerialName("move_seq")
    val moveSeq: Int = 0,
    @SerialName("turn_user_id")
    val turnUserId: String? = null,
    @SerialName("winner_id")
    val winnerId: String? = null,
    val result: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("board_state")
    val boardStateJson: String? = null
)

@Serializable
data class BattleListDto(
    val items: List<BattleMatchDto> = emptyList()
)

@Serializable
data class CreateBattleRequest(
    @SerialName("game_code")
    val gameCode: String,
    @SerialName("invitee_id")
    val inviteeId: String
)

@Serializable
data class BattleMoveRequest(
    @SerialName("expected_seq")
    val expectedSeq: Int,
    val x: Int,
    val y: Int,
    @SerialName("from_x")
    val fromX: Int? = null,
    @SerialName("from_y")
    val fromY: Int? = null
)
