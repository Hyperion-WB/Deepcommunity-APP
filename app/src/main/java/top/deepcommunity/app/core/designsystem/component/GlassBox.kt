package top.deepcommunity.app.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.deepcommunity.app.core.designsystem.theme.DeepTheme

/**
 * 具有极致液态玻璃质感的磨砂微光容器组件
 * 采用 Apple iOS / Nothing 级别的高光反射双重渐变边缘与环境弥散投影
 */
@Composable
fun GlassBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    backgroundColor: Color = DeepTheme.extended.glassBackground,
    borderColor: Color? = null,
    borderWidth: Dp = 1.dp,
    elevation: Dp = 12.dp,
    blurRadius: Dp = 20.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f

    // 液态微光高斯渐变边缘：顶部高光折射，侧底柔和过渡
    val specularBorderBrush = Brush.verticalGradient(
        colors = if (isDark) {
            listOf(
                Color.White.copy(alpha = 0.38f),
                Color.White.copy(alpha = 0.16f),
                Color.White.copy(alpha = 0.08f)
            )
        } else {
            listOf(
                Color.Black.copy(alpha = 0.22f),
                Color.Black.copy(alpha = 0.12f),
                Color.Black.copy(alpha = 0.05f)
            )
        }
    )

    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = if (isDark) Color.Black.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.12f),
                spotColor = if (isDark) Color.Black.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.16f)
            )
            .clip(shape)
            .background(backgroundColor)
            .then(
                if (borderColor != null) {
                    Modifier.border(borderWidth, borderColor, shape)
                } else {
                    Modifier.border(borderWidth, specularBorderBrush, shape)
                }
            ),
        content = content
    )
}
