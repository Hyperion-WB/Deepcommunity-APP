package top.deepcommunity.app.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import top.deepcommunity.app.core.designsystem.theme.DeepTheme

/**
 * 液态极简玻璃质感确认弹窗
 * 全面替代 Android/Material3 原生白底粗角弹窗
 */
@Composable
fun DeepConfirmDialog(
    onDismissRequest: () -> Unit,
    title: String,
    message: String,
    confirmText: String = "确定",
    dismissText: String? = "取消",
    isDanger: Boolean = false,
    icon: ImageVector? = null,
    isLoading: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit = onDismissRequest
) {
    val dangerColor = Color(0xFFFF3B30)

    Dialog(
        onDismissRequest = {
            if (!isLoading) onDismissRequest()
        },
        properties = DialogProperties(
            dismissOnBackPress = !isLoading,
            dismissOnClickOutside = !isLoading,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassBox(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                backgroundColor = DeepTheme.extended.surfaceElevated,
                elevation = 20.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 顶部光圈指示图标
                    val displayIcon = icon ?: if (isDanger) Icons.Outlined.WarningAmber else Icons.Outlined.Info
                    val iconTint = if (isDanger) dangerColor else MaterialTheme.colorScheme.primary
                    val iconBg = if (isDanger) dangerColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)

                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(iconBg)
                            .border(1.dp, iconTint.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = displayIcon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // 标题
                    Text(
                        text = title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 描述内容
                    Text(
                        text = message,
                        fontSize = 13.sp,
                        color = DeepTheme.extended.textSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // 双按钮操作行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (dismissText != null) {
                            // 取消按钮
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(Color.Transparent)
                                    .border(1.dp, DeepTheme.extended.border, RoundedCornerShape(13.dp))
                                    .clickable(enabled = !isLoading, onClick = onDismiss),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = dismissText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = DeepTheme.extended.textSecondary
                                )
                            }
                        }

                        // 确认按钮
                        val confirmBg = if (isDanger) dangerColor else MaterialTheme.colorScheme.primary
                        val confirmTextColor = if (isDanger) Color.White else MaterialTheme.colorScheme.onPrimary

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(confirmBg)
                                .clickable(enabled = !isLoading, onClick = onConfirm),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    color = confirmTextColor,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Text(
                                    text = confirmText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = confirmTextColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 动作选项数据结构
 */
data class DialogActionItem(
    val title: String,
    val subtitle: String? = null,
    val icon: ImageVector? = null,
    val isDanger: Boolean = false,
    val onClick: () -> Unit
)

/**
 * 液态极简玻璃质感菜单动作选择弹窗
 * 适用于群管理菜单、快捷操作卡片等
 */
@Composable
fun DeepActionDialog(
    onDismissRequest: () -> Unit,
    title: String,
    subtitle: String? = null,
    actions: List<DialogActionItem>,
    cancelText: String = "取消"
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassBox(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                backgroundColor = DeepTheme.extended.surfaceElevated,
                elevation = 20.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    if (!subtitle.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = subtitle,
                            fontSize = 12.sp,
                            color = DeepTheme.extended.textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 动作项列表
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        actions.forEach { action ->
                            val dangerColor = Color(0xFFFF3B30)
                            val itemColor = if (action.isDanger) dangerColor else MaterialTheme.colorScheme.onSurface

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (action.isDanger) dangerColor.copy(alpha = 0.06f)
                                        else DeepTheme.extended.glassBackground
                                    )
                                    .border(
                                        1.dp,
                                        if (action.isDanger) dangerColor.copy(alpha = 0.2f) else DeepTheme.extended.border,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        onDismissRequest()
                                        action.onClick()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (action.icon != null) {
                                    Icon(
                                        imageVector = action.icon,
                                        contentDescription = null,
                                        tint = itemColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = action.title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = itemColor
                                    )
                                    if (!action.subtitle.isNullOrBlank()) {
                                        Text(
                                            text = action.subtitle,
                                            fontSize = 11.sp,
                                            color = DeepTheme.extended.textTertiary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 取消按钮
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Transparent)
                            .border(1.dp, DeepTheme.extended.border, RoundedCornerShape(12.dp))
                            .clickable(onClick = onDismissRequest),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = cancelText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = DeepTheme.extended.textSecondary
                        )
                    }
                }
            }
        }
    }
}
