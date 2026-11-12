package top.deepcommunity.app.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import top.deepcommunity.app.core.designsystem.theme.DeepMotion
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import top.deepcommunity.app.ui.profile.FollowListBottomSheet
import top.deepcommunity.app.ui.profile.PrivacyDndBottomSheet
import top.deepcommunity.app.ui.profile.UserProfileBottomSheet
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import top.deepcommunity.app.data.model.BlockedUserDto
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import top.deepcommunity.app.core.designsystem.component.DeepButton
import top.deepcommunity.app.core.designsystem.component.GlassBox
import top.deepcommunity.app.core.designsystem.component.UserAvatar
import top.deepcommunity.app.core.designsystem.theme.AccentOnline
import top.deepcommunity.app.core.designsystem.theme.DeepTheme
import top.deepcommunity.app.ui.home.PostCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onLogoutSuccess: () -> Unit,
    onPostClick: (String) -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val pagerState = rememberPagerState(initialPage = uiState.selectedTab) { 2 }
    var followListTab by remember { mutableStateOf<Int?>(null) }
    var showPrivacyDndSheet by remember { mutableStateOf(false) }
    var selectedOtherUserId by remember { mutableStateOf<Long?>(null) }

    // 左右滑动手势联动选中的 Tab
    LaunchedEffect(pagerState.currentPage) {
        viewModel.selectTab(pagerState.currentPage)
    }

    // 点击 Tab 时平滑滚动 Pager
    LaunchedEffect(uiState.selectedTab) {
        if (pagerState.currentPage != uiState.selectedTab) {
            pagerState.animateScrollToPage(uiState.selectedTab)
        }
    }

    LaunchedEffect(key1 = true) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is ProfileEvent.LogoutSuccess -> onLogoutSuccess()
                is ProfileEvent.ShowToast -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 100.dp, start = 16.dp, end = 16.dp, top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 顶部名片卡片
            item {
                val user = uiState.user
                GlassBox(
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth(),
                    elevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // 个人头像纯净展示（仅支持网页端修改，点击轻提示）
                            val avatarInteractionSource = remember { MutableInteractionSource() }
                            val isAvatarPressed by avatarInteractionSource.collectIsPressedAsState()
                            val avatarScale by animateFloatAsState(
                                targetValue = if (isAvatarPressed) 0.94f else 1f,
                                animationSpec = DeepMotion.bouncy(),
                                label = "avatar_press_scale"
                            )

                            Box(
                                modifier = Modifier
                                    .scale(avatarScale)
                                    .clip(CircleShape)
                                    .clickable(
                                        interactionSource = avatarInteractionSource,
                                        indication = null
                                    ) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("个人头像需前往社区网页端更换 (deepcommunity.top)")
                                        }
                                    }
                            ) {
                                UserAvatar(
                                    avatarUrl = user?.avatarUrl,
                                    displayName = user?.nickname ?: user?.username ?: "D",
                                    size = 64.dp
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = user?.nickname ?: user?.username ?: "社区居民",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "Lv.${user?.level ?: 1}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(3.dp))

                                Text(
                                    text = "@${user?.username ?: "user"}",
                                    fontSize = 12.sp,
                                    color = DeepTheme.extended.textTertiary
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                // 编辑资料入口
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { viewModel.setShowEditDialog(true) }
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "编辑资料",
                                        tint = DeepTheme.extended.textSecondary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "修改资料",
                                        fontSize = 11.sp,
                                        color = DeepTheme.extended.textSecondary
                                    )
                                }
                            }

                            // 每日签到按钮
                            val isChecked = uiState.isCheckedIn
                            val btnBg = if (!isChecked) MaterialTheme.colorScheme.primary else DeepTheme.extended.surfaceElevated
                            val btnTextColor = if (!isChecked) MaterialTheme.colorScheme.onPrimary else DeepTheme.extended.textTertiary

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(btnBg)
                                    .then(
                                        if (isChecked) Modifier.border(0.8.dp, DeepTheme.extended.border, RoundedCornerShape(12.dp))
                                        else Modifier
                                    )
                                    .clickable(
                                        enabled = !isChecked && !uiState.isCheckingIn,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.checkIn()
                                        }
                                    )
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                if (uiState.isCheckingIn) {
                                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp), color = btnTextColor)
                                } else {
                                    Text(
                                        text = if (isChecked) "✓ 已签到" else "每日签到",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = btnTextColor
                                    )
                                }
                            }
                        }

                        // 个人简介 (Bio)
                        val bio = user?.bio?.takeIf { it.isNotBlank() } ?: "这个人很神秘，还没有填写个人简介～"
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = bio,
                            fontSize = 13.sp,
                            color = DeepTheme.extended.textSecondary,
                            lineHeight = 18.sp
                        )

                        // 关注、粉丝与隐私设置数据统计栏
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(DeepTheme.extended.surfaceElevated.copy(alpha = 0.5f))
                                .border(0.8.dp, DeepTheme.extended.border, RoundedCornerShape(12.dp))
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { followListTab = 0 }
                                    .padding(horizontal = 14.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${user?.followingCount ?: 0}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "关注",
                                    fontSize = 11.sp,
                                    color = DeepTheme.extended.textSecondary
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(width = 1.dp, height = 22.dp)
                                    .background(DeepTheme.extended.border)
                            )

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { followListTab = 1 }
                                    .padding(horizontal = 14.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${user?.followersCount ?: 0}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "粉丝",
                                    fontSize = 11.sp,
                                    color = DeepTheme.extended.textSecondary
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(width = 1.dp, height = 22.dp)
                                    .background(DeepTheme.extended.border)
                            )

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showPrivacyDndSheet = true }
                                    .padding(horizontal = 14.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Security,
                                    contentDescription = "隐私设置",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "隐私与免打扰",
                                    fontSize = 11.sp,
                                    color = DeepTheme.extended.textSecondary
                                )
                            }
                        }

                        // 连续打卡与补签战绩横条
                        val checkin = uiState.checkinStatus
                        if (checkin != null) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(0.8.dp, DeepTheme.extended.border, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "⚡", fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = "连续打卡 ${checkin.streak} 天 (最高 ${checkin.bestStreak} 天)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "剩余补签机会: ${checkin.makeupLeft} 次",
                                            fontSize = 10.sp,
                                            color = DeepTheme.extended.textTertiary
                                        )
                                    }
                                }
                                if (checkin.makeupLeft > 0) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                            .clickable { viewModel.makeupCheckin() }
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text(text = "一键补签", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }

                        // 我的成就勋章墙
                        if (uiState.achievements.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Column {
                                Text(
                                    text = "成就勋章 (${uiState.achievements.size})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DeepTheme.extended.textSecondary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    uiState.achievements.forEach { ach ->
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(MaterialTheme.colorScheme.surface)
                                                .border(0.8.dp, DeepTheme.extended.border, RoundedCornerShape(10.dp))
                                                .padding(horizontal = 8.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = ach.icon, fontSize = 14.sp)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(text = ach.name, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Tab 切换条 [我的动态] [我的收藏]（毛玻璃风格）
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DeepTheme.extended.surfaceElevated)
                        .padding(4.dp)
                ) {
                    val tabs = listOf("我的动态", "我的收藏")
                    tabs.forEachIndexed { index, title ->
                        val isSelected = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                                .clickable {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else DeepTheme.extended.textTertiary
                            )
                        }
                    }
                }
            }

            // 支持直接左右滑屏切换的动态与收藏列表
            item {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(550.dp)
                ) { page ->
                    val list = if (page == 0) uiState.myPosts else uiState.myCollections
                    if (uiState.isLoadingList && list.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                        }
                    } else if (list.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (page == 0) "暂未发布任何动态" else "暂无收藏的帖子",
                                fontSize = 13.sp,
                                color = DeepTheme.extended.textTertiary
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 60.dp)
                        ) {
                            items(list, key = { it.id }) { post ->
                                PostCard(
                                    post = post,
                                    onClick = { onPostClick(post.id) },
                                    onLikeClick = {}
                                )
                            }
                        }
                    }
                }
            }

            // 黑名单与消息屏蔽管理
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DeepTheme.extended.surfaceElevated)
                        .clickable { viewModel.toggleBlocksSheet(true) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = null,
                            tint = DeepTheme.extended.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "黑名单与消息屏蔽",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "查看管理 ›",
                        fontSize = 12.sp,
                        color = DeepTheme.extended.textTertiary
                    )
                }
            }

            // 退出登录按钮
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DeepTheme.extended.surfaceElevated)
                        .clickable(onClick = viewModel::logout)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "退出登录",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // 黑名单抽屉
        if (uiState.showBlocksSheet) {
            val blocksSheetState = rememberModalBottomSheetState()
            ModalBottomSheet(
                onDismissRequest = { viewModel.toggleBlocksSheet(false) },
                sheetState = blocksSheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                BlockedUsersSheetContent(
                    blockedUsers = uiState.blockedUsers,
                    isLoading = uiState.isLoadingBlocks,
                    onUnblock = viewModel::unblockUser
                )
            }
        }

        // 修改资料 Dialog
        if (uiState.showEditDialog) {
            EditProfileDialog(
                currentNickname = uiState.user?.nickname ?: uiState.user?.username ?: "",
                currentBio = uiState.user?.bio ?: "",
                isUpdating = uiState.isUpdatingProfile,
                onDismiss = { viewModel.setShowEditDialog(false) },
                onConfirm = { nick, bio -> viewModel.updateProfile(nick, bio) }
            )
        }

        // 关注与粉丝列表抽屉
        followListTab?.let { tabIndex ->
            val myIdLong = uiState.user?.id?.toLongOrNull() ?: 0L
            FollowListBottomSheet(
                userId = myIdLong,
                initialTab = tabIndex,
                userRepository = viewModel.userRepo,
                onDismissRequest = { followListTab = null },
                onUserClick = { uId ->
                    followListTab = null
                    selectedOtherUserId = uId
                }
            )
        }

        // 隐私与夜间免打扰抽屉
        if (showPrivacyDndSheet) {
            PrivacyDndBottomSheet(
                userRepository = viewModel.userRepo,
                onDismissRequest = { showPrivacyDndSheet = false }
            )
        }

        // 他人个人主页弹窗
        selectedOtherUserId?.let { otherId ->
            UserProfileBottomSheet(
                userId = otherId,
                userRepository = viewModel.userRepo,
                onDismissRequest = { selectedOtherUserId = null }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileDialog(
    currentNickname: String,
    currentBio: String,
    isUpdating: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var nickname by remember { mutableStateOf(currentNickname) }
    var bio by remember { mutableStateOf(currentBio) }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, DeepTheme.extended.border, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Text(
                text = "修改个人资料",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "昵称",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = DeepTheme.extended.textSecondary
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = DeepTheme.extended.border
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "个人简介",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = DeepTheme.extended.textSecondary
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = DeepTheme.extended.border
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = !isUpdating, onClick = onDismiss)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "取消",
                        fontSize = 13.sp,
                        color = DeepTheme.extended.textTertiary
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(enabled = !isUpdating) { onConfirm(nickname, bio) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (isUpdating) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(
                            text = "保存",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockedUsersSheetContent(
    blockedUsers: List<BlockedUserDto>,
    isLoading: Boolean,
    onUnblock: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "黑名单管理",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${blockedUsers.size} 人",
                fontSize = 12.sp,
                color = DeepTheme.extended.textTertiary
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            }
        } else if (blockedUsers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无被屏蔽的用户",
                    fontSize = 13.sp,
                    color = DeepTheme.extended.textTertiary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(blockedUsers, key = { it.id }) { user ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DeepTheme.extended.surfaceElevated)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UserAvatar(
                                avatarUrl = user.finalAvatarUrl,
                                displayName = user.displayName,
                                size = 36.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = user.displayName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "@${user.username}",
                                    fontSize = 11.sp,
                                    color = DeepTheme.extended.textTertiary
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .border(0.8.dp, DeepTheme.extended.border, RoundedCornerShape(8.dp))
                                .clickable { onUnblock(user.username) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "解除屏蔽",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
