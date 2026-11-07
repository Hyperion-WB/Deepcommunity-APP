package top.deepcommunity.app.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import top.deepcommunity.app.core.common.Constants
import top.deepcommunity.app.core.designsystem.theme.DeepTheme

/**
 * 社区高质感统一头像组件
 * 自动处理绝对/相对 URL 拼接、加载中骨架、加载失败降级为首字母极简头像
 */
@Composable
fun UserAvatar(
    avatarUrl: String?,
    displayName: String?,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    borderWidth: Dp = 0.5.dp
) {
    val fullUrl = rememberFullAvatarUrl(avatarUrl)
    val initial = (displayName?.trim()?.firstOrNull() ?: 'D').uppercaseChar().toString()

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(borderWidth, DeepTheme.extended.border, CircleShape)
            .background(DeepTheme.extended.surfaceElevated),
        contentAlignment = Alignment.Center
    ) {
        if (!fullUrl.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(fullUrl)
                    .crossfade(300)
                    .build(),
                contentDescription = displayName,
                modifier = Modifier.size(size).clip(CircleShape),
                contentScale = ContentScale.Crop,
                loading = {
                    DefaultFallbackAvatar(initial = initial, size = size)
                },
                error = {
                    DefaultFallbackAvatar(initial = initial, size = size)
                }
            )
        } else {
            DefaultFallbackAvatar(initial = initial, size = size)
        }
    }
}

@Composable
private fun DefaultFallbackAvatar(initial: String, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontSize = (size.value * 0.44f).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun rememberFullAvatarUrl(url: String?): String? {
    if (url.isNullOrBlank()) return null
    return if (url.startsWith("http://") || url.startsWith("https://")) {
        url
    } else {
        Constants.BASE_URL.trimEnd('/') + "/" + url.trimStart('/')
    }
}
