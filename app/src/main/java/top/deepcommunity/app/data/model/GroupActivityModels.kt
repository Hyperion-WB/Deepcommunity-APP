package top.deepcommunity.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FortuneStatusDto(
    val used: Int = 0,
    val limit: Int = 2,
    val remaining: Int = 2
)

@Serializable
data class FortuneResultDto(
    val tier: String = "good",
    val label: String = "上签",
    val grade: String = "中吉",
    val stars: Int = 4,
    val icon: String = "✨",
    val wish: String = "",
    val quote: String = "",
    val lucky: String = "",
    val remaining: Int = 0,
    val date: String? = null
)

@Serializable
data class IdiomChainDetailDto(
    val starter: String = "",
    @SerialName("current_char")
    val currentChar: String = "",
    val used: List<String> = emptyList(),
    val round: Int = 1,
    @SerialName("started_by")
    val startedBy: String? = null
)

@Serializable
data class IdiomChainStateDto(
    val active: Boolean = false,
    @SerialName("game_id")
    val gameId: String? = null,
    val state: IdiomChainDetailDto? = null,
    @SerialName("can_manage")
    val canManage: Boolean = false
)

@Serializable
data class IdiomAnswerRequest(
    val word: String
)

@Serializable
data class IdiomAnswerResponse(
    val ok: Boolean = false,
    val reason: String? = null,
    val state: IdiomChainDetailDto? = null
)
