package top.deepcommunity.app.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import top.deepcommunity.app.core.designsystem.component.CommunitySettingsDrawer
import top.deepcommunity.app.core.designsystem.component.GlassFloatingNavigationBar
import top.deepcommunity.app.core.designsystem.component.MainTab
import top.deepcommunity.app.ui.chat.ConversationListScreen
import androidx.hilt.navigation.compose.hiltViewModel
import top.deepcommunity.app.ui.home.HomeScreen
import top.deepcommunity.app.ui.notification.NotificationScreen
import top.deepcommunity.app.ui.notification.NotificationViewModel
import top.deepcommunity.app.ui.profile.ProfileScreen

@Composable
fun MainScreen(
    onNavigateToPostDetail: (String) -> Unit,
    onNavigateToCreatePost: () -> Unit,
    onNavigateToChatDetail: (String) -> Unit,
    onLogoutSuccess: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var currentTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var isBarVisible by remember { mutableStateOf(true) }

    val notificationViewModel: NotificationViewModel = hiltViewModel()
    val notificationUiState by notificationViewModel.uiState.collectAsState()

    // 切换 Tab 时强制保证底部导航栏可见并即时刷新通知未读数据
    LaunchedEffect(currentTab) {
        isBarVisible = true
        if (currentTab == MainTab.NOTIFICATION) {
            notificationViewModel.loadNotifications(isRefresh = true)
        }
    }

    // 监听通知数量变化：当删除单条或清空通知后，无条件立即弹回底部导航栏，彻底修复消失卡死问题
    LaunchedEffect(notificationUiState.notifications.size) {
        isBarVisible = true
    }

    // Android 13+ 原生通知权限温和申请
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* 授权结果 */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // 智能手势滚动连接器：优化阈值与回弹机制，解决删除通知或列表变短时控件消失卡死的 Bug
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // 向下滑动超过 20f 且导航栏可见时隐退
                if (available.y < -20f && isBarVisible) {
                    isBarVisible = false
                } else if (available.y > 6f && !isBarVisible) {
                    // 轻微向上拉动即刻展出
                    isBarVisible = true
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // 到达边缘或列表没有更多可滚内容时，无条件弹出导航栏
                if (!isBarVisible && (available.y > 0f || consumed.y > 0f)) {
                    isBarVisible = true
                }
                return Offset.Zero
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            CommunitySettingsDrawer(
                onCloseDrawer = {
                    coroutineScope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .nestedScroll(nestedScrollConnection)
        ) {
            // 主内容区域切换，带有柔和弹性的淡入淡出过渡动效
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(120))
                },
                label = "tab_transition"
            ) { targetTab ->
                when (targetTab) {
                    MainTab.HOME -> HomeScreen(
                        onPostClick = onNavigateToPostDetail,
                        onOpenDrawer = {
                            coroutineScope.launch { drawerState.open() }
                        }
                    )
                    MainTab.CHAT -> ConversationListScreen(onConversationClick = onNavigateToChatDetail)
                    MainTab.CREATE -> {} // 浮动发帖按钮
                    MainTab.NOTIFICATION -> NotificationScreen(
                        viewModel = notificationViewModel,
                        onNotificationClick = { notif ->
                            val targetId = notif.targetId
                            if (!targetId.isNullOrBlank()) {
                                when (notif.actionType) {
                                    "group_apply", "group_apply_approved", "group_apply_rejected",
                                    "group_invite", "group_owner_transferred", "chat_message",
                                    "group_room" -> {
                                        onNavigateToChatDetail(targetId)
                                    }
                                    else -> {
                                        onNavigateToPostDetail(targetId)
                                    }
                                }
                            }
                        }
                    )
                    MainTab.PROFILE -> ProfileScreen(
                        onLogoutSuccess = onLogoutSuccess,
                        onPostClick = onNavigateToPostDetail
                    )
                }
            }

            // 苹果灵动岛 (Dynamic Island) 风格液态玻璃浮动状态栏（严格 5 项，加号正居中）
            GlassFloatingNavigationBar(
                currentTab = currentTab,
                isVisible = isBarVisible,
                unreadNotificationCount = notificationUiState.unreadCount,
                onTabSelected = { tab ->
                    if (tab != MainTab.CREATE) {
                        currentTab = tab
                    }
                    isBarVisible = true
                },
                onCreateClick = onNavigateToCreatePost,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
