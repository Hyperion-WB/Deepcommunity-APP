package top.deepcommunity.app.ui.post.component

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.deepcommunity.app.core.designsystem.component.UserAvatar
import top.deepcommunity.app.data.model.PostItemDto

@Composable
fun PostSharePosterDialog(
    post: PostItemDto?,
    onDismissRequest: () -> Unit
) {
    if (post == null) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isGenerating by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .statusBarsPadding()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 顶部关闭栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onDismissRequest) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 极客海报预览卡片 (Apple 液态玻璃黑白极简风格)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF1E1E1E),
                                    Color(0xFF121212)
                                )
                            )
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                        .padding(20.dp)
                ) {
                    Column {
                        // 1. 品牌 Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "DEEP COMMUNITY",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "深度社区 · 极客共识与日常分享",
                                    fontSize = 9.sp,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }

                            // 极客微徽标
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "C",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.Black
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 分割细线
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.8.dp)
                                .background(Color.White.copy(alpha = 0.1f))
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // 2. 作者信息
                        val authorName = post.authorNickname ?: post.authorUsername ?: post.author?.nickname ?: "居民"
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UserAvatar(
                                avatarUrl = post.finalAvatarUrl,
                                displayName = authorName,
                                size = 38.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = authorName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color.White.copy(alpha = 0.12f))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "Lv.${post.authorLevel}",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                                Text(
                                    text = post.createdAt.take(16).replace("T", " "),
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.4f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 3. 帖子大标题
                        Text(
                            text = post.title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            lineHeight = 24.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // 4. 精选正文摘录
                        val rawContent = post.contentMd ?: post.contentHtml ?: ""
                        val snippet = remember(rawContent) {
                            rawContent.replace(Regex("<[^>]*>"), "")
                                .replace(Regex("#+\\s*"), "")
                                .replace(Regex("`+"), "")
                                .replace(Regex("\\[.*?\\]\\(.*?\\)"), "")
                                .replace(Regex("[*_-]"), "")
                                .trim()
                                .take(220)
                        }

                        Text(
                            text = snippet.ifBlank { "（分享自深度社区）" },
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.75f),
                            lineHeight = 20.sp,
                            maxLines = 8,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // 5. 底部终端风扫码与水印
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "deepcommunity.top",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White
                                )
                                Text(
                                    text = "扫码加入极客圈，参与热烈讨论",
                                    fontSize = 9.sp,
                                    color = Color.White.copy(alpha = 0.45f)
                                )
                            }

                            // 极客黑白条码视觉装饰
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val barHeights = listOf(14, 20, 10, 22, 16, 24, 12, 18, 22, 14, 20, 16)
                                barHeights.forEach { h ->
                                    Box(
                                        modifier = Modifier
                                            .width(2.dp)
                                            .height(h.dp)
                                            .background(Color.White.copy(alpha = 0.7f))
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 操作按钮栏：保存海报 / 系统分享
                Row(
                    modifier = Modifier.fillMaxWidth(0.92f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 保存到相册
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .clickable(enabled = !isGenerating) {
                                isGenerating = true
                                scope.launch {
                                    val bitmap = renderPosterBitmap(context, post)
                                    val success = saveBitmapToGallery(context, bitmap, post.title)
                                    isGenerating = false
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                            context,
                                            if (success) "已保存极客高清海报到系统相册！" else "保存失败，请检查存储权限",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp), color = Color.Black)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.FileDownload,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "保存至相册",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }
                        }
                    }

                    // 系统原生分享
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                            .clickable(enabled = !isGenerating) {
                                isGenerating = true
                                scope.launch {
                                    val bitmap = renderPosterBitmap(context, post)
                                    sharePosterBitmap(context, bitmap, post.title)
                                    isGenerating = false
                                }
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "系统分享",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

/**
 * 原生离屏绘制超高清 1080P 黑白极客分享海报
 */
private suspend fun renderPosterBitmap(context: Context, post: PostItemDto): Bitmap = withContext(Dispatchers.Default) {
    val width = 1080
    val height = 1680
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // 1. 渐变底色
    val bgPaint = Paint().apply {
        isAntiAlias = true
        color = AndroidColor.parseColor("#121212")
    }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

    // 2. 内层高质感卡片
    val cardPaint = Paint().apply {
        isAntiAlias = true
        color = AndroidColor.parseColor("#1B1B1B")
    }
    val cardRect = RectF(60f, 60f, width - 60f, height - 60f)
    canvas.drawRoundRect(cardRect, 48f, 48f, cardPaint)

    // 边框
    val strokePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = AndroidColor.parseColor("#2E2E2E")
    }
    canvas.drawRoundRect(cardRect, 48f, 48f, strokePaint)

    // 3. 绘制 Header
    val logoTextPaint = TextPaint().apply {
        isAntiAlias = true
        color = AndroidColor.WHITE
        textSize = 38f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        letterSpacing = 0.08f
    }
    canvas.drawText("DEEP COMMUNITY", 110f, 150f, logoTextPaint)

    val subTextPaint = TextPaint().apply {
        isAntiAlias = true
        color = AndroidColor.parseColor("#8E8E93")
        textSize = 26f
    }
    canvas.drawText("深度社区 · 极客共识与日常分享", 110f, 195f, subTextPaint)

    // 绘制 C 标微章
    val circlePaint = Paint().apply {
        isAntiAlias = true
        color = AndroidColor.WHITE
    }
    canvas.drawCircle(width - 150f, 165f, 36f, circlePaint)
    val cPaint = TextPaint().apply {
        isAntiAlias = true
        color = AndroidColor.BLACK
        textSize = 42f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText("C", width - 150f, 180f, cPaint)

    // 分割线
    val linePaint = Paint().apply {
        isAntiAlias = true
        strokeWidth = 2f
        color = AndroidColor.parseColor("#2C2C2E")
    }
    canvas.drawLine(110f, 240f, width - 110f, 240f, linePaint)

    // 4. 作者信息
    val authorName = post.authorNickname ?: post.authorUsername ?: post.author?.nickname ?: "社区居民"
    val authorPaint = TextPaint().apply {
        isAntiAlias = true
        color = AndroidColor.WHITE
        textSize = 34f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    canvas.drawText("@$authorName", 110f, 310f, authorPaint)

    val datePaint = TextPaint().apply {
        isAntiAlias = true
        color = AndroidColor.parseColor("#636366")
        textSize = 24f
    }
    canvas.drawText(post.createdAt.take(16).replace("T", " "), 110f, 350f, datePaint)

    // 5. 标题（支持自动折行）
    val titlePaint = TextPaint().apply {
        isAntiAlias = true
        color = AndroidColor.WHITE
        textSize = 52f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val contentWidth = width - 220
    val titleLayout = StaticLayout.Builder.obtain(post.title, 0, post.title.length, titlePaint, contentWidth)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setMaxLines(3)
        .build()

    canvas.save()
    canvas.translate(110f, 410f)
    titleLayout.draw(canvas)
    canvas.restore()

    // 6. 精选正文
    val rawText = (post.contentMd ?: post.contentHtml ?: "")
        .replace(Regex("<[^>]*>"), "")
        .replace(Regex("#+\\s*"), "")
        .replace(Regex("`+"), "")
        .replace(Regex("\\[.*?\\]\\(.*?\\)"), "")
        .replace(Regex("[*_-]"), "")
        .trim()
        .take(300)

    val bodyPaint = TextPaint().apply {
        isAntiAlias = true
        color = AndroidColor.parseColor("#C7C7CC")
        textSize = 34f
    }
    val bodyLayout = StaticLayout.Builder.obtain(rawText, 0, rawText.length, bodyPaint, contentWidth)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(8f, 1.25f)
        .setMaxLines(10)
        .build()

    val bodyTop = 410f + titleLayout.height + 40f
    canvas.save()
    canvas.translate(110f, bodyTop)
    bodyLayout.draw(canvas)
    canvas.restore()

    // 7. 底部终端风条码与链接
    val bottomBoxPaint = Paint().apply {
        isAntiAlias = true
        color = AndroidColor.parseColor("#151515")
    }
    val bottomBoxRect = RectF(110f, height - 240f, width - 110f, height - 120f)
    canvas.drawRoundRect(bottomBoxRect, 20f, 20f, bottomBoxPaint)
    canvas.drawRoundRect(bottomBoxRect, 20f, 20f, strokePaint)

    val urlPaint = TextPaint().apply {
        isAntiAlias = true
        color = AndroidColor.WHITE
        textSize = 30f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    canvas.drawText("https://deepcommunity.top", 140f, height - 185f, urlPaint)

    val hintPaint = TextPaint().apply {
        isAntiAlias = true
        color = AndroidColor.parseColor("#8E8E93")
        textSize = 22f
    }
    canvas.drawText("扫码或访问深度社区 · 与极客同行", 140f, height - 145f, hintPaint)

    // 绘制条形码小装饰
    val barPaint = Paint().apply {
        isAntiAlias = true
        color = AndroidColor.parseColor("#E5E5EA")
    }
    var barX = width - 360f
    val barHeights = listOf(30, 48, 24, 52, 36, 56, 28, 44, 50, 32, 48, 38, 54, 26, 46)
    barHeights.forEach { bh ->
        canvas.drawRect(barX, height - 180f - (bh / 2f), barX + 5f, height - 180f + (bh / 2f), barPaint)
        barX += 12f
    }

    bitmap
}

/**
 * 保存位图到 MediaStore 相册
 */
private suspend fun saveBitmapToGallery(context: Context, bitmap: Bitmap, title: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val filename = "DeepCommunity_Poster_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/DeepCommunity")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false

        resolver.openOutputStream(uri)?.use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }

        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

/**
 * 保存临时图片并调起 Android 系统原生分享
 */
private suspend fun sharePosterBitmap(context: Context, bitmap: Bitmap, title: String) = withContext(Dispatchers.IO) {
    try {
        val filename = "Share_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/DeepCommunity/Share")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext

        resolver.openOutputStream(uri)?.use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }

        withContext(Dispatchers.Main) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "【极客分享】${title} · 来自深度社区")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "分享极客动态海报"))
        }
    } catch (e: Exception) {
        e.printStackTrace()
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "调起系统分享失败", Toast.LENGTH_SHORT).show()
        }
    }
}
