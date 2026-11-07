package top.deepcommunity.app.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mood
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
import top.deepcommunity.app.core.common.Constants
import top.deepcommunity.app.core.designsystem.component.GlassBox
import top.deepcommunity.app.core.designsystem.component.ImageViewerDialog
import top.deepcommunity.app.core.designsystem.component.UserAvatar
import top.deepcommunity.app.core.designsystem.theme.AccentLike
import top.deepcommunity.app.core.designsystem.theme.DeepTheme
import top.deepcommunity.app.data.model.ChatMessageDto
import top.deepcommunity.app.data.model.GroupMemberDto
import top.deepcommunity.app.data.model.StickerItemDto

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
            }
        }
    }

    // 默认打开时/加载完成时立即精准定位到最新消息底部
    LaunchedEffect(uiState.messages.size, uiState.isLoading) {
        if (uiState.messages.isNotEmpty()) {
            listState.scrollToItem(uiState.messages.size - 1)
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
                // 引用回复预览横条
                AnimatedVisibility(
                    visible = uiState.replyingToMessage != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    val replyMsg = uiState.replyingToMessage
                    if (replyMsg != null) {
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
                                Text(
                                    text = "回复 @${replyMsg.displayName}: ${replyMsg.content.take(30)}",
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
                        onStickerClick = viewModel::sendSticker
                    )
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
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
                    onImageClick = { previewChatImageUrl = it }
                )
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
                    onMemberClick = { member ->
                        val atUsername = member.username.takeIf { it.isNotBlank() } ?: member.displayName
                        viewModel.appendText("@$atUsername ")
                        viewModel.toggleGroupInfoSheet(false)
                    },
                    onKickMember = viewModel::kickMember,
                    onUpdateRole = viewModel::updateMemberRole,
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
    }
}

@Composable
private fun EnhancedStickerDrawer(
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    onEmojiClick: (String) -> Unit,
    stickers: List<StickerItemDto>,
    isLoadingStickers: Boolean,
    onStickerClick: (StickerItemDto) -> Unit
) {
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
                                .clickable { onStickerClick(sticker) }
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
    onImageClick: (String) -> Unit = {}
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
    val isImage = message.msgType == "image" ||
            message.content.startsWith("/uploads/") ||
            message.content.startsWith("http://") ||
            message.content.startsWith("https://")

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
        // 群聊中非自己发送的消息，展示头像与昵称（长按头像可快捷 @，丝滑物理微缩放 + 触感震动，去除原生方块水波纹）
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
                        onClick = {},
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

            // 气泡本体 + 长按弹出高质感液态操作条（无方形水波纹，物理触感反馈）
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
                    // 若有被引用的回复快照（点击平滑定位跳转）
                    if (message.replySnippet.isNotBlank() || message.replySender.isNotBlank()) {
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
                                text = "“${message.replySender}: ${message.replySnippet}”",
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isMe) Color.White.copy(alpha = 0.8f) else DeepTheme.extended.textTertiary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    if (isImage) {
                        val fullUrl = if (!message.content.startsWith("http")) {
                            Constants.BASE_URL.trimEnd('/') + "/" + message.content.trimStart('/')
                        } else {
                            message.content
                        }
                        AsyncImage(
                            model = fullUrl,
                            contentDescription = "图片消息",
                            modifier = Modifier
                                .widthIn(max = 200.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onImageClick(fullUrl) },
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text(
                            text = message.content,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // 高质感液态玻璃长按浮动药丸菜单
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

                            if (!isImage) {
                                // 复制
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
    onMemberClick: (GroupMemberDto) -> Unit,
    onKickMember: (String) -> Unit,
    onUpdateRole: (String, String) -> Unit,
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
            text = "💡 提示：长按头像才能 @ 对方",
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
