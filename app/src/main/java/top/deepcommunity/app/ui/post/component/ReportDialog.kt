package top.deepcommunity.app.ui.post.component

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.deepcommunity.app.core.common.Resource
import top.deepcommunity.app.core.designsystem.component.DeepButton
import top.deepcommunity.app.core.designsystem.theme.LocalExtendedColors
import top.deepcommunity.app.data.repository.PostRepository

private val REASONS = listOf(
    "SPAM" to "垃圾广告 / 恶意刷屏",
    "ABUSE" to "辱骂引战 / 人身攻击",
    "PORN" to "低俗色情 / 不良信息",
    "ILLEGAL" to "违法违规 / 虚假造谣",
    "PRIVACY" to "泄露隐私 / 侵犯人权",
    "OTHER" to "其他违规行为"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDialog(
    targetType: String, // "POST" | "COMMENT" | "USER"
    targetId: String,
    targetTitle: String? = null,
    postRepository: PostRepository,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val extColors = LocalExtendedColors.current

    var selectedReason by remember { mutableStateOf(REASONS[0].first) }
    var description by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    val typeLabel = when (targetType.uppercase()) {
        "POST" -> "举报帖子"
        "COMMENT" -> "举报评论"
        "USER" -> "举报用户"
        else -> "举报内容"
    }

    BasicAlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismissRequest() },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, extColors.glassBorder, RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = typeLabel,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            if (!targetTitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "针对: $targetTitle",
                    fontSize = 12.sp,
                    color = extColors.textTertiary,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "请选择违规类型",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = extColors.textSecondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                REASONS.forEach { (code, label) ->
                    val isSelected = selectedReason == code
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                else Color.Transparent
                            )
                            .border(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else extColors.border,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedReason = code }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                )
                                .border(1.5.dp, extColors.textTertiary, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { if (it.length <= 200) description = it },
                placeholder = { Text("补充详细描述（选填，最多200字）", fontSize = 12.sp, color = extColors.textTertiary) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = extColors.border
                ),
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DeepButton(
                    text = "取消",
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f),
                    isPrimary = false,
                    enabled = !isSubmitting
                )

                DeepButton(
                    text = if (isSubmitting) "提交中..." else "确认举报",
                    onClick = {
                        if (isSubmitting) return@DeepButton
                        isSubmitting = true
                        scope.launch {
                            postRepository.createReport(
                                targetType = targetType.uppercase(),
                                targetId = targetId,
                                reasonType = selectedReason,
                                detail = description
                            ).collect { res ->
                                isSubmitting = false
                                when (res) {
                                    is Resource.Success -> {
                                        Toast.makeText(context, "举报已收到，管理员将尽快审核处理", Toast.LENGTH_SHORT).show()
                                        onDismissRequest()
                                    }
                                    is Resource.Error -> {
                                        Toast.makeText(context, res.message ?: "提交举报失败", Toast.LENGTH_SHORT).show()
                                    }
                                    else -> {}
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isSubmitting
                )
            }
        }
    }
}
