package top.deepcommunity.app.ui.profile

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import top.deepcommunity.app.core.designsystem.theme.LocalExtendedColors
import top.deepcommunity.app.data.model.UpdateDndRequest
import top.deepcommunity.app.data.model.UpdatePrivacyRequest
import top.deepcommunity.app.data.repository.UserRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyDndBottomSheet(
    userRepository: UserRepository,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val extColors = LocalExtendedColors.current

    var hidePosts by remember { mutableStateOf(false) }
    var hideCollections by remember { mutableStateOf(false) }
    var isDndEnabled by remember { mutableStateOf(false) }
    var startTime by remember { mutableStateOf("23:00") }
    var endTime by remember { mutableStateOf("07:00") }
    var isSaving by remember { mutableStateOf(false) }

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
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
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
                text = "隐私与夜间免打扰",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 隐私设置部分
            Text(
                text = "隐私设置",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = extColors.textSecondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 隐藏我的主页动态
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "隐藏主页动态",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "开启后其他居民访问您的个人主页时将无法查看您的发帖",
                        fontSize = 11.sp,
                        color = extColors.textTertiary
                    )
                }
                Switch(
                    checked = hidePosts,
                    onCheckedChange = { hidePosts = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            // 隐藏我的个人收藏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "隐藏个人收藏",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "开启后其他居民将无法获知您的收藏列表",
                        fontSize = 11.sp,
                        color = extColors.textTertiary
                    )
                }
                Switch(
                    checked = hideCollections,
                    onCheckedChange = { hideCollections = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 免打扰部分
            Text(
                text = "夜间免打扰 (DND)",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = extColors.textSecondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "定时夜间免打扰",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "免打扰时段内（$startTime ~ $endTime）社区消息不弹出通知响铃",
                        fontSize = 11.sp,
                        color = extColors.textTertiary
                    )
                }
                Switch(
                    checked = isDndEnabled,
                    onCheckedChange = { isDndEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            DeepButton(
                text = if (isSaving) "保存中..." else "保存设置",
                onClick = {
                    if (isSaving) return@DeepButton
                    isSaving = true
                    scope.launch {
                        val privacyReq = UpdatePrivacyRequest(
                            hidePosts = hidePosts,
                            hideCollections = hideCollections
                        )
                        val dndReq = UpdateDndRequest(
                            dndEnabled = isDndEnabled,
                            dndStart = startTime,
                            dndEnd = endTime
                        )

                        launch { userRepository.updatePrivacy(privacyReq).collect {} }
                        launch {
                            userRepository.updateDnd(dndReq).collect { res ->
                                isSaving = false
                                when (res) {
                                    is Resource.Success -> {
                                        Toast.makeText(context, "隐私与免打扰设置已更新", Toast.LENGTH_SHORT).show()
                                        onDismissRequest()
                                    }
                                    is Resource.Error -> {
                                        Toast.makeText(context, res.message ?: "保存设置失败", Toast.LENGTH_SHORT).show()
                                    }
                                    else -> {}
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving
            )

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}
