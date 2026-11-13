package top.deepcommunity.app.ui.post.component

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MonetizationOn
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.deepcommunity.app.core.designsystem.component.GlassBox
import top.deepcommunity.app.core.designsystem.theme.LocalExtendedColors
import top.deepcommunity.app.data.model.QaQuestionStateDto

@Composable
fun QaBountyCard(
    qaState: QaQuestionStateDto,
    modifier: Modifier = Modifier
) {
    val extColors = LocalExtendedColors.current
    val isSettled = qaState.status == "settled"

    val goldBg = Color(0xFFFFB300)

    GlassBox(
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isSettled) extColors.border else goldBg.copy(alpha = 0.35f),
                RoundedCornerShape(14.dp)
            ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isSettled) Icons.Outlined.WorkspacePremium else Icons.Outlined.MonetizationOn,
                    contentDescription = null,
                    tint = if (isSettled) Color(0xFF10B981) else goldBg,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "问答悬赏: ${qaState.bounty} 经验",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSettled) MaterialTheme.colorScheme.onBackground else goldBg
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (isSettled) Color(0xFF10B981).copy(alpha = 0.12f)
                                    else goldBg.copy(alpha = 0.12f)
                                )
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = if (isSettled) "已解决" else "待采纳",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSettled) Color(0xFF10B981) else goldBg
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = if (isSettled) "题主已采纳最佳回答并派发奖励" else "写下你的专业解答，采纳后可瓜分经验奖励",
                        fontSize = 11.sp,
                        color = extColors.textSecondary
                    )
                }
            }
        }
    }
}
