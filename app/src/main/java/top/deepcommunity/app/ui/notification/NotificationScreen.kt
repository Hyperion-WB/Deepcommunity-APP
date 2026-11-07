package top.deepcommunity.app.ui.notification

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import top.deepcommunity.app.core.designsystem.theme.AccentError
import top.deepcommunity.app.core.designsystem.theme.AccentLike
import top.deepcommunity.app.core.designsystem.theme.AccentLink
import top.deepcommunity.app.core.designsystem.theme.DeepMotion
import top.deepcommunity.app.core.designsystem.theme.DeepTheme
import top.deepcommunity.app.data.model.NotificationDto
import kotlin.math.roundToInt

@Composable
fun NotificationScreen(
    onNotificationClick: (String?) -> Unit = {},
    viewModel: NotificationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showMarkAllReadDialog by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    // 每次进入通知页面主动同步最新状态
    LaunchedEffect(Unit) {
        viewModel.loadNotifications(isRefresh = true)
    }

    // 全部已读确认弹窗
    if (showMarkAllReadDialog) {
        AlertDialog(
            onDismissRequest = { showMarkAllReadDialog = false },
            title = { Text("全部已读", fontWeight = FontWeight.Bold) },
            text = { Text("是否确定将所有未读消息标记为已读？", fontSize = 14.sp) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showMarkAllReadDialog = false
                        viewModel.markAllAsRead()
                    }
                ) {
                    Text("确定全部已读", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMarkAllReadDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 全部清空确认弹窗
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("清空提醒", fontWeight = FontWeight.Bold) },
            text = { Text("是否确定清空全部消息提醒？此操作不可恢复。", fontSize = 14.sp) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearAllDialog = false
                        viewModel.clearAllNotifications()
                    }
                ) {
                    Text("确定清空", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶部操作栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "消息提醒",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (uiState.notifications.any { !it.isRead }) {
                    Text(
                        text = "全部已读",
                        fontSize = 12.sp,
                        color = AccentLink,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { showMarkAllReadDialog = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                if (uiState.notifications.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "清空全部",
                        fontSize = 12.sp,
                        color = DeepTheme.extended.textTertiary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { showClearAllDialog = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        if (uiState.isLoading && uiState.notifications.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
            }
        } else if (uiState.notifications.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "暂无未读消息提醒",
                    color = DeepTheme.extended.textTertiary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp, start = 16.dp, end = 16.dp, top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(uiState.notifications, key = { it.id }) { notif ->
                    SwipeableNotificationCard(
                        notification = notif,
                        onClick = {
                            if (!notif.isRead) {
                                viewModel.markSingleAsRead(notif.id)
                            }
                            onNotificationClick(notif.targetId)
                        },
                        onMarkAsRead = { viewModel.markSingleAsRead(notif.id) },
                        onDelete = { viewModel.deleteNotification(notif.id) }
                    )
                }
            }
        }
    }
}

/**
 * 消息提醒智能双向滑动手势卡片：
 * 1. 往左滑 (←)：露出红色【删除】操作区，深滑直接触发删除
 * 2. 往右滑 (→)：露出绿色【已读】操作区，划开松手即刻标为已读并弹簧归位
 * 3. 点开卡片：自动标记为已读，并进入对应内容详情
 */
@Composable
private fun SwipeableNotificationCard(
    notification: NotificationDto,
    onClick: () -> Unit,
    onMarkAsRead: () -> Unit,
    onDelete: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
    ) {
        // 底层左右双向操作背景层
        Box(
            modifier = Modifier.matchParentSize(),
            contentAlignment = Alignment.Center
        ) {
            // 右滑 (→) 时露出的【已读】底色区（位于卡片左侧）
            if (offsetX.value > 10f) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF10B981).copy(alpha = 0.92f))
                        .padding(start = 20.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "标为已读",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (notification.isRead) "已读" else "标为已读",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 左滑 (←) 时露出的【删除】底色区（位于卡片右侧）
            if (offsetX.value < -10f) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.92f))
                        .padding(end = 20.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "删除",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除通知",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 上层通知卡片本体（支持流畅阻尼与手势分流）
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(notification.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            coroutineScope.launch {
                                val current = offsetX.value
                                when {
                                    // 1. 往左深划（超过 -260f）：直接删除
                                    current < -260f -> {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        offsetX.animateTo(-1200f)
                                        onDelete()
                                    }
                                    // 2. 往左轻划（超过 -100f）：吸附露出删除按钮，点击可删
                                    current < -100f -> {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        offsetX.animateTo(-160f)
                                    }
                                    // 3. 往右滑（超过 120f）：触发已读并弹簧归位
                                    current > 120f -> {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        if (!notification.isRead) {
                                            onMarkAsRead()
                                        }
                                        offsetX.animateTo(0f)
                                    }
                                    // 4. 其余轻微偏移：弹簧回弹归位
                                    else -> {
                                        offsetX.animateTo(0f)
                                    }
                                }
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            coroutineScope.launch {
                                // 限制左滑最大可滑出屏幕，右滑最大拉出 260f 弹性阻尼
                                val newOffset = (offsetX.value + dragAmount).coerceIn(-1200f, 260f)
                                offsetX.snapTo(newOffset)
                            }
                        }
                    )
                }
        ) {
            NotificationCard(
                notification = notification,
                onClick = {
                    if (kotlin.math.abs(offsetX.value) > 20f) {
                        coroutineScope.launch { offsetX.animateTo(0f) }
                    } else {
                        onClick()
                    }
                }
            )
        }
    }
}

@Composable
private fun NotificationCard(
    notification: NotificationDto,
    onClick: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = DeepMotion.bouncy(),
        label = "notif_card_scale"
    )

    Row(
        modifier = Modifier
            .scale(cardScale)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 类型图标微标
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    when (notification.actionType) {
                        "like" -> AccentLike.copy(alpha = 0.12f)
                        "comment" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else -> DeepTheme.extended.surfaceElevated
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (notification.actionType) {
                    "like" -> Icons.Filled.Favorite
                    "comment" -> Icons.AutoMirrored.Filled.Comment
                    else -> Icons.Filled.Notifications
                },
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = when (notification.actionType) {
                    "like" -> AccentLike
                    "comment" -> MaterialTheme.colorScheme.primary
                    else -> DeepTheme.extended.textSecondary
                }
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = notification.brief,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = if (!notification.isRead) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = notification.createdAt.take(16).replace("T", " "),
                fontSize = 10.sp,
                color = DeepTheme.extended.textTertiary
            )
        }

        // 未读蓝点
        if (!notification.isRead) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(AccentLink)
            )
        }
    }
}
