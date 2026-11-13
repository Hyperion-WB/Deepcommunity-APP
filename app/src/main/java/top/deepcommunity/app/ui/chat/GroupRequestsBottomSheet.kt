package top.deepcommunity.app.ui.chat

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.deepcommunity.app.core.common.Resource
import top.deepcommunity.app.core.designsystem.component.DeepButton
import top.deepcommunity.app.core.designsystem.component.UserAvatar
import top.deepcommunity.app.core.designsystem.theme.LocalExtendedColors
import top.deepcommunity.app.data.model.GroupApplyRequestDto
import top.deepcommunity.app.data.repository.ChatRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupRequestsBottomSheet(
    groupId: String,
    chatRepository: ChatRepository,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val extColors = LocalExtendedColors.current

    var isLoading by remember { mutableStateOf(true) }
    val requests = remember { mutableStateListOf<GroupApplyRequestDto>() }

    fun loadRequests() {
        isLoading = true
        scope.launch {
            chatRepository.getGroupRequests(groupId).collect { res ->
                isLoading = false
                when (res) {
                    is Resource.Success -> {
                        requests.clear()
                        res.data?.let { requests.addAll(it) }
                    }
                    is Resource.Error -> {
                        Toast.makeText(context, res.message ?: "获取入群申请失败", Toast.LENGTH_SHORT).show()
                    }
                    else -> {}
                }
            }
        }
    }

    LaunchedEffect(groupId) {
        loadRequests()
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
                .height(480.dp)
                .navigationBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(extColors.border)
                )
            }

            Text(
                text = "入群申请审批",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.dp
                    )
                }
            } else if (requests.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无待审批的入群申请",
                        fontSize = 13.sp,
                        color = extColors.textSecondary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(requests, key = { it.id }) { req ->
                        val displayName = req.nickname ?: req.username ?: "申请人"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatar(
                                avatarUrl = req.avatarUrl,
                                displayName = displayName,
                                size = 44.dp
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = displayName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!req.reason.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "理由: ${req.reason}",
                                        fontSize = 12.sp,
                                        color = extColors.textSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                req.createdAt?.let { time ->
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = time,
                                        fontSize = 10.sp,
                                        color = extColors.textTertiary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // 拒绝
                            DeepButton(
                                text = "拒绝",
                                onClick = {
                                    scope.launch {
                                        chatRepository.rejectGroupRequest(groupId, req.id).collect { res ->
                                            when (res) {
                                                is Resource.Success -> {
                                                    requests.remove(req)
                                                    Toast.makeText(context, "已拒绝申请", Toast.LENGTH_SHORT).show()
                                                }
                                                is Resource.Error -> {
                                                    Toast.makeText(context, res.message ?: "操作失败", Toast.LENGTH_SHORT).show()
                                                }
                                                else -> {}
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.height(32.dp),
                                isPrimary = false
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            // 批准
                            DeepButton(
                                text = "通过",
                                onClick = {
                                    scope.launch {
                                        chatRepository.approveGroupRequest(groupId, req.id).collect { res ->
                                            when (res) {
                                                is Resource.Success -> {
                                                    requests.remove(req)
                                                    Toast.makeText(context, "已批准加入群聊", Toast.LENGTH_SHORT).show()
                                                }
                                                is Resource.Error -> {
                                                    Toast.makeText(context, res.message ?: "操作失败", Toast.LENGTH_SHORT).show()
                                                }
                                                else -> {}
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.height(32.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
