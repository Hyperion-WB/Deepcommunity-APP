package top.deepcommunity.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class QaConfigDto(
    val bounty: Int = 10,
    @SerialName("accept_window_days")
    val acceptWindowDays: Int = 7,
    @SerialName("max_accepted")
    val maxAccepted: Int = 5
)

@Serializable
data class QaQuestionStateDto(
    @SerialName("is_qa")
    val isQa: Boolean = false,
    val bounty: Int = 0,
    val status: String = "open", // "open" | "settled" | "expired"
    @SerialName("accepted_count")
    val acceptedCount: Int = 0,
    @SerialName("can_settle")
    val canSettle: Boolean = false,
    @SerialName("accepted_comment_ids")
    val acceptedCommentIds: List<String> = emptyList()
)

@Serializable
data class QaSettleRequest(
    @SerialName("comment_ids")
    val commentIds: List<String>,
    val confirm: Boolean = true
)
