package top.deepcommunity.app.core.designsystem.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.deepcommunity.app.core.designsystem.theme.AccentError
import top.deepcommunity.app.core.designsystem.theme.DeepMotion
import top.deepcommunity.app.core.designsystem.theme.DeepTheme
import kotlin.math.roundToInt

enum class MainTab {
    HOME, CHAT, CREATE, NOTIFICATION, PROFILE
}

/**
 * 苹果灵动岛 (Dynamic Island) 风格液态玻璃悬浮导航栏
 * 严格 5 项对称布局：[首页] [聊天会话] 【+ 加号居中】 [通知提醒] [我的]
 * 图标上下左右绝对居中，选中时直接变纯黑/纯白变色，支持手势滑动收起与展开
 */
@Composable
fun GlassFloatingNavigationBar(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
    unreadMessageCount: Int = 0,
    unreadNotificationCount: Int = 0,
    onCreateClick: () -> Unit = {}
) {
    // 隐藏/显示 物理弹簧位移
    val offsetY by animateDpAsState(
        targetValue = if (isVisible) 0.dp else 120.dp,
        animationSpec = spring(
            dampingRatio = 0.76f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "island_offset_y"
    )

    // 动态栏展开缩放动效（出现时弹性舒展微扩容）
    val islandScale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.85f,
        animationSpec = spring(
            dampingRatio = 0.68f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "island_scale"
    )

    val islandAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "island_alpha"
    )

    Box(
        modifier = modifier
            .offset { IntOffset(x = 0, y = offsetY.toPx().roundToInt()) }
            .scale(islandScale)
            .alpha(islandAlpha)
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassBox(
            shape = RoundedCornerShape(36.dp),
            elevation = 12.dp,
            blurRadius = 24.dp,
            modifier = Modifier.height(64.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. 首页
                NavItem(
                    selected = currentTab == MainTab.HOME,
                    outlineIcon = Icons.Outlined.Home,
                    filledIcon = Icons.Filled.Home,
                    contentDescription = "首页",
                    onClick = { onTabSelected(MainTab.HOME) }
                )

                // 2. 聊天会话（私聊与群聊大厅）
                NavItem(
                    selected = currentTab == MainTab.CHAT,
                    outlineIcon = Icons.Outlined.ChatBubbleOutline,
                    filledIcon = Icons.Filled.ChatBubble,
                    badgeCount = unreadMessageCount,
                    contentDescription = "会话",
                    onClick = { onTabSelected(MainTab.CHAT) }
                )

                // 3. 【正中间】苹果质感悬浮加号发帖键
                CenterCreateButton(onClick = onCreateClick)

                // 4. 通知提醒
                NavItem(
                    selected = currentTab == MainTab.NOTIFICATION,
                    outlineIcon = Icons.Outlined.Notifications,
                    filledIcon = Icons.Filled.Notifications,
                    badgeCount = unreadNotificationCount,
                    contentDescription = "提醒",
                    onClick = { onTabSelected(MainTab.NOTIFICATION) }
                )

                // 5. 我的主页
                NavItem(
                    selected = currentTab == MainTab.PROFILE,
                    outlineIcon = Icons.Outlined.Person,
                    filledIcon = Icons.Filled.Person,
                    contentDescription = "我的",
                    onClick = { onTabSelected(MainTab.PROFILE) }
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    selected: Boolean,
    outlineIcon: ImageVector,
    filledIcon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    badgeCount: Int = 0
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.82f
            selected -> 1.12f
            else -> 1f
        },
        animationSpec = DeepMotion.bouncy(),
        label = "nav_item_scale"
    )

    val iconColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        DeepTheme.extended.textTertiary
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .scale(scale)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        BadgedBox(
            badge = {
                if (badgeCount > 0) {
                    Badge(
                        containerColor = AccentError,
                        contentColor = Color.White
                    ) {
                        Text(
                            text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                            fontSize = 9.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }
                }
            }
        ) {
            Icon(
                imageVector = if (selected) filledIcon else outlineIcon,
                contentDescription = contentDescription,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun CenterCreateButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = DeepMotion.bouncy(),
        label = "create_btn_scale"
    )

    Box(
        modifier = modifier
            .size(48.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "发布动态",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(26.dp)
        )
    }
}
