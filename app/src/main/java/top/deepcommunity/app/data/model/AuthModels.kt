package top.deepcommunity.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    @SerialName("username_or_email")
    val usernameOrEmail: String,
    @SerialName("password")
    val password: String,
    @SerialName("captcha_token")
    val captchaToken: String? = null
)

@Serializable
data class LoginResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("refresh_token")
    val refreshToken: String? = null,
    @SerialName("token_type")
    val tokenType: String = "bearer"
)

@Serializable
data class UserDto(
    val id: String,
    val username: String,
    val nickname: String? = null,
    val email: String? = null,
    val role: String? = null,
    val level: Int = 1,
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    @SerialName("is_online")
    val isOnline: Boolean = false,
    val bio: String? = null,
    @SerialName("followers_count")
    val followersCount: Int = 0,
    @SerialName("following_count")
    val followingCount: Int = 0
)

@Serializable
data class ErrorResponse(
    val detail: String? = null,
    val message: String? = null
)

@Serializable
data class MessageResponse(
    val message: String
)

@Serializable
data class CheckinStatusDetailDto(
    @SerialName("checked_today")
    val checkedToday: Boolean = false,
    val streak: Int = 0,
    @SerialName("best_streak")
    val bestStreak: Int = 0,
    @SerialName("makeup_earned")
    val makeupEarned: Int = 0,
    @SerialName("makeup_used")
    val makeupUsed: Int = 0,
    @SerialName("makeup_left")
    val makeupLeft: Int = 0
)

@Serializable
data class AchievementDto(
    val code: String,
    val name: String,
    val subtitle: String? = null,
    val description: String? = null,
    val icon: String = "🏅",
    val rarity: String = "common",
    @SerialName("unlocked_at")
    val unlockedAt: String? = null
)
