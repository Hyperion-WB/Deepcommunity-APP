package top.deepcommunity.app.ui.chat

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import top.deepcommunity.app.core.designsystem.theme.DeepTheme
import top.deepcommunity.app.data.model.GroupPollDto
import top.deepcommunity.app.data.repository.ChatRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupPollBottomSheet(
    roomId: String,
    chatRepository: ChatRepository,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var polls by remember { mutableStateOf<List<GroupPollDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showCreateDialog by remember { mutableStateOf(false) }

    // 勾选选项集合 Map<pollId, Set<optionId>>
    val selectedOptionsMap = remember { mutableStateMapOf<String, Set<Int>>() }
    var isSubmittingVoteId by remember { mutableStateOf<String?>(null) }

    // 新建投票表单状态
    var newTitle by remember { mutableStateOf("") }
    var newOptions by remember { mutableStateOf(listOf("", "")) }
    var newIsMultiple by remember { mutableStateOf(false) }
    var isCreating by remember { mutableStateOf(false) }

    fun loadPolls() {
        scope.launch {
            isLoading = true
            chatRepository.getGroupPolls(roomId).collect { res ->
                isLoading = false
                if (res is Resource.Success) {
                    polls = res.data
                }
            }
        }
    }

    LaunchedEffect(roomId) {
        loadPolls()
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
                .heightIn(max = 680.dp)
        ) {
            // 顶部拖动手柄
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(DeepTheme.extended.border)
                )
            }

            // 标题栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Poll,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "社群互动 · 群投票",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showCreateDialog) "返回列表" else "+ 发起投票",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .clickable { showCreateDialog = !showCreateDialog }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    )

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = DeepTheme.extended.textTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                }
            } else if (showCreateDialog) {
                // ==================== 发起新群投票表单 ====================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("投票标题 / 主题") },
                        placeholder = { Text("例如：本周六极客线上分享会主题？") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = DeepTheme.extended.border
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "投票选项 (最少 2 项)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DeepTheme.extended.textSecondary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    newOptions.forEachIndexed { index, opt ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = opt,
                                onValueChange = { updated ->
                                    val copy = newOptions.toMutableList()
                                    copy[index] = updated
                                    newOptions = copy
                                },
                                placeholder = { Text("选项 ${index + 1}") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = DeepTheme.extended.border
                                )
                            )

                            if (newOptions.size > 2) {
                                IconButton(
                                    onClick = {
                                        val copy = newOptions.toMutableList()
                                        copy.removeAt(index)
                                        newOptions = copy
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "删除选项",
                                        tint = DeepTheme.extended.textTertiary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (newOptions.size < 8) {
                        Text(
                            text = "+ 添加更多选项",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { newOptions = newOptions + "" }
                                .padding(vertical = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "允许多选",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Switch(
                            checked = newIsMultiple,
                            onCheckedChange = { newIsMultiple = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    DeepButton(
                        text = if (isCreating) "正在发起…" else "确认发起群投票",
                        enabled = !isCreating && newTitle.isNotBlank() && newOptions.count { it.isNotBlank() } >= 2,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            isCreating = true
                            scope.launch {
                                val validOpts = newOptions.filter { it.isNotBlank() }
                                chatRepository.createGroupPoll(
                                    roomId = roomId,
                                    title = newTitle,
                                    options = validOpts,
                                    isMultiple = newIsMultiple
                                ).collect { res ->
                                    isCreating = false
                                    when (res) {
                                        is Resource.Success -> {
                                            Toast.makeText(context, "群投票已成功发起！", Toast.LENGTH_SHORT).show()
                                            showCreateDialog = false
                                            newTitle = ""
                                            newOptions = listOf("", "")
                                            loadPolls()
                                        }
                                        is Resource.Error -> {
                                            Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                        }
                                        else -> {}
                                    }
                                }
                            }
                        }
                    )
                }
            } else if (polls.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "当前群内暂无进行中的投票",
                            fontSize = 13.sp,
                            color = DeepTheme.extended.textTertiary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "点击右上角「+ 发起投票」开启讨论",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                // ==================== 投票列表展示 ====================
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(polls, key = { it.id }) { poll ->
                        val selectedSet = selectedOptionsMap[poll.id] ?: emptySet()
                        val showStats = poll.hasVoted || poll.isExpired

                        GlassBox(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (poll.isMultiple) "多选 (最多 ${poll.maxChoices} 项)" else "单选投票",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = if (poll.isExpired) "已截止" else "共 ${poll.totalVotes} 票",
                                        fontSize = 11.sp,
                                        color = DeepTheme.extended.textTertiary
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = poll.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                poll.options.forEach { option ->
                                    val isSelected = selectedSet.contains(option.id)

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                else MaterialTheme.colorScheme.surface
                                            )
                                            .border(
                                                0.8.dp,
                                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                                else DeepTheme.extended.border.copy(alpha = 0.5f),
                                                RoundedCornerShape(10.dp)
                                            )
                                            .clickable(enabled = !poll.hasVoted && !poll.isExpired) {
                                                val next = if (poll.isMultiple) {
                                                    if (selectedSet.contains(option.id)) selectedSet - option.id
                                                    else selectedSet + option.id
                                                } else {
                                                    setOf(option.id)
                                                }
                                                selectedOptionsMap[poll.id] = next
                                            }
                                            .padding(horizontal = 12.dp, vertical = 9.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = (if (option.isVoted) "✓ " else "") + option.optionText,
                                                fontSize = 13.sp,
                                                fontWeight = if (option.isVoted || isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )

                                            if (showStats) {
                                                Text(
                                                    text = "${option.voteCount} 票 (${option.percentage.toInt()}%)",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = DeepTheme.extended.textTertiary
                                                )
                                            }
                                        }

                                        if (showStats) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            LinearProgressIndicator(
                                                progress = { (option.percentage / 100f).coerceIn(0f, 1f) },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(5.dp)
                                                    .clip(RoundedCornerShape(3.dp)),
                                                color = if (option.isVoted) MaterialTheme.colorScheme.primary else DeepTheme.extended.textTertiary,
                                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                                            )
                                        }
                                    }
                                }

                                if (!poll.hasVoted && !poll.isExpired && selectedSet.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    DeepButton(
                                        text = if (isSubmittingVoteId == poll.id) "正在提交…" else "确认投票",
                                        enabled = isSubmittingVoteId == null,
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = {
                                            isSubmittingVoteId = poll.id
                                            scope.launch {
                                                chatRepository.voteGroupPoll(
                                                    roomId = roomId,
                                                    pollId = poll.id,
                                                    optionIds = selectedSet.toList()
                                                ).collect { res ->
                                                    isSubmittingVoteId = null
                                                    when (res) {
                                                        is Resource.Success -> {
                                                            Toast.makeText(context, "投票成功！", Toast.LENGTH_SHORT).show()
                                                            loadPolls()
                                                        }
                                                        is Resource.Error -> {
                                                            Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                                        }
                                                        else -> {}
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}
