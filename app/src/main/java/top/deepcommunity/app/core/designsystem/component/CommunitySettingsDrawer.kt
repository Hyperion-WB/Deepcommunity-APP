package top.deepcommunity.app.core.designsystem.component

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.deepcommunity.app.core.common.AppThemeMode
import top.deepcommunity.app.core.common.ThemeManager
import top.deepcommunity.app.core.designsystem.theme.DeepTheme

@Composable
fun CommunitySettingsDrawer(
    onCloseDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentThemeMode by ThemeManager.themeMode.collectAsState()
    val isHapticEnabled by ThemeManager.hapticEnabled.collectAsState()
    var cacheSizeText by remember { mutableStateOf(getCacheSize(context)) }
    var cacheClearedToast by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(310.dp)
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // 顶部品牌区
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "深",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "深度极客社区",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "deepcommunity.top",
                    fontSize = 11.sp,
                    color = DeepTheme.extended.textTertiary
                )
            }
        }

        HorizontalDivider(color = DeepTheme.extended.border, thickness = 0.6.dp)

        Spacer(modifier = Modifier.height(20.dp))

        // 外观与主题设置
        Text(
            text = "外观与主题",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = DeepTheme.extended.textSecondary
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DeepTheme.extended.surfaceElevated)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ThemeOptionChip(
                title = "系统",
                icon = Icons.Outlined.BrightnessAuto,
                isSelected = currentThemeMode == AppThemeMode.FOLLOW_SYSTEM,
                modifier = Modifier.weight(1f),
                onClick = { ThemeManager.setThemeMode(context, AppThemeMode.FOLLOW_SYSTEM) }
            )
            ThemeOptionChip(
                title = "浅色",
                icon = Icons.Outlined.LightMode,
                isSelected = currentThemeMode == AppThemeMode.LIGHT,
                modifier = Modifier.weight(1f),
                onClick = { ThemeManager.setThemeMode(context, AppThemeMode.LIGHT) }
            )
            ThemeOptionChip(
                title = "深色",
                icon = Icons.Outlined.DarkMode,
                isSelected = currentThemeMode == AppThemeMode.DARK,
                modifier = Modifier.weight(1f),
                onClick = { ThemeManager.setThemeMode(context, AppThemeMode.DARK) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 触感反馈设置
        Text(
            text = "触感与交互",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = DeepTheme.extended.textSecondary
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DeepTheme.extended.surfaceElevated)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Vibration,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "物理机械微震反馈",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Switch(
                checked = isHapticEnabled,
                onCheckedChange = { ThemeManager.setHapticEnabled(context, it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 缓存管理
        Text(
            text = "存储与清理",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = DeepTheme.extended.textSecondary
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DeepTheme.extended.surfaceElevated)
                .clickable {
                    clearImageCache(context)
                    cacheSizeText = "0.0 MB"
                    cacheClearedToast = true
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.CleaningServices,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "清理图片与离线缓存",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "已占用 $cacheSizeText",
                        fontSize = 11.sp,
                        color = DeepTheme.extended.textTertiary
                    )
                }
            }
            Text(
                text = if (cacheClearedToast) "已清空" else "立即清理",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (cacheClearedToast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // 底部关于
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = DeepTheme.extended.textTertiary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "深度社区 Android v1.0.0 Pro",
                fontSize = 11.sp,
                color = DeepTheme.extended.textTertiary
            )
        }
    }
}

@Composable
private fun ThemeOptionChip(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "chip_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else DeepTheme.extended.textSecondary,
        label = "chip_color"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = contentColor,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = contentColor
        )
    }
}

private fun getCacheSize(context: Context): String {
    return try {
        val size = context.cacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        val mb = size / (1024.0 * 1024.0)
        String.format(java.util.Locale.getDefault(), "%.1f MB", mb)
    } catch (_: Exception) {
        "0.0 MB"
    }
}

private fun clearImageCache(context: Context) {
    try {
        context.cacheDir.deleteRecursively()
    } catch (_: Exception) {}
}
