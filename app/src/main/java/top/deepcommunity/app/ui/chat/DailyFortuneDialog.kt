package top.deepcommunity.app.ui.chat

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.deepcommunity.app.core.common.Resource
import top.deepcommunity.app.core.designsystem.component.DeepButton
import top.deepcommunity.app.core.designsystem.component.GlassBox
import top.deepcommunity.app.core.designsystem.theme.LocalExtendedColors
import top.deepcommunity.app.data.model.FortuneResultDto
import top.deepcommunity.app.data.model.FortuneStatusDto
import top.deepcommunity.app.data.repository.ChatRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyFortuneDialog(
    roomId: String,
    chatRepository: ChatRepository,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val extColors = LocalExtendedColors.current

    var fortuneStatus by remember { mutableStateOf<FortuneStatusDto?>(null) }
    var fortuneResult by remember { mutableStateOf<FortuneResultDto?>(null) }
    var isLoadingStatus by remember { mutableStateOf(true) }
    var isDrawing by remember { mutableStateOf(false) }

    LaunchedEffect(roomId) {
        chatRepository.getFortuneStatus(roomId).collect { res ->
            isLoadingStatus = false
            if (res is Resource.Success) {
                fortuneStatus = res.data
            }
        }
    }

    BasicAlertDialog(
        onDismissRequest = { if (!isDrawing) onDismissRequest() },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, extColors.glassBorder, RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🥠 每日求签",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(4.dp))

            val remaining = fortuneResult?.remaining ?: fortuneStatus?.remaining ?: 0
            Text(
                text = "今日剩余求签机会: $remaining 次",
                fontSize = 11.sp,
                color = extColors.textTertiary
            )

            Spacer(modifier = Modifier.height(16.dp))

            val result = fortuneResult
            if (result != null) {
                // 已抽签结果展示
                GlassBox(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = result.icon, fontSize = 36.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = result.label,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = result.grade,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "★".repeat(result.stars.coerceIn(1, 5)),
                            fontSize = 14.sp,
                            color = Color(0xFFFFB300)
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = result.wish,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        if (result.quote.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "「${result.quote}」",
                                fontSize = 12.sp,
                                color = extColors.textSecondary,
                                textAlign = TextAlign.Center
                            )
                        }

                        if (result.lucky.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF10B981).copy(alpha = 0.1f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = result.lucky,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF10B981)
                                )
                            }
                        }
                    }
                }
            } else {
                // 尚未抽签默认占位卡片
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(extColors.surfaceElevated),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDrawing || isLoadingStatus) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "🎋", fontSize = 42.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "诚心摇动灵签，测测今日社区运势吧",
                                fontSize = 12.sp,
                                color = extColors.textSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DeepButton(
                    text = "关闭",
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f),
                    isPrimary = false,
                    enabled = !isDrawing
                )

                DeepButton(
                    text = if (isDrawing) "摇签中..." else if (fortuneResult != null) "再求一签" else "摇签测运",
                    onClick = {
                        if (isDrawing) return@DeepButton
                        if (remaining <= 0) {
                            Toast.makeText(context, "今日求签次数已用完，明天再来哦~", Toast.LENGTH_SHORT).show()
                            return@DeepButton
                        }
                        isDrawing = true
                        scope.launch {
                            chatRepository.drawFortune(roomId).collect { res ->
                                isDrawing = false
                                when (res) {
                                    is Resource.Success -> {
                                        fortuneResult = res.data
                                    }
                                    is Resource.Error -> {
                                        Toast.makeText(context, res.message ?: "求签失败", Toast.LENGTH_SHORT).show()
                                    }
                                    else -> {}
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isDrawing && remaining > 0
                )
            }
        }
    }
}
