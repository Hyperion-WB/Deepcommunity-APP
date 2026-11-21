package top.deepcommunity.app.ui.chat

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.deepcommunity.app.core.common.Resource
import top.deepcommunity.app.core.designsystem.component.DeepButton
import top.deepcommunity.app.core.designsystem.component.GlassBox
import top.deepcommunity.app.core.designsystem.theme.DeepTheme
import top.deepcommunity.app.data.model.BattleMatchDto
import top.deepcommunity.app.data.repository.BattleRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BattleGameBottomSheet(
    battleRepository: BattleRepository,
    currentUserId: String? = null,
    targetUserId: String? = null,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var selectedTab by remember { mutableIntStateOf(0) } // 0: 井字棋, 1: 五子棋, 2: 我的对局
    var battles by remember { mutableStateOf<List<BattleMatchDto>>(emptyList()) }
    var isLoadingBattles by remember { mutableStateOf(false) }

    fun loadMyBattles() {
        scope.launch {
            isLoadingBattles = true
            battleRepository.getMyBattles().collect { res ->
                isLoadingBattles = false
                if (res is Resource.Success) {
                    battles = res.data
                }
            }
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 2) {
            loadMyBattles()
        }
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
            // 顶部手柄
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
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "极客对战中心",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

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

            // Tab 切换：井字棋 / 五子棋 / 我的对局
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = MaterialTheme.colorScheme.primary,
                        height = 2.dp
                    )
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("井字棋", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("五子棋", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("对局列表", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (selectedTab) {
                0 -> TicTacToeScreen(
                    targetUserId = targetUserId,
                    onInviteOnline = {
                        if (targetUserId != null) {
                            scope.launch {
                                battleRepository.createBattle("tictactoe", targetUserId).collect { res ->
                                    if (res is Resource.Success) {
                                        Toast.makeText(context, "已向对方发起井字棋挑战！", Toast.LENGTH_SHORT).show()
                                    } else if (res is Resource.Error) {
                                        Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        } else {
                            Toast.makeText(context, "请在好友私聊中直接点击发起挑战", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                1 -> GomokuScreen(
                    targetUserId = targetUserId,
                    onInviteOnline = {
                        if (targetUserId != null) {
                            scope.launch {
                                battleRepository.createBattle("gomoku", targetUserId).collect { res ->
                                    if (res is Resource.Success) {
                                        Toast.makeText(context, "已向对方发起五子棋挑战！", Toast.LENGTH_SHORT).show()
                                    } else if (res is Resource.Error) {
                                        Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        } else {
                            Toast.makeText(context, "请在好友私聊中直接点击发起挑战", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                2 -> BattleListScreen(
                    battles = battles,
                    isLoading = isLoadingBattles,
                    onRefresh = { loadMyBattles() },
                    onAccept = { matchId ->
                        scope.launch {
                            battleRepository.acceptBattle(matchId).collect { res ->
                                if (res is Resource.Success) {
                                    Toast.makeText(context, "已接受对局！", Toast.LENGTH_SHORT).show()
                                    loadMyBattles()
                                }
                            }
                        }
                    },
                    onReject = { matchId ->
                        scope.launch {
                            battleRepository.rejectBattle(matchId).collect { res ->
                                if (res is Resource.Success) {
                                    Toast.makeText(context, "已拒绝该对战", Toast.LENGTH_SHORT).show()
                                    loadMyBattles()
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

// ==========================================
// 1. 经典井字棋 (Tic-Tac-Toe 3x3)
// ==========================================
@Composable
private fun TicTacToeScreen(
    targetUserId: String?,
    onInviteOnline: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val board = remember { mutableStateListOf("", "", "", "", "", "", "", "", "") }
    var isPlayerTurn by remember { mutableStateOf(true) } // true: X (用户), false: O (AI/对手)
    var winner by remember { mutableStateOf<String?>(null) } // "X", "O", "draw", null

    fun checkWin(): String? {
        val lines = listOf(
            listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
            listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
            listOf(0, 4, 8), listOf(2, 4, 6)
        )
        for (l in lines) {
            val a = board[l[0]]
            if (a.isNotBlank() && a == board[l[1]] && a == board[l[2]]) {
                return a
            }
        }
        if (board.none { it.isBlank() }) return "draw"
        return null
    }

    fun resetGame() {
        for (i in 0 until 9) board[i] = ""
        isPlayerTurn = true
        winner = null
    }

    fun makeAiMove() {
        if (winner != null) return
        val emptyIndices = board.indices.filter { board[it].isBlank() }
        if (emptyIndices.isNotEmpty()) {
            val choice = emptyIndices.random()
            board[choice] = "O"
            winner = checkWin()
            isPlayerTurn = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 状态说明
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when (winner) {
                    "X" -> "🎉 恭喜！你执 (X) 获胜！"
                    "O" -> "🤖 AI 执 (O) 获胜！"
                    "draw" -> "🤝 势均力敌，平局！"
                    else -> if (isPlayerTurn) "你的回合 (执 X)" else "极客 AI 思考中…"
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (winner == "X") Color(0xFF10B981) else MaterialTheme.colorScheme.primary
            )

            IconButton(onClick = { resetGame() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Refresh, contentDescription = "重新开始", tint = DeepTheme.extended.textSecondary)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3x3 九宫格棋盘
        Box(
            modifier = Modifier
                .size(270.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(DeepTheme.extended.surfaceElevated)
                .border(1.dp, DeepTheme.extended.border, RoundedCornerShape(16.dp))
                .padding(8.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                for (row in 0..2) {
                    Row(modifier = Modifier.weight(1f)) {
                        for (col in 0..2) {
                            val index = row * 3 + col
                            val piece = board[index]
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(0.8.dp, DeepTheme.extended.border.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                    .clickable(enabled = piece.isBlank() && winner == null && isPlayerTurn) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        board[index] = "X"
                                        winner = checkWin()
                                        if (winner == null) {
                                            isPlayerTurn = false
                                            makeAiMove()
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = piece,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (piece == "X") MaterialTheme.colorScheme.primary else Color(0xFFE53935)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (targetUserId != null) {
            DeepButton(
                text = "发起在线对战邀请 ⚔️",
                modifier = Modifier.fillMaxWidth(),
                onClick = onInviteOnline
            )
        }
    }
}

// ==========================================
// 2. 极客五子棋 (Gomoku 9x9 触屏友好版)
// ==========================================
@Composable
private fun GomokuScreen(
    targetUserId: String?,
    onInviteOnline: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val boardDimension = 9
    val board = remember { mutableStateListOf<Int>().apply { repeat(boardDimension * boardDimension) { add(0) } } } // 0: empty, 1: 黑子(用户), 2: 白子(AI)
    var isBlackTurn by remember { mutableStateOf(true) }
    var winner by remember { mutableStateOf<Int?>(null) } // 1, 2, 0(draw), null

    fun checkWin(x: Int, y: Int, player: Int): Boolean {
        val dirs = listOf(Pair(1, 0), Pair(0, 1), Pair(1, 1), Pair(1, -1))
        for (d in dirs) {
            var count = 1
            // 正向
            var step = 1
            while (true) {
                val nx = x + d.first * step
                val ny = y + d.second * step
                if (nx in 0 until boardDimension && ny in 0 until boardDimension && board[ny * boardDimension + nx] == player) {
                    count++
                    step++
                } else break
            }
            // 反向
            step = 1
            while (true) {
                val nx = x - d.first * step
                val ny = y - d.second * step
                if (nx in 0 until boardDimension && ny in 0 until boardDimension && board[ny * boardDimension + nx] == player) {
                    count++
                    step++
                } else break
            }
            if (count >= 5) return true
        }
        return false
    }

    fun makeAiMove() {
        if (winner != null) return
        val emptyList = board.indices.filter { board[it] == 0 }
        if (emptyList.isNotEmpty()) {
            val idx = emptyList.random()
            board[idx] = 2
            val ax = idx % boardDimension
            val ay = idx / boardDimension
            if (checkWin(ax, ay, 2)) {
                winner = 2
            }
            isBlackTurn = true
        }
    }

    fun resetGame() {
        for (i in board.indices) board[i] = 0
        isBlackTurn = true
        winner = null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when (winner) {
                    1 -> "🎉 执黑 (你) 达成五连珠获胜！"
                    2 -> "🤖 执白 (AI) 达成五连珠！"
                    else -> if (isBlackTurn) "执黑先行 (你的回合)" else "AI 正在推演棋谱…"
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (winner == 1) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
            )

            IconButton(onClick = { resetGame() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Refresh, contentDescription = "重新开始", tint = DeepTheme.extended.textSecondary)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 9x9 纯木纹质感极客棋盘
        Box(
            modifier = Modifier
                .size(280.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFE8D3A7))
                .border(1.5.dp, Color(0xFF9E7E52), RoundedCornerShape(14.dp))
                .pointerInput(winner, isBlackTurn) {
                    detectTapGestures { offset ->
                        if (winner != null || !isBlackTurn) return@detectTapGestures
                        val containerWidth = this.size.width.toFloat()
                        val cellStep = containerWidth / (boardDimension + 1)
                        val gx = ((offset.x - cellStep / 2) / cellStep).toInt().coerceIn(0, boardDimension - 1)
                        val gy = ((offset.y - cellStep / 2) / cellStep).toInt().coerceIn(0, boardDimension - 1)
                        val idx = gy * boardDimension + gx
                        if (board[idx] == 0) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            board[idx] = 1
                            if (checkWin(gx, gy, 1)) {
                                winner = 1
                            } else {
                                isBlackTurn = false
                                makeAiMove()
                            }
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasW = this.size.width
                val cellStep = canvasW / (boardDimension + 1)
                // 绘制网格线
                for (i in 1..boardDimension) {
                    val p = i * cellStep
                    drawLine(Color(0xFF8B6B44), Offset(cellStep, p), Offset(canvasW - cellStep, p), strokeWidth = 1.2f)
                    drawLine(Color(0xFF8B6B44), Offset(p, cellStep), Offset(p, canvasW - cellStep), strokeWidth = 1.2f)
                }

                // 绘制落子
                for (gy in 0 until boardDimension) {
                    for (gx in 0 until boardDimension) {
                        val piece = board[gy * boardDimension + gx]
                        if (piece != 0) {
                            val cx = (gx + 1) * cellStep
                            val cy = (gy + 1) * cellStep
                            val pieceColor = if (piece == 1) Color(0xFF1E1E1E) else Color(0xFFFBFBFB)
                            drawCircle(pieceColor, radius = cellStep * 0.42f, center = Offset(cx, cy))
                            if (piece == 2) {
                                drawCircle(Color(0xFFB0B0B0), radius = cellStep * 0.42f, center = Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(1.2f))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (targetUserId != null) {
            DeepButton(
                text = "发起五子棋在线对战 ⚔️",
                modifier = Modifier.fillMaxWidth(),
                onClick = onInviteOnline
            )
        }
    }
}

// ==========================================
// 3. 对局列表 (我的对局与待接战)
// ==========================================
@Composable
private fun BattleListScreen(
    battles: List<BattleMatchDto>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit
) {
    if (isLoading) {
        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        }
    } else if (battles.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "暂无活跃对局", fontSize = 13.sp, color = DeepTheme.extended.textTertiary)
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "切换至前两个 Tab 即可随时随地畅玩练习", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(battles, key = { it.id }) { match ->
                val gameTitle = when (match.gameCode) {
                    "gomoku" -> "五子棋"
                    "tictactoe" -> "井字棋"
                    else -> "棋类对战"
                }

                GlassBox(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "$gameTitle 对局",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "状态: ${match.status} · 步数: ${match.moveSeq}",
                                fontSize = 11.sp,
                                color = DeepTheme.extended.textTertiary
                            )
                        }

                        if (match.status == "pending") {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "接受",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF10B981).copy(alpha = 0.12f))
                                        .clickable { onAccept(match.id) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                                Text(
                                    text = "拒绝",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                                        .clickable { onReject(match.id) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
