package top.deepcommunity.app.ui.chat

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.deepcommunity.app.core.common.Resource
import top.deepcommunity.app.core.designsystem.component.DeepButton
import top.deepcommunity.app.core.designsystem.component.GlassBox
import top.deepcommunity.app.core.designsystem.theme.LocalExtendedColors
import top.deepcommunity.app.data.model.IdiomChainStateDto
import top.deepcommunity.app.data.repository.ChatRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdiomChainBottomSheet(
    roomId: String,
    chatRepository: ChatRepository,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val extColors = LocalExtendedColors.current

    var chainState by remember { mutableStateOf<IdiomChainStateDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var inputWord by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    fun loadState() {
        scope.launch {
            chatRepository.getIdiomChainState(roomId).collect { res ->
                isLoading = false
                if (res is Resource.Success) {
                    chainState = res.data
                }
            }
        }
    }

    LaunchedEffect(roomId) {
        loadState()
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 8.dp,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(extColors.border)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "🐉 成语接龙",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (chainState?.active == true) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                else extColors.surfaceElevated
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (chainState?.active == true) "进行中 · 第${chainState?.state?.round ?: 1}轮" else "未开始",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (chainState?.active == true) MaterialTheme.colorScheme.primary else extColors.textTertiary
                        )
                    }
                }

                if (chainState?.canManage == true && chainState?.active == true) {
                    Text(
                        text = "结束接龙",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                scope.launch {
                                    chatRepository.endIdiomChain(roomId).collect { res ->
                                        if (res is Resource.Success) {
                                            Toast.makeText(context, "接龙已结束", Toast.LENGTH_SHORT).show()
                                            loadState()
                                        }
                                    }
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                }
            } else {
                val state = chainState?.state
                if (chainState?.active == true && state != null) {
                    // 进行中的接龙卡片
                    GlassBox(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "请接以「${state.currentChar}」开头的四字成语",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // 历史词汇横向浏览
                            if (state.used.isNotEmpty()) {
                                Text(
                                    text = "本局已使用 ${state.used.size} 个成语：",
                                    fontSize = 11.sp,
                                    color = extColors.textTertiary,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(state.used.reversed()) { word ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(extColors.surfaceElevated)
                                                .border(0.8.dp, extColors.border, RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(text = word, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 接龙输入提交行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inputWord,
                            onValueChange = { inputWord = it.trim() },
                            placeholder = { Text("输入以「${state.currentChar}」开头的四字成语", fontSize = 13.sp, color = extColors.textTertiary) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = extColors.border
                            )
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = {
                                if (inputWord.isBlank() || isSubmitting) return@IconButton
                                isSubmitting = true
                                scope.launch {
                                    chatRepository.answerIdiomChain(roomId, inputWord).collect { res ->
                                        isSubmitting = false
                                        when (res) {
                                            is Resource.Success -> {
                                                val answerRes = res.data
                                                if (answerRes?.ok == true) {
                                                    Toast.makeText(context, "接得漂亮！已记录", Toast.LENGTH_SHORT).show()
                                                    inputWord = ""
                                                    loadState()
                                                } else {
                                                    Toast.makeText(context, answerRes?.reason ?: "接龙不合规则", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            is Resource.Error -> {
                                                Toast.makeText(context, res.message ?: "接龙失败", Toast.LENGTH_SHORT).show()
                                            }
                                            else -> {}
                                        }
                                    }
                                }
                            },
                            enabled = !isSubmitting && inputWord.isNotBlank()
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "发送接龙",
                                    tint = if (inputWord.isNotBlank()) MaterialTheme.colorScheme.primary else extColors.textTertiary
                                )
                            }
                        }
                    }
                } else {
                    // 未开始接龙
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 30.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "🐉", fontSize = 44.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "当前没有进行中的接龙游戏",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "点击下方按钮即可一键在群内开启成语对决",
                            fontSize = 12.sp,
                            color = extColors.textSecondary
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        DeepButton(
                            text = "立即开局",
                            onClick = {
                                scope.launch {
                                    chatRepository.startIdiomChain(roomId).collect { res ->
                                        if (res is Resource.Success) {
                                            Toast.makeText(context, "成语接龙已开局！", Toast.LENGTH_SHORT).show()
                                            loadState()
                                        } else if (res is Resource.Error) {
                                            Toast.makeText(context, res.message ?: "开局失败", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.width(160.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}
