package top.deepcommunity.app.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.regex.Pattern
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.material3.LocalTextStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import top.deepcommunity.app.core.designsystem.theme.DeepMotion
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.ui.graphics.vector.ImageVector
import top.deepcommunity.app.ui.chat.component.DiceCube
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.deepcommunity.app.core.common.ChatContentParser
import top.deepcommunity.app.core.common.Constants
import top.deepcommunity.app.core.designsystem.component.GlassBox
import top.deepcommunity.app.core.designsystem.component.ImageViewerDialog
import top.deepcommunity.app.core.designsystem.component.UserAvatar
import top.deepcommunity.app.core.designsystem.theme.AccentLike
import top.deepcommunity.app.core.designsystem.theme.DeepTheme
import top.deepcommunity.app.data.model.ChatMessageDto
import top.deepcommunity.app.data.model.GroupCheckinStatusDto
import top.deepcommunity.app.data.model.GroupMemberDto
import top.deepcommunity.app.data.model.StickerItemDto
import top.deepcommunity.app.ui.chat.GroupRequestsBottomSheet
import top.deepcommunity.app.ui.profile.UserProfileBottomSheet

// 预设高频常用 Emoji
private val DEFAULT_EMOJIS = listOf(
    "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "😉", "😊", "😇",
    "🥰", "😍", "🤩", "😘", "😗", "😚", "😋", "😛", "😜", "🤪", "😝", "🤑",
    "🤗", "🤭", "🤫", "🤔", "🤐", "🤨", "😐", "😑", "😶", "😏", "😒", "🙄",
    "😬", "🤥", "😌", "😔", "😪", "🤤", "😴", "😷", "🤒", "🤕", "🤢", "🤮",
    "🤧", "🥵", "🥶", "🥴", "😵", "🤯", "🤠", "🥳", "😎", "🤓", "🧐", "😕",
    "👍", "👎", "👊", "✊", "✌️", "👌", "🤌", "🤏", "👈", "👉", "👆", "👇",
    "✋", "🤚", "🖐️", "👋", "🤙", "💪", "🙏", "❤️", "🔥", "✨", "🌟", "🎉"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBackClick: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()
    var showPrivateMenu by remember { mutableStateOf(false) }
    var showBlockConfirmDialog by remember { mutableStateOf(false) }
    var highlightedMsgId by remember { mutableStateOf<String?>(null) }
    var previewChatImageUrl by remember { mutableStateOf<String?>(null) }
    var showGroupRequestsSheet by remember { mutableStateOf(false) }
    var selectedChatUserId by remember { mutableStateOf<String?>(null) }
    var selectedChatUsername by remember { mutableStateOf<String?>(null) }
    var showDailyFortuneDialog by remember { mutableStateOf(false) }
    var showIdiomChainSheet by remember { mutableStateOf(false) }

    // 相册选图发送
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.uploadAndSendImage(uri)
        }
    }

    LaunchedEffect(key1 = true) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is ChatEvent.ShowToast -> snackbarHostState.showSnackbar(event.message)
                is ChatEvent.ScrollToBottom -> {
                    if (uiState.messages.isNotEmpty()) {
                        listState.animateScrollToItem(uiState.messages.size - 1)
                    }
                }
                is ChatEvent.ScrollToIndex -> {
                    if (event.index in uiState.messages.indices) {
                        listState.animateScrollToItem(event.index)
                    }
                }
            }
        }
    }

    // 默认打开时/加载完成时立即精准定位到最新消息底部（若正在定位未读则不抢焦）
    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading && uiState.messages.isNotEmpty() && uiState.unreadAtEntry == 0) {
            listState.scrollToItem(uiState.messages.size - 1)
        }
    }

    // 往上滑动自动触发加载更早历史消息
    val shouldLoadOlder by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex <= 1 && !uiState.isLoading && !uiState.isLoadingOlder && uiState.hasMoreHistory && uiState.messages.isNotEmpty()
        }
    }
    LaunchedEffect(shouldLoadOlder) {
        if (shouldLoadOlder) {
            viewModel.loadOlderHistory()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        text = uiState.chatTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                if (uiState.isGroupChat) {
                    IconButton(onClick = { viewModel.toggleGroupInfoSheet(true) }) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = "群信息",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                } else {
                    Box {
                        IconButton(onClick = { showPrivateMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "更多",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        DropdownMenu(
                            expanded = showPrivateMenu,
                            onDismissRequest = { showPrivateMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("加入黑名单 / 屏蔽消息", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                },
                                onClick = {
                                    showPrivateMenu = false
                                    showBlockConfirmDialog = true
                                }
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
            ) {
                // 引用回复预览横条（智能折叠图片路径，绝不裸露原始地址）
                AnimatedVisibility(
                    visible = uiState.replyingToMessage != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    val replyMsg = uiState.replyingToMessage
                    if (replyMsg != null) {
                        val replySnippetText = remember(replyMsg) {
                            ChatContentParser.formatReplySnippet(replyMsg.replySnippet, replyMsg.content)
                        }
                        val parsed = remember(replyMsg) {
                            ChatContentParser.parse(replyMsg.content, replyMsg.msgType)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DeepTheme.extended.surfaceElevated)
                                .border(1.dp, DeepTheme.extended.border)
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Reply,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                if (parsed.imageUrls.isNotEmpty()) {
                                    val firstThumb = ChatContentParser.toFullImageUrl(parsed.imageUrls.first())
                                    AsyncImage(
                                        model = firstThumb,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = "回复 @${replyMsg.displayName}: $replySnippetText",
                                    fontSize = 12.sp,
                                    color = DeepTheme.extended.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { viewModel.setReplyMessage(null) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "取消回复",
                                    tint = DeepTheme.extended.textTertiary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // 群互动快捷胶囊条 (群聊专属轻量高频互动，无幼稚emoji)
                if (uiState.isGroupChat) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GroupActionChip(
                            icon = Icons.Outlined.Casino,
                            label = "掷骰子",
                            loading = uiState.isRollingDice,
                            onClick = { viewModel.rollDice() }
                        )
                        GroupActionChip(
                            icon = Icons.Outlined.AutoAwesome,
                            label = "每日求签",
                            onClick = { showDailyFortuneDialog = true }
                        )
                        GroupActionChip(
                            icon = Icons.Outlined.Translate,
                            label = "成语接龙",
                            onClick = { showIdiomChainSheet = true }
                        )
                    }
                }

                // 输入框工具栏
                GlassBox(
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 发送图片按钮
                        IconButton(
                            onClick = {
                                imagePickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = !uiState.isUploadingImage,
                            modifier = Modifier.size(36.dp)
                        ) {
                            if (uiState.isUploadingImage) {
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Image,
                                    contentDescription = "发送图片",
                                    tint = DeepTheme.extended.textSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // 表情面板切换按钮
                        IconButton(
                            onClick = { viewModel.toggleStickerPanel() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Mood,
                                contentDescription = "表情面板",
                                tint = if (uiState.showStickerPanel) MaterialTheme.colorScheme.primary else DeepTheme.extended.textSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        OutlinedTextField(
                            value = uiState.inputText,
                            onValueChange = viewModel::onInputTextChange,
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("发送消息…", fontSize = 13.sp, color = DeepTheme.extended.textTertiary) },
                            shape = RoundedCornerShape(20.dp),
                            maxLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = DeepTheme.extended.border,
                                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                            )
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        IconButton(
                            onClick = viewModel::sendMessage,
                            enabled = !uiState.isSending && uiState.inputText.isNotBlank()
                        ) {
                            if (uiState.isSending) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "发送",
                                    tint = if (uiState.inputText.isNotBlank()) MaterialTheme.colorScheme.primary else DeepTheme.extended.textTertiary
                                )
                            }
                        }
                    }
                }

                // 表情面板（Emoji + 自定义表情双 Tab）
                AnimatedVisibility(
                    visible = uiState.showStickerPanel,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    EnhancedStickerDrawer(
                        selectedTab = uiState.stickerTab,
                        onTabChange = viewModel::setStickerTab,
                        onEmojiClick = { emoji -> viewModel.appendText(emoji) },
                        stickers = uiState.stickers,
                        isLoadingStickers = uiState.isLoadingStickers,
                        onStickerClick = viewModel::sendSticker,
                        onStickerDelete = viewModel::deleteSticker
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 顶部历史消息加载指示器 / 按钮 (对齐 Web 端)
                if (uiState.isLoadingOlder) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "正在加载更早的消息…",
                                    fontSize = 12.sp,
                                    color = DeepTheme.extended.textTertiary
                                )
                            }
                        }
                    }
                } else if (uiState.hasMoreHistory && uiState.messages.isNotEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "加载更早的消息",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                    .border(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                    .clickable { viewModel.loadOlderHistory() }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                items(uiState.messages, key = { it.id }) { msg ->
                    val isMe = msg.senderId == uiState.currentUserId
                    ChatMessageBubble(
                        message = msg,
                        isMe = isMe,
                        isGroup = uiState.isGroupChat,
                        isHighlighted = (msg.id == highlightedMsgId),
                        onAvatarLongClick = {
                            // 长按头像快捷 @该成员（优先取系统的唯一用户名 username，绝不显示长串 UUID）
                            val atUsername = msg.senderUsername?.takeIf { it.isNotBlank() }
                                ?: msg.sender?.username?.takeIf { it.isNotBlank() }
                                ?: msg.displayName.takeIf { it.isNotBlank() && it != "用户" }
                                ?: "居民"
                            viewModel.appendText("@$atUsername ")
                        },
                        onQuoteClick = { replyId ->
                            val targetIndex = uiState.messages.indexOfFirst { it.id == replyId }
                            if (targetIndex != -1) {
                                scope.launch {
                                    listState.animateScrollToItem(targetIndex)
                                    highlightedMsgId = replyId
                                    delay(1500)
                                    highlightedMsgId = null
                                }
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar("被回复的消息较早，请向上滑动查看")
                                }
                            }
                        },
                        onReplyClick = { viewModel.setReplyMessage(msg) },
                        onRecallClick = { viewModel.recallMessage(msg) },
                        onCopyClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("chat_msg", msg.content))
                            scope.launch { snackbarHostState.showSnackbar("已复制到剪贴板") }
                        },
                        onImageClick = { previewChatImageUrl = it },
                        onCollectSticker = viewModel::collectSticker,
                        onUserClick = { uName, uId ->
                            selectedChatUsername = uName
                            selectedChatUserId = uId
                        }
                    )
                }
            }

            // 顶部未读定位悬浮胶囊 (对齐 Web 端 jumpToUnread)
            AnimatedVisibility(
                visible = uiState.unreadAtEntry > 0,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .shadow(8.dp, RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha = 0.25f))
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { viewModel.jumpToUnread() }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ArrowUpward,
                        contentDescription = "定位到未读",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (uiState.isJumpingToUnread) "正在加载未读消息…" else "${uiState.unreadAtEntry} 条未读 · 回到上次位置",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                            .clickable { viewModel.dismissUnreadBanner() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭提示",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            // 右下角回到最新悬浮按钮
            val showScrollToBottom by remember {
                derivedStateOf {
                    val total = listState.layoutInfo.totalItemsCount
                    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    total > 8 && lastVisible < total - 3
                }
            }

            AnimatedVisibility(
                visible = showScrollToBottom,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .shadow(6.dp, RoundedCornerShape(18.dp), spotColor = Color.Black.copy(alpha = 0.2f))
                        .clip(RoundedCornerShape(18.dp))
                        .background(DeepTheme.extended.surfaceElevated)
                        .border(0.8.dp, DeepTheme.extended.border, RoundedCornerShape(18.dp))
                        .clickable {
                            scope.launch {
                                if (uiState.messages.isNotEmpty()) {
                                    listState.animateScrollToItem(uiState.messages.size - 1)
                                }
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ArrowDownward,
                        contentDescription = "回到最新",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "回到最新",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // 群信息半屏抽屉
        if (uiState.showGroupInfoSheet) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.toggleGroupInfoSheet(false) },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                GroupInfoSheetContent(
                    roomTitle = uiState.chatTitle,
                    announcement = uiState.groupAnnouncement,
                    members = uiState.groupMembers,
                    isLoading = uiState.isLoadingGroupMembers,
                    myRole = uiState.myRole,
                    currentUserId = uiState.currentUserId,
                    groupCheckinStatus = uiState.groupCheckinStatus,
                    onMemberClick = { member ->
                        val atUsername = member.username.takeIf { it.isNotBlank() } ?: member.displayName
                        viewModel.appendText("@$atUsername ")
                        viewModel.toggleGroupInfoSheet(false)
                    },
                    onKickMember = viewModel::kickMember,
                    onUpdateRole = viewModel::updateMemberRole,
                    onMuteMember = viewModel::muteMember,
                    onUnmuteMember = viewModel::unmuteMember,
                    onOpenRequests = { showGroupRequestsSheet = true },
                    onLeaveGroup = {
                        viewModel.toggleGroupInfoSheet(false)
                        viewModel.leaveCurrentGroup(onBackClick)
                    }
                )
            }
        }

        // 私聊拉黑确认对话框
        if (showBlockConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showBlockConfirmDialog = false },
                title = { Text("加入黑名单") },
                text = { Text("确定要将用户 @${uiState.chatTitle} 加入黑名单吗？加入后将不再接收对方发送的任何私聊消息。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showBlockConfirmDialog = false
                            viewModel.blockUser(uiState.chatTitle) { onBackClick() }
                        }
                    ) {
                        Text("确定拉黑", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBlockConfirmDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }

        // 沉浸式专业看图弹层（双指缩放/双击放大/下拉退出/一键保存相册）
        ImageViewerDialog(
            imageUrl = previewChatImageUrl,
            onDismissRequest = { previewChatImageUrl = null }
        )

        // 入群审批中心抽屉
        if (showGroupRequestsSheet) {
            GroupRequestsBottomSheet(
                groupId = viewModel.convId,
                chatRepository = viewModel.chatRepo,
                onDismissRequest = { showGroupRequestsSheet = false }
            )
        }

        // 他人个人主页弹窗
        (selectedChatUsername ?: selectedChatUserId)?.let { _ ->
            UserProfileBottomSheet(
                username = selectedChatUsername,
                userId = selectedChatUserId,
                userRepository = viewModel.userRepo,
                onDismissRequest = {
                    selectedChatUserId = null
                    selectedChatUsername = null
                }
            )
        }

        // 每日求签弹窗
        if (showDailyFortuneDialog) {
            DailyFortuneDialog(
                roomId = viewModel.convId,
                chatRepository = viewModel.chatRepo,
                onDismissRequest = { showDailyFortuneDialog = false }
            )
        }

        // 成语接龙抽屉
        if (showIdiomChainSheet) {
            IdiomChainBottomSheet(
                roomId = viewModel.convId,
                chatRepository = viewModel.chatRepo,
                onDismissRequest = { showIdiomChainSheet = false }
            )
        }
    }
}

@Composable
private fun GroupActionChip(
    icon: ImageVector,
    label: String,
    loading: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DeepTheme.extended.surfaceElevated.copy(alpha = 0.85f))
            .border(1.dp, DeepTheme.extended.border.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .clickable(enabled = !loading, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(13.dp)
            )
        }
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EnhancedStickerDrawer(
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    onEmojiClick: (String) -> Unit,
    stickers: List<StickerItemDto>,
    isLoadingStickers: Boolean,
    onStickerClick: (StickerItemDto) -> Unit,
    onStickerDelete: ((String) -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    var stickerToDelete by remember { mutableStateOf<StickerItemDto?>(null) }

    if (stickerToDelete != null) {
        val target = stickerToDelete!!
        AlertDialog(
            onDismissRequest = { stickerToDelete = null },
            title = { Text("移除表情包", fontSize = 15.sp) },
            text = { Text("确定要将该表情包从「我的表情」中移除吗？", fontSize = 13.sp) },
            confirmButton = {
                TextButton(
                    onClick = {
                        stickerToDelete = null
                        onStickerDelete?.invoke(target.id)
                    }
                ) {
                    Text("确定移除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { stickerToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .background(DeepTheme.extended.surfaceElevated)
            .border(1.dp, DeepTheme.extended.border)
            .padding(8.dp)
    ) {
        // Tab 胶囊切换
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(0.8.dp, DeepTheme.extended.border, RoundedCornerShape(12.dp))
                    .padding(2.dp)
            ) {
                val tabs = listOf("常用表情", "我的表情包")
                tabs.forEachIndexed { index, name ->
                    val isSelected = selectedTab == index
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { onTabChange(index) }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = name,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else DeepTheme.extended.textTertiary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (selectedTab == 0) {
            // Emoji 网格
            LazyVerticalGrid(
                columns = GridCells.Fixed(8),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(4.dp)
            ) {
                items(DEFAULT_EMOJIS) { emoji ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onEmojiClick(emoji) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = emoji, fontSize = 22.sp)
                    }
                }
            }
        } else {
            // 我的自选表情包
            if (isLoadingStickers) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
            } else if (stickers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "暂无表情包", fontSize = 12.sp, color = DeepTheme.extended.textTertiary)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(6.dp)
                ) {
                    items(stickers, key = { it.id }) { sticker ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .combinedClickable(
                                    onClick = { onStickerClick(sticker) },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        stickerToDelete = sticker
                                    }
                                )
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = sticker.finalThumbUrl,
                                contentDescription = "表情",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatMessageBubble(
    message: ChatMessageDto,
    isMe: Boolean,
    isGroup: Boolean,
    isHighlighted: Boolean = false,
    onAvatarLongClick: () -> Unit,
    onQuoteClick: (String) -> Unit,
    onReplyClick: () -> Unit,
    onRecallClick: () -> Unit,
    onCopyClick: () -> Unit,
    onImageClick: (String) -> Unit = {},
    onCollectSticker: (String) -> Unit = {},
    onUserClick: (username: String?, userId: String?) -> Unit = { _, _ -> }
) {
    // 若消息已撤回
    if (message.isRecalled) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isMe) "你撤回了一条消息" else "${message.displayName} 撤回了一条消息",
                fontSize = 11.sp,
                color = DeepTheme.extended.textTertiary
            )
        }
        return
    }

    var showMenu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    // 使用智能解析器分离文本、图片列表、骰子、签到
    val parsed = remember(message.content, message.msgType) {
        ChatContentParser.parse(message.content, message.msgType)
    }

    // 高亮定位动画
    val highlightColor by animateColorAsState(
        targetValue = if (isHighlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f) else Color.Transparent,
        label = "msg_highlight"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(highlightColor)
            .padding(vertical = 2.dp, horizontal = 4.dp),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        // 群聊中非自己发送的消息，展示头像与昵称
        if (!isMe && isGroup) {
            val avatarInteractionSource = remember { MutableInteractionSource() }
            val isAvatarPressed by avatarInteractionSource.collectIsPressedAsState()
            val avatarScale by animateFloatAsState(
                targetValue = if (isAvatarPressed) 0.92f else 1f,
                animationSpec = DeepMotion.bouncy(),
                label = "avatar_press_scale"
            )

            Box(
                modifier = Modifier
                    .padding(end = 8.dp, top = 2.dp)
                    .scale(avatarScale)
                    .clip(CircleShape)
                    .combinedClickable(
                        interactionSource = avatarInteractionSource,
                        indication = null,
                        onClick = {
                            onUserClick(message.senderUsername, message.senderId)
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAvatarLongClick()
                        }
                    )
            ) {
                UserAvatar(
                    avatarUrl = message.finalAvatarUrl,
                    displayName = message.displayName,
                    size = 36.dp
                )
            }
        }

        Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
            // 群聊中非自己发送的消息，展示昵称与等级徽章
            if (!isMe && isGroup) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
                ) {
                    Text(
                        text = message.displayName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = DeepTheme.extended.textSecondary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "Lv.${message.senderLevel}",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // 气泡本体 + 长按弹出操作条
            Box {
                val bubbleInteractionSource = remember { MutableInteractionSource() }
                Column(
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (isMe) 16.dp else 4.dp,
                                bottomEnd = if (isMe) 4.dp else 16.dp
                            )
                        )
                        .background(
                            if (isMe) MaterialTheme.colorScheme.primary else DeepTheme.extended.surfaceElevated
                        )
                        .combinedClickable(
                            interactionSource = bubbleInteractionSource,
                            indication = null,
                            onClick = {},
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showMenu = true
                            }
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    // 若有被引用的回复快照（智能折叠图片，绝不暴露裸露地址）
                    val replySnippetFormatted = remember(message.replySnippet) {
                        ChatContentParser.formatReplySnippet(message.replySnippet)
                    }
                    if (replySnippetFormatted.isNotBlank() || message.replySender.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isMe) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface)
                                .clickable {
                                    message.effectiveReplyId?.let { onQuoteClick(it) }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "“${message.replySender}: $replySnippetFormatted”",
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isMe) Color.White.copy(alpha = 0.8f) else DeepTheme.extended.textTertiary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // 1. 摇骰子结果卡片（物理 3D 滚动动画与象牙骨质感）
                    if (parsed.isDice) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp)
                        ) {
                            DiceCube(
                                value = parsed.dicePoints ?: 1,
                                size = 48.dp,
                                animate = true
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "掷出 ${parsed.dicePoints ?: 1} 点",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "服务端随机下发 · 点击可重掷",
                                    fontSize = 10.sp,
                                    color = if (isMe) Color.White.copy(alpha = 0.7f) else DeepTheme.extended.textTertiary
                                )
                            }
                        }
                    } else if (parsed.isCheckin) {
                        // 2. 每日群签到卡片
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "每日签到 · ${parsed.pureText}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "群等级经验已到账",
                                    fontSize = 9.sp,
                                    color = if (isMe) Color.White.copy(alpha = 0.7f) else DeepTheme.extended.textTertiary
                                )
                            }
                        }
                    } else {
                        // 3. 正常图文混排或纯图消息
                        // 若有文本部分：
                        if (parsed.pureText.isNotBlank()) {
                            val content = parsed.pureText
                            val urlPattern = remember { Pattern.compile("https?://[\\w-]+(\\.[\\w-]+)+(/[\\w-./?%&=#@:;+~]*)?") }
                            val hasUrl = remember(content) { urlPattern.matcher(content).find() }
                            val linkColor = if (isMe) Color.White else MaterialTheme.colorScheme.primary

                            if (hasUrl) {
                                val annotatedString = remember(content, linkColor) {
                                    buildAnnotatedString {
                                        val m = urlPattern.matcher(content)
                                        var lastIndex = 0
                                        while (m.find()) {
                                            val start = m.start()
                                            val end = m.end()
                                            append(content.substring(lastIndex, start))
                                            val url = m.group()
                                            pushStringAnnotation(tag = "URL", annotation = url)
                                            withStyle(
                                                style = SpanStyle(
                                                    color = linkColor,
                                                    textDecoration = TextDecoration.Underline,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            ) {
                                                append(url)
                                            }
                                            pop()
                                            lastIndex = end
                                        }
                                        if (lastIndex < content.length) {
                                            append(content.substring(lastIndex))
                                        }
                                    }
                                }

                                ClickableText(
                                    text = annotatedString,
                                    style = LocalTextStyle.current.copy(
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp,
                                        color = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    ),
                                    onClick = { offset ->
                                        annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                                            .firstOrNull()?.let { annotation ->
                                                try {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item)).apply {
                                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                    }
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
                                    }
                                )
                            } else {
                                Text(
                                    text = content,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    color = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // 若包含图片：分离渲染为缩略图卡片（绝不在文字中裸露 URL）
                        if (parsed.imageUrls.isNotEmpty()) {
                            if (parsed.pureText.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            parsed.imageUrls.forEach { rawUrl ->
                                val fullUrl = ChatContentParser.toFullImageUrl(rawUrl)
                                AsyncImage(
                                    model = fullUrl,
                                    contentDescription = "图片",
                                    modifier = Modifier
                                        .widthIn(max = if (parsed.isPureImage) 200.dp else 180.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onImageClick(fullUrl) }
                                        .padding(vertical = 2.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }
                }

                // 高质感长按浮动操作条
                if (showMenu) {
                    Popup(
                        alignment = if (isMe) Alignment.TopEnd else Alignment.TopStart,
                        onDismissRequest = { showMenu = false },
                        properties = PopupProperties(focusable = true)
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(0.8.dp, DeepTheme.extended.border, RoundedCornerShape(14.dp))
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // 回复
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        showMenu = false
                                        onReplyClick()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Reply,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("回复", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            }

                            if (parsed.pureText.isNotBlank()) {
                                // 复制文字
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            showMenu = false
                                            onCopyClick()
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("复制", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }

                            // 收藏表情（如果含有图片）
                            if (parsed.imageUrls.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            showMenu = false
                                            onCollectSticker(parsed.imageUrls.first())
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("⭐️", fontSize = 12.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("存为表情", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }

                            if (isMe) {
                                // 撤回
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            showMenu = false
                                            onRecallClick()
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Undo,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("撤回", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupInfoSheetContent(
    roomTitle: String,
    announcement: String,
    members: List<GroupMemberDto>,
    isLoading: Boolean,
    myRole: String?,
    currentUserId: String?,
    groupCheckinStatus: GroupCheckinStatusDto? = null,
    onMemberClick: (GroupMemberDto) -> Unit,
    onKickMember: (String) -> Unit,
    onUpdateRole: (String, String) -> Unit,
    onMuteMember: (String, Int) -> Unit = { _, _ -> },
    onUnmuteMember: (String) -> Unit = {},
    onOpenRequests: () -> Unit = {},
    onLeaveGroup: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var selectedMemberForAction by remember { mutableStateOf<GroupMemberDto?>(null) }
    var showLeaveConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            text = roomTitle,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 群专属签到与等级进度卡片
        if (groupCheckinStatus != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF10B981).copy(alpha = 0.12f))
                    .border(1.dp, Color(0xFF10B981).copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "⚡ 群等级 Lv.${groupCheckinStatus.groupLevel}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "EXP: ${groupCheckinStatus.groupExp}", fontSize = 11.sp, color = DeepTheme.extended.textSecondary)
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "发送「${groupCheckinStatus.checkinWord}」自动打卡获取群经验",
                        fontSize = 11.sp,
                        color = DeepTheme.extended.textTertiary
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (groupCheckinStatus.isCheckedIn) Color(0xFF10B981).copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (groupCheckinStatus.isCheckedIn) "今日已签" else "未签到",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (groupCheckinStatus.isCheckedIn) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // 群公告
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DeepTheme.extended.surfaceElevated)
                .padding(12.dp)
        ) {
            Text(
                text = "群公告",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = announcement.ifBlank { "群主暂未设置群公告" },
                fontSize = 12.sp,
                color = DeepTheme.extended.textSecondary
            )
        }

        // 管理员入群审批中心入口
        if (myRole == "owner" || myRole == "admin") {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DeepTheme.extended.surfaceElevated)
                    .border(0.8.dp, DeepTheme.extended.border, RoundedCornerShape(12.dp))
                    .clickable { onOpenRequests() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "📋", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "入群申请审批中心",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "查看待审批 ➔",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "群成员 (${members.size}人)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (myRole != null) {
                Text(
                    text = when (myRole) {
                        "owner" -> "我的身份: 群主"
                        "admin" -> "我的身份: 管理员"
                        else -> "我的身份: 成员"
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Text(
            text = "提示：长按头像可快捷 @ 对方",
            fontSize = 11.sp,
            color = DeepTheme.extended.textTertiary,
            modifier = Modifier.padding(top = 2.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(members, key = { it.userId }) { member ->
                    val memberInteractionSource = remember { MutableInteractionSource() }
                    val isMemberPressed by memberInteractionSource.collectIsPressedAsState()
                    val memberScale by animateFloatAsState(
                        targetValue = if (isMemberPressed) 0.93f else 1f,
                        animationSpec = DeepMotion.bouncy(),
                        label = "member_item_scale"
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .scale(memberScale)
                            .clip(RoundedCornerShape(10.dp))
                            .combinedClickable(
                                interactionSource = memberInteractionSource,
                                indication = null,
                                onClick = {
                                    if (myRole in listOf("owner", "admin") && member.userId != currentUserId) {
                                        selectedMemberForAction = member
                                    }
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onMemberClick(member)
                                }
                            )
                            .padding(4.dp)
                    ) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            UserAvatar(
                                avatarUrl = member.finalAvatarUrl,
                                displayName = member.displayName,
                                size = 40.dp
                            )
                            if (member.role in listOf("owner", "admin")) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (member.role == "owner") AccentLike else MaterialTheme.colorScheme.primary)
                                        .padding(horizontal = 3.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = if (member.role == "owner") "主" else "管",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = member.displayName,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 退出群聊按钮
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                .clickable { showLeaveConfirm = true },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "退出该群聊",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }

    // 成员管理弹窗
    selectedMemberForAction?.let { member ->
        val isOwner = myRole == "owner"
        AlertDialog(
            onDismissRequest = { selectedMemberForAction = null },
            title = {
                val atName = member.username.takeIf { it.isNotBlank() } ?: member.displayName
                Text("成员管理: @$atName")
            },
            text = { Text("角色: ${if (member.role == "owner") "群主" else if (member.role == "admin") "管理员" else "普通成员"}") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targetId = member.userId
                        selectedMemberForAction = null
                        onKickMember(targetId)
                    }
                ) {
                    Text("移出群聊", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                Row {
                    if (isOwner && member.role != "owner") {
                        val nextRole = if (member.role == "admin") "member" else "admin"
                        TextButton(
                            onClick = {
                                val targetId = member.userId
                                selectedMemberForAction = null
                                onUpdateRole(targetId, nextRole)
                            }
                        ) {
                            Text(if (member.role == "admin") "撤销管理" else "设为管理")
                        }
                    }
                    if (member.role != "owner") {
                        TextButton(
                            onClick = {
                                val targetId = member.userId
                                selectedMemberForAction = null
                                onMuteMember(targetId, 60)
                            }
                        ) {
                            Text("禁言1h")
                        }
                    }
                    TextButton(
                        onClick = {
                            selectedMemberForAction = null
                            onMemberClick(member)
                        }
                    ) {
                        Text("@Ta")
                    }
                }
            }
        )
    }

    // 退出群聊确认
    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text("退出群聊") },
            text = { Text("确定要退出群聊「$roomTitle」吗？退出后将无法接收该群的后续消息。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLeaveConfirm = false
                        onLeaveGroup()
                    }
                ) {
                    Text("确定退出", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}
