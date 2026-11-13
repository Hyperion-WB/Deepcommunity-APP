package top.deepcommunity.app.ui.home

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.deepcommunity.app.core.common.Resource
import top.deepcommunity.app.core.designsystem.component.GlassBox
import top.deepcommunity.app.core.designsystem.component.UserAvatar
import top.deepcommunity.app.core.designsystem.theme.LocalExtendedColors
import top.deepcommunity.app.data.model.CheckinStatusDetailDto
import top.deepcommunity.app.data.model.LeaderboardsResponse
import top.deepcommunity.app.data.repository.PostRepository
import top.deepcommunity.app.data.repository.UserRepository

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LeaderboardsBottomSheet(
    postRepository: PostRepository,
    userRepository: UserRepository,
    onDismissRequest: () -> Unit,
    onUserClick: (Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val extColors = LocalExtendedColors.current

    var selectedTab by remember { mutableIntStateOf(0) } // 0: 连续签到, 1: 早起榜, 2: 7日活跃
    var isLoading by remember { mutableStateOf(true) }
    var leaderboardsData by remember { mutableStateOf<LeaderboardsResponse?>(null) }
    var checkinStatus by remember { mutableStateOf<CheckinStatusDetailDto?>(null) }

    fun refreshAll() {
        scope.launch {
            launch {
                postRepository.getLeaderboards().collect { res ->
                    if (res is Resource.Success) {
                        leaderboardsData = res.data
                    }
                    isLoading = false
                }
            }
            launch {
                userRepository.getCheckinStatus().collect { res ->
                    if (res is Resource.Success) {
                        checkinStatus = res.data
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshAll()
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
                .height(600.dp)
                .navigationBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(extColors.border)
                )
            }

            // 打卡热力图卡片
            GlassBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "📅 签到热力图",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "连续 ${checkinStatus?.streak ?: 0} 天 · 最高 ${checkinStatus?.bestStreak ?: 0} 天",
                                fontSize = 11.sp,
                                color = extColors.textSecondary
                            )
                        }

                        val makeupCount = checkinStatus?.makeupLeft ?: 0
                        if (makeupCount > 0) {
                            Text(
                                text = "补签卡 ×$makeupCount",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                    .clickable {
                                        scope.launch {
                                            userRepository.makeupCheckin().collect { res ->
                                                when (res) {
                                                    is Resource.Success -> {
                                                        Toast.makeText(context, "补签成功！", Toast.LENGTH_SHORT).show()
                                                        refreshAll()
                                                    }
                                                    is Resource.Error -> {
                                                        Toast.makeText(context, res.message ?: "补签失败", Toast.LENGTH_SHORT).show()
                                                    }
                                                    else -> {}
                                                }
                                            }
                                        }
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 28格（近4周）极简热力图绿格子
                    val streak = checkinStatus?.streak ?: 0
                    val checkedToday = checkinStatus?.checkedToday == true

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        maxItemsInEachRow = 14
                    ) {
                        for (i in 27 downTo 0) {
                            val isToday = i == 0
                            val isChecked = if (isToday) checkedToday else (i < streak)
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        when {
                                            isChecked -> Color(0xFF388E3C)
                                            isToday -> extColors.border
                                            else -> extColors.surfaceElevated
                                        }
                                    )
                                    .then(
                                        if (isToday) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))
                                        else Modifier
                                    )
                            )
                        }
                    }
                }
            }

            // 榜单 Tab
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("🔥 连续签到", fontSize = 13.sp, fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("⚡ 抢早签榜", fontSize = 13.sp, fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("🌟 7日活跃", fontSize = 13.sp, fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) }
                )
            }

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
            } else {
                val currentList = when (selectedTab) {
                    0 -> leaderboardsData?.total ?: emptyList()
                    1 -> leaderboardsData?.early ?: emptyList()
                    else -> leaderboardsData?.active ?: emptyList()
                }

                if (currentList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "榜单正在统计中，快来争夺第一名吧~",
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
                        itemsIndexed(currentList, key = { _, item -> item.userId }) { index, item ->
                            val rank = item.rank.takeIf { it > 0 } ?: (index + 1)
                            val displayName = item.nickname ?: item.username.ifBlank { "居民" }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onUserClick(item.userId.toLongOrNull() ?: 0L) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 排名徽章
                                Box(
                                    modifier = Modifier.size(28.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    when (rank) {
                                        1 -> Text("🥇", fontSize = 18.sp)
                                        2 -> Text("🥈", fontSize = 18.sp)
                                        3 -> Text("🥉", fontSize = 18.sp)
                                        else -> Text(
                                            text = "$rank",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = extColors.textTertiary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                UserAvatar(
                                    avatarUrl = item.avatarUrl,
                                    displayName = displayName,
                                    size = 40.dp
                                )

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = displayName,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onBackground,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = "Lv.${item.level}",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    if (item.title.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = item.title,
                                            fontSize = 11.sp,
                                            color = extColors.textTertiary
                                        )
                                    }
                                }

                                Text(
                                    text = item.metricLabel.ifBlank {
                                        when (selectedTab) {
                                            0 -> "${item.level} 级"
                                            1 -> "早签前列"
                                            else -> "高活跃"
                                        }
                                    },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
