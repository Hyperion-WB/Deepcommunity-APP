package top.deepcommunity.app.core.designsystem.component

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.deepcommunity.app.core.common.Constants
import java.io.OutputStream
import kotlin.math.roundToInt

/**
 * 行业顶级沉浸式图片浏览器组件
 * 支持双指捏合缩放、双击 2.5x 放大、单指下拉拖拽阻尼退出、长按/一键保存到本地系统相册
 */
@Composable
fun ImageViewerDialog(
    imageUrl: String?,
    onDismissRequest: () -> Unit
) {
    if (imageUrl.isNullOrBlank()) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val fullUrl = if (imageUrl.startsWith("http")) imageUrl else Constants.BASE_URL.trimEnd('/') + "/" + imageUrl.trimStart('/')

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val dismissOffsetY = remember { Animatable(0f) }
    var isSaving by remember { mutableStateOf(false) }

    // 背景黑色透明度随下拉拖拽实时渐变
    val bgAlpha by animateFloatAsState(
        targetValue = (1f - (dismissOffsetY.value / 600f).coerceIn(0f, 0.7f)),
        label = "bg_alpha"
    )

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = bgAlpha))
                .pointerInput(Unit) {
                    // 双击放大/还原与长按保存手势
                    detectTapGestures(
                        onDoubleTap = {
                            scale = if (scale > 1.2f) 1f else 2.5f
                            offset = Offset.Zero
                        },
                        onLongPress = {
                            coroutineScope.launch {
                                saveImageToGallery(context, fullUrl) { isSaving = it }
                            }
                        },
                        onTap = {
                            if (scale == 1f) onDismissRequest()
                        }
                    )
                }
                .pointerInput(scale) {
                    // 双指缩放与平移手势
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 4.5f)
                        scale = newScale
                        if (newScale > 1f) {
                            val maxOffset = 500f * (newScale - 1f)
                            offset = Offset(
                                x = (offset.x + pan.x).coerceIn(-maxOffset, maxOffset),
                                y = (offset.y + pan.y).coerceIn(-maxOffset, maxOffset)
                            )
                        } else {
                            // 未放大时单指下拉触发阻尼退出
                            coroutineScope.launch {
                                val newY = (dismissOffsetY.value + pan.y).coerceAtLeast(0f)
                                dismissOffsetY.snapTo(newY)
                                if (pan.y < 0 && dismissOffsetY.value > 0) {
                                    dismissOffsetY.animateTo(0f)
                                }
                            }
                        }
                    }
                }
                .pointerInput(scale) {
                    // 单指下拉松手时判定是否关闭
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.all { !it.pressed }) {
                                if (dismissOffsetY.value > 160f && scale == 1f) {
                                    coroutineScope.launch {
                                        dismissOffsetY.animateTo(1000f, tween(150))
                                        onDismissRequest()
                                    }
                                } else {
                                    coroutineScope.launch {
                                        dismissOffsetY.animateTo(0f, tween(180))
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            // 图片本体
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, dismissOffsetY.value.roundToInt()) }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = fullUrl,
                    contentDescription = "查看大图",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 顶部液态毛玻璃浮动操作条
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 关闭按钮
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = onDismissRequest) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // 保存到本地相册按钮
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp), color = Color.White)
                    } else {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    saveImageToGallery(context, fullUrl) { isSaving = it }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FileDownload,
                                contentDescription = "保存到本地相册",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 将网络图片保存至 Android 手机系统相册
 */
private suspend fun saveImageToGallery(
    context: Context,
    imageUrl: String,
    onProgress: (Boolean) -> Unit
) {
    onProgress(true)
    try {
        withContext(Dispatchers.IO) {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .allowHardware(false)
                .build()
            val result = (loader.execute(request) as? SuccessResult)?.drawable
            val bitmap = (result as? BitmapDrawable)?.bitmap

            if (bitmap != null) {
                val filename = "Deep_${System.currentTimeMillis()}.jpg"
                var fos: OutputStream? = null

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/DeepCommunity")
                    }
                    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        fos = context.contentResolver.openOutputStream(uri)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.insertImage(context.contentResolver, bitmap, filename, "深度社区保存图片")
                }

                fos?.use {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "✓ 图片已成功保存至手机相册", Toast.LENGTH_SHORT).show()
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "保存失败：无法解析图片", Toast.LENGTH_SHORT).show()
                }
            }
        }
    } catch (_: Exception) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "保存失败：写入异常", Toast.LENGTH_SHORT).show()
        }
    } finally {
        onProgress(false)
    }
}
