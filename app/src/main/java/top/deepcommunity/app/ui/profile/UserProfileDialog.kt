package top.deepcommunity.app.ui.profile

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.deepcommunity.app.core.common.Resource
import top.deepcommunity.app.core.designsystem.component.DeepButton
import top.deepcommunity.app.core.designsystem.component.GlassBox
import top.deepcommunity.app.core.designsystem.component.UserAvatar
import top.deepcommunity.app.core.designsystem.theme.LocalExtendedColors
import top.deepcommunity.app.data.model.PublicUserProfileDto
import top.deepcommunity.app.data.repository.UserRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileBottomSheet(
    userId: String? = null,
    username: String? = null,
    userRepository: UserRepository,
    onDismissRequest: () -> Unit,
    onNavigateToChat: ((targetUserId: String, targetUserName: String) -> Unit)? = null,
    onReportUser: ((targetUserId: String, userName: String) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val extColors = LocalExtendedColors.current

    val identifier = remember(userId, username) {
        username?.takeIf { it.isNotBlank() } ?: userId?.takeIf { it.isNotBlank() } ?: ""
    }

    var isLoading by remember { mutableStateOf(true) }
    var userProfile by remember { mutableStateOf<PublicUserProfileDto?>(null) }
    var isFollowing by remember { mutableStateOf(false) }
    var isFollowLoading by remember { mutableStateOf(false) }

    LaunchedEffect(identifier) {
        isLoading = true
        userRepository.getUserPublicProfile(identifier).collect { res ->
            when (res) {
                is Resource.Success -> {
                    userProfile = res.data
                    isFollowing = res.data?.isFollowing == true
                    isLoading = false
                }
                is Resource.Error -> {
                    isLoading = false
                    Toast.makeText(context, res.message ?: "获取用户信息失败", Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 8.dp,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部小横条
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(extColors.border)
            )

            // 右上角举报按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = {
                        val name = userProfile?.nickname ?: userProfile?.username ?: "该用户"
                        val targetId = userProfile?.id?.takeIf { it.isNotBlank() } ?: userId ?: ""
                        onReportUser?.invoke(targetId, name)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Flag,
                        contentDescription = "举报",
                        tint = extColors.textTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 2.dp
                    )
                }
            } else {
                val profile = userProfile
                if (profile == null) {
                    Text(
                        text = "暂无该用户详细资料",
                        color = extColors.textSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 40.dp)
                    )
                } else {
                    val displayName = profile.nickname ?: profile.username ?: "居民"
                    // 头像
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .border(2.dp, extColors.glassBorder, CircleShape)
                    ) {
                        UserAvatar(
                            avatarUrl = profile.avatarUrl,
                            displayName = displayName,
                            size = 76.dp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 昵称
                    Text(
                        text = displayName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // 等级放在用户名和唯一ID中间
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .border(0.6.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Lv.${profile.level}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    profile.username?.let { u ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "@$u",
                            fontSize = 12.sp,
                            color = extColors.textTertiary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 简介/签名
                    Text(
                        text = if (!profile.bio.isNullOrBlank()) profile.bio else "这个人很神秘，什么都没写~",
                        fontSize = 13.sp,
                        color = extColors.textSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 统计方块：获赞 / 帖子 / 关注 / 粉丝
                    GlassBox(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ProfileStatItem(title = "经验", value = profile.exp.toString())
                            ProfileStatItem(title = "动态", value = profile.postCount.toString())
                            ProfileStatItem(title = "关注", value = profile.followingCount.toString())
                            ProfileStatItem(title = "粉丝", value = profile.followersCount.toString())
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 底部交互按钮：关注 / 私信
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 关注按钮
                        DeepButton(
                            text = if (isFollowing) "已关注" else "+ 关注",
                            onClick = {
                                if (isFollowLoading) return@DeepButton
                                isFollowLoading = true
                                scope.launch {
                                    val flow = if (isFollowing) {
                                        userRepository.unfollowUser(identifier)
                                    } else {
                                        userRepository.followUser(identifier)
                                    }
                                    flow.collect { res ->
                                        isFollowLoading = false
                                        when (res) {
                                            is Resource.Success -> {
                                                isFollowing = !isFollowing
                                                Toast.makeText(
                                                    context,
                                                    if (isFollowing) "已关注" else "已取消关注",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                            is Resource.Error -> {
                                                Toast.makeText(context, res.message ?: "操作失败", Toast.LENGTH_SHORT).show()
                                            }
                                            else -> {}
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            isPrimary = !isFollowing
                        )

                        // 发私信
                        if (onNavigateToChat != null) {
                            DeepButton(
                                text = "发私信",
                                onClick = {
                                    onDismissRequest()
                                    val targetId = userProfile?.id?.takeIf { it.isNotBlank() } ?: userId ?: ""
                                    onNavigateToChat(targetId, displayName)
                                },
                                modifier = Modifier.weight(1f),
                                isPrimary = isFollowing
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun ProfileStatItem(title: String, value: String) {
    val extColors = LocalExtendedColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = title,
            fontSize = 11.sp,
            color = extColors.textSecondary
        )
    }
}
