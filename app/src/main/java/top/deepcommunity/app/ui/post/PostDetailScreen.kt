package top.deepcommunity.app.ui.post

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.ui.graphics.Color
import top.deepcommunity.app.data.model.StickerItemDto
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import top.deepcommunity.app.ui.post.component.PostSharePosterDialog
import top.deepcommunity.app.ui.post.component.ReportDialog
import top.deepcommunity.app.ui.profile.UserProfileBottomSheet
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import top.deepcommunity.app.core.common.Constants
import top.deepcommunity.app.core.designsystem.component.GlassBox
import top.deepcommunity.app.core.designsystem.component.ImageViewerDialog
import top.deepcommunity.app.core.designsystem.component.MarkdownArticleContent
import top.deepcommunity.app.core.designsystem.component.UserAvatar
import top.deepcommunity.app.core.designsystem.theme.AccentCollect
import top.deepcommunity.app.core.designsystem.theme.AccentLike
import top.deepcommunity.app.core.designsystem.theme.DeepMotion
import top.deepcommunity.app.core.designsystem.theme.DeepTheme
import top.deepcommunity.app.data.model.CommentDto
import top.deepcommunity.app.data.model.PollDetailDto

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PostDetailScreen(
    onBackClick: () -> Unit,
    viewModel: PostDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    var showSharePosterDialog by remember { mutableStateOf(false) }
    var showReportPostDialog by remember { mutableStateOf(false) }
    var reportCommentTarget by remember { mutableStateOf<Pair<Long, String>?>(null) } // id to content
    var selectedUserId by remember { mutableStateOf<Long?>(null) }

    // 评论区相册选图上传
    val commentImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.uploadCommentImage(uri)
        }
    }

    LaunchedEffect(key1 = true) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is PostDetailEvent.ShowToast -> snackbarHostState.showSnackbar(event.message)
            }
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
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
                        text = "动态详情",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isCollected = uiState.post?.isCollected == true
                    // 收藏动态
                    IconButton(onClick = viewModel::toggleCollect) {
                        Icon(
                            imageVector = if (isCollected) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = "收藏",
                            tint = if (isCollected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                        )
                    }

                    // 生成极客长图海报分享
                    IconButton(onClick = { showSharePosterDialog = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = "生成极客海报分享",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    // 举报帖子
                    IconButton(onClick = { showReportPostDialog = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Flag,
                            contentDescription = "举报帖子",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        },
        bottomBar = {
            // 底部液态玻璃评论输入条
            GlassBox(
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 回复指示提示条
                    AnimatedVisibility(
                        visible = uiState.replyingToAuthorName != null,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "正在回复 @${uiState.replyingToAuthorName}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(
                                onClick = viewModel::cancelReply,
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

                    // 选中的表情包/图片预览条与上传状态
                    AnimatedVisibility(
                        visible = uiState.attachedImageUrl != null || uiState.isUploadingCommentImage,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Box(modifier = Modifier.padding(start = 16.dp, top = 6.dp)) {
                            if (uiState.isUploadingCommentImage) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DeepTheme.extended.surfaceElevated)
                                        .border(1.dp, DeepTheme.extended.border, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                                }
                            } else {
                                val rawUrl = uiState.attachedImageUrl ?: ""
                                val fullUrl = if (!rawUrl.startsWith("http")) Constants.BASE_URL.trimEnd('/') + "/" + rawUrl.trimStart('/') else rawUrl
                                AsyncImage(
                                    model = fullUrl,
                                    contentDescription = "评论配图",
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, DeepTheme.extended.border, RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.6f))
                                        .clickable(onClick = viewModel::removeAttachedImage),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "移除", tint = Color.White, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 表情包按钮
                        IconButton(
                            onClick = { viewModel.toggleStickerPanel() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Mood,
                                contentDescription = "表情",
                                tint = if (uiState.showStickerPanel) MaterialTheme.colorScheme.primary else DeepTheme.extended.textSecondary
                            )
                        }

                        // 相册选图按钮
                        IconButton(
                            onClick = {
                                commentImagePickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Image,
                                contentDescription = "发图",
                                tint = DeepTheme.extended.textSecondary
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        OutlinedTextField(
                            value = uiState.commentInput,
                            onValueChange = viewModel::onCommentInputChange,
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(
                                    text = if (uiState.replyingToAuthorName != null) "写下对 @${uiState.replyingToAuthorName} 的回复…" else "撰写你的高见…",
                                    fontSize = 13.sp,
                                    color = DeepTheme.extended.textTertiary
                                )
                            },
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
                            onClick = viewModel::sendComment,
                            enabled = !uiState.isSendingComment && (uiState.commentInput.isNotBlank() || uiState.attachedImageUrl != null)
                        ) {
                            if (uiState.isSendingComment) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "发送",
                                    tint = if (uiState.commentInput.isNotBlank() || uiState.attachedImageUrl != null) MaterialTheme.colorScheme.primary else DeepTheme.extended.textTertiary
                                )
                            }
                        }
                    }

                    // 评论表情包展开面板
                    AnimatedVisibility(
                        visible = uiState.showStickerPanel,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        CommentStickerDrawer(
                            selectedTab = uiState.stickerTab,
                            onTabChange = viewModel::setStickerTab,
                            onEmojiClick = viewModel::appendEmoji,
                            stickers = uiState.stickers,
                            onStickerClick = viewModel::attachSticker
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading && uiState.post == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            val post = uiState.post
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                if (post != null) {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                            // 作者横条
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val authorName = post.authorNickname ?: post.authorUsername ?: post.author?.nickname ?: "居民"
                                val targetAuthorUsername = post.authorUsername?.takeIf { it.isNotBlank() } ?: authorName
                                val postAvatarInteractionSource = remember { MutableInteractionSource() }
                                val isPostAvatarPressed by postAvatarInteractionSource.collectIsPressedAsState()
                                val postAvatarScale by animateFloatAsState(
                                    targetValue = if (isPostAvatarPressed) 0.92f else 1f,
                                    animationSpec = DeepMotion.bouncy(),
                                    label = "post_avatar_press"
                                )

                                Box(
                                    modifier = Modifier
                                        .scale(postAvatarScale)
                                        .clip(CircleShape)
                                        .combinedClickable(
                                            interactionSource = postAvatarInteractionSource,
                                            indication = null,
                                            onClick = {
                                                post.effectiveAuthorId?.let { selectedUserId = it }
                                            },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.appendText("@$targetAuthorUsername ")
                                            }
                                        )
                                ) {
                                    UserAvatar(
                                        avatarUrl = post.finalAvatarUrl,
                                        displayName = authorName,
                                        size = 42.dp
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(
                                    modifier = Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        post.effectiveAuthorId?.let { selectedUserId = it }
                                    }
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = authorName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = "Lv.${post.authorLevel}",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Text(
                                        text = post.createdAt.take(16).replace("T", " "),
                                        fontSize = 11.sp,
                                        color = DeepTheme.extended.textTertiary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // 标题
                            Text(
                                text = post.title,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // 正文 Markdown / HTML 复合渲染
                            MarkdownArticleContent(
                                contentHtml = post.contentHtml,
                                contentMd = post.contentMd
                            )

                            // 附加相册九宫格图片展示
                            if (post.imageUrls.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(14.dp))
                                PostDetailImageGallery(
                                    images = post.imageUrls,
                                    onImageClick = { previewImageUrl = it }
                                )
                            }

                            // 随帖投票模块
                            if (uiState.poll != null) {
                                Spacer(modifier = Modifier.height(18.dp))
                                PollCard(
                                    poll = uiState.poll!!,
                                    selectedOptionIds = uiState.selectedOptionIds,
                                    isVoting = uiState.isVoting,
                                    onOptionClick = viewModel::toggleOptionSelect,
                                    onSubmitVote = viewModel::submitVote
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // 互动行（点赞、收藏）
                            val likeScale by animateFloatAsState(
                                targetValue = if (post.isLiked) 1.25f else 1f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                                label = "detail_like_scale"
                            )
                            val collectScale by animateFloatAsState(
                                targetValue = if (post.isCollected) 1.25f else 1f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                                label = "detail_collect_scale"
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(DeepTheme.extended.surfaceElevated)
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.toggleLike()
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (post.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                        contentDescription = "点赞",
                                        tint = if (post.isLiked) AccentLike else DeepTheme.extended.textTertiary,
                                        modifier = Modifier.scale(likeScale)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${post.likeCount} 赞",
                                        fontSize = 13.sp,
                                        color = if (post.isLiked) AccentLike else MaterialTheme.colorScheme.onBackground
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.toggleCollect()
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (post.isCollected) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                        contentDescription = "收藏",
                                        tint = if (post.isCollected) AccentCollect else DeepTheme.extended.textTertiary,
                                        modifier = Modifier.scale(collectScale)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (post.isCollected) "已收藏" else "收藏",
                                        fontSize = 13.sp,
                                        color = if (post.isCollected) AccentCollect else MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // 表情回应 Reactions 快捷药丸条
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(DeepTheme.extended.surfaceElevated.copy(alpha = 0.5f))
                                    .border(0.8.dp, DeepTheme.extended.border, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "态度回应",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DeepTheme.extended.textTertiary
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val emojis = listOf("👍", "❤️", "🚀", "💡", "🎉", "🔥")
                                    emojis.forEach { emoji ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surface)
                                                .clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    viewModel.toggleReaction(emoji)
                                                }
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = emoji, fontSize = 15.sp)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Text(
                                text = "评论 (${uiState.comments.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    // 评论列表
                    items(uiState.comments, key = { it.id }) { comment ->
                        CommentItem(
                            comment = comment,
                            onReplyClick = { commentId, authorName ->
                                viewModel.setReplyTo(commentId, authorName)
                            },
                            onCommentLikeClick = viewModel::toggleCommentLike,
                            onImageClick = { previewImageUrl = it },
                            onAvatarLongClick = { username ->
                                viewModel.appendText("@$username ")
                            },
                            onUserClick = { uId -> selectedUserId = uId },
                            onAcceptClick = { cId -> viewModel.acceptComment(cId) },
                            onFeatureClick = { cId -> viewModel.featureComment(cId) },
                            onReportClick = { cId, cText -> reportCommentTarget = cId to cText }
                        )
                    }
                }
            }
        }

        // 行业级全功能沉浸式大图浏览器（捏合缩放/双击放大/下拉阻尼退出/长按或一键保存到本地相册）
        ImageViewerDialog(
            imageUrl = previewImageUrl,
            onDismissRequest = { previewImageUrl = null }
        )

        // 极客动态高清海报生成与分享弹窗
        if (showSharePosterDialog) {
            PostSharePosterDialog(
                post = uiState.post,
                onDismissRequest = { showSharePosterDialog = false }
            )
        }

        // 举报帖子弹窗
        if (showReportPostDialog) {
            uiState.post?.let { p ->
                ReportDialog(
                    targetType = "POST",
                    targetId = p.id.toLongOrNull() ?: 0L,
                    targetTitle = p.title,
                    postRepository = viewModel.postRepo,
                    onDismissRequest = { showReportPostDialog = false }
                )
            }
        }

        // 举报评论弹窗
        reportCommentTarget?.let { (cId, cText) ->
            ReportDialog(
                targetType = "COMMENT",
                targetId = cId,
                targetTitle = cText.take(30),
                postRepository = viewModel.postRepo,
                onDismissRequest = { reportCommentTarget = null }
            )
        }

        // 他人个人主页
        selectedUserId?.let { uId ->
            UserProfileBottomSheet(
                userId = uId,
                userRepository = viewModel.userRepo,
                onDismissRequest = { selectedUserId = null }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommentItem(
    comment: CommentDto,
    onReplyClick: (String, String) -> Unit,
    onCommentLikeClick: (String) -> Unit,
    onImageClick: (String) -> Unit,
    onAvatarLongClick: (String) -> Unit,
    onUserClick: (Long) -> Unit,
    onAcceptClick: (Long) -> Unit,
    onFeatureClick: (Long) -> Unit,
    onReportClick: (Long, String) -> Unit
) {
    var isRepliesExpanded by remember { mutableStateOf(false) }
    var isMenuExpanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val authorName = comment.displayAuthorName
    val targetUsername = comment.authorUsername?.takeIf { it.isNotBlank() } ?: authorName

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val avatarInteractionSource = remember { MutableInteractionSource() }
            val isAvatarPressed by avatarInteractionSource.collectIsPressedAsState()
            val avatarScale by animateFloatAsState(
                targetValue = if (isAvatarPressed) 0.90f else 1f,
                animationSpec = DeepMotion.bouncy(),
                label = "comment_avatar_press"
            )

            // 圆形头像点击唤起他人主页，长按快捷 @
            Box(
                modifier = Modifier
                    .scale(avatarScale)
                    .clip(CircleShape)
                    .combinedClickable(
                        interactionSource = avatarInteractionSource,
                        indication = null,
                        onClick = {
                            comment.effectiveAuthorId?.let(onUserClick)
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAvatarLongClick(targetUsername)
                        }
                    )
            ) {
                UserAvatar(
                    avatarUrl = comment.displayAuthorAvatar,
                    displayName = authorName,
                    size = 30.dp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = authorName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (comment.isAccepted) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFFFB300).copy(alpha = 0.15f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "👑 最佳采纳",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF8F00)
                            )
                        }
                    }
                    if (comment.isFeatured) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF8E24AA).copy(alpha = 0.12f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "📌 作者置顶",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF8E24AA)
                            )
                        }
                    }
                }
                Text(
                    text = comment.createdAt.take(16).replace("T", " "),
                    fontSize = 10.sp,
                    color = DeepTheme.extended.textTertiary
                )
            }

            // 点赞按钮
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onCommentLikeClick(comment.id)
                    }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = if (comment.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "赞",
                    tint = if (comment.isLiked) AccentLike else DeepTheme.extended.textTertiary,
                    modifier = Modifier.size(14.dp)
                )
                if (comment.likeCount > 0) {
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "${comment.likeCount}",
                        fontSize = 11.sp,
                        color = if (comment.isLiked) AccentLike else DeepTheme.extended.textTertiary
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // 回复按钮
            Text(
                text = "回复",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onReplyClick(comment.id, targetUsername) }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )

            // 更多操作下拉菜单（采纳/置顶/举报）
            Box {
                IconButton(
                    onClick = { isMenuExpanded = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "操作",
                        tint = DeepTheme.extended.textTertiary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = isMenuExpanded,
                    onDismissRequest = { isMenuExpanded = false }
                ) {
                    val cIdLong = comment.id.toLongOrNull() ?: 0L
                    DropdownMenuItem(
                        text = { Text("👑 采纳为最佳答案", fontSize = 12.sp) },
                        onClick = {
                            isMenuExpanded = false
                            onAcceptClick(cIdLong)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("📌 精选置顶该评论", fontSize = 12.sp) },
                        onClick = {
                            isMenuExpanded = false
                            onFeatureClick(cIdLong)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("🚩 举报违规评论", fontSize = 12.sp) },
                        onClick = {
                            isMenuExpanded = false
                            onReportClick(cIdLong, comment.content)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (comment.content.isNotBlank()) {
            Text(
                text = comment.content,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 40.dp)
            )
        }

        // 评论附带表情包图片
        if (!comment.imageUrl.isNullOrBlank()) {
            val raw = comment.imageUrl
            val fullImgUrl = if (!raw.startsWith("http")) Constants.BASE_URL.trimEnd('/') + "/" + raw.trimStart('/') else raw
            Spacer(modifier = Modifier.height(6.dp))
            AsyncImage(
                model = fullImgUrl,
                contentDescription = "评论表情",
                modifier = Modifier
                    .padding(start = 40.dp)
                    .size(90.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onImageClick(fullImgUrl) },
                contentScale = ContentScale.Fit
            )
        }

        // 二级楼中楼树状回复列表
        if (comment.replies.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            val visibleReplies = if (isRepliesExpanded) comment.replies else comment.replies.take(2)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 32.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DeepTheme.extended.surfaceElevated.copy(alpha = 0.6f))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                visibleReplies.forEach { subComment ->
                    SubCommentItem(
                        subComment = subComment,
                        onReplyClick = onReplyClick,
                        onCommentLikeClick = onCommentLikeClick,
                        onImageClick = onImageClick,
                        onAvatarLongClick = onAvatarLongClick
                    )
                }

                if (comment.replies.size > 2) {
                    Text(
                        text = if (isRepliesExpanded) "收起回复 ▴" else "展开其余 ${comment.replies.size - 2} 条回复 ▾",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { isRepliesExpanded = !isRepliesExpanded }
                            .padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SubCommentItem(
    subComment: CommentDto,
    onReplyClick: (String, String) -> Unit,
    onCommentLikeClick: (String) -> Unit,
    onImageClick: (String) -> Unit,
    onAvatarLongClick: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val authorName = subComment.displayAuthorName
    val targetUsername = subComment.authorUsername?.takeIf { it.isNotBlank() } ?: authorName
    val avatarInteractionSource = remember { MutableInteractionSource() }
    val isAvatarPressed by avatarInteractionSource.collectIsPressedAsState()
    val avatarScale by animateFloatAsState(
        targetValue = if (isAvatarPressed) 0.90f else 1f,
        animationSpec = DeepMotion.bouncy(),
        label = "sub_avatar_press"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onReplyClick(subComment.id, targetUsername) },
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .scale(avatarScale)
                .clip(CircleShape)
                .combinedClickable(
                    interactionSource = avatarInteractionSource,
                    indication = null,
                    onClick = {},
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onAvatarLongClick(targetUsername)
                    }
                )
        ) {
            UserAvatar(
                avatarUrl = subComment.displayAuthorAvatar,
                displayName = authorName,
                size = 22.dp
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = authorName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 子评论点赞
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onCommentLikeClick(subComment.id)
                            }
                            .padding(end = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (subComment.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "赞",
                            tint = if (subComment.isLiked) AccentLike else DeepTheme.extended.textTertiary,
                            modifier = Modifier.size(12.dp)
                        )
                        if (subComment.likeCount > 0) {
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "${subComment.likeCount}",
                                fontSize = 9.sp,
                                color = if (subComment.isLiked) AccentLike else DeepTheme.extended.textTertiary
                            )
                        }
                    }

                    Text(
                        text = subComment.createdAt.take(10),
                        fontSize = 9.sp,
                        color = DeepTheme.extended.textTertiary
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            if (subComment.content.isNotBlank()) {
                Text(
                    text = subComment.content,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 子评论附带表情包图片
            if (!subComment.imageUrl.isNullOrBlank()) {
                val raw = subComment.imageUrl
                val fullImgUrl = if (!raw.startsWith("http")) Constants.BASE_URL.trimEnd('/') + "/" + raw.trimStart('/') else raw
                Spacer(modifier = Modifier.height(4.dp))
                AsyncImage(
                    model = fullImgUrl,
                    contentDescription = "子评论表情",
                    modifier = Modifier
                        .size(70.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onImageClick(fullImgUrl) },
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

@Composable
private fun PollCard(
    poll: PollDetailDto,
    selectedOptionIds: Set<Int>,
    isVoting: Boolean,
    onOptionClick: (Int) -> Unit,
    onSubmitVote: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DeepTheme.extended.surfaceElevated)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "随帖投票",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = if (poll.isMultiple) "最多可选 ${poll.maxChoices} 项" else "单选",
                fontSize = 11.sp,
                color = DeepTheme.extended.textTertiary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = poll.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 选项列表
        poll.options.forEach { option ->
            val isSelected = selectedOptionIds.contains(option.id)
            val showStats = poll.hasVoted || poll.isExpired

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        else MaterialTheme.colorScheme.surface
                    )
                    .clickable(enabled = !poll.hasVoted && !poll.isExpired) {
                        onOptionClick(option.id)
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
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
                            text = "${option.voteCount} 票 (${(option.percentage).toInt()}%)",
                            fontSize = 12.sp,
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
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (option.isVoted) MaterialTheme.colorScheme.primary else DeepTheme.extended.textTertiary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }

        // 提交投票按钮
        if (!poll.hasVoted && !poll.isExpired && selectedOptionIds.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(enabled = !isVoting, onClick = onSubmitVote),
                contentAlignment = Alignment.Center
            ) {
                if (isVoting) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(
                        text = "提交投票",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun PostDetailImageGallery(
    images: List<String>,
    onImageClick: (String) -> Unit
) {
    val displayImages = images.take(9)
    val columns = when {
        displayImages.size == 1 -> 1
        displayImages.size in 2..4 -> 2
        else -> 3
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        displayImages.chunked(columns).forEach { rowImages ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                rowImages.forEach { imgUrl ->
                    val fullUrl = if (!imgUrl.startsWith("http")) Constants.BASE_URL.trimEnd('/') + "/" + imgUrl.trimStart('/') else imgUrl
                    AsyncImage(
                        model = fullUrl,
                        contentDescription = "相册配图",
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(if (columns == 1) 1.6f else 1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DeepTheme.extended.surfaceElevated)
                            .clickable { onImageClick(fullUrl) },
                        contentScale = ContentScale.Crop
                    )
                }
                repeat(columns - rowImages.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private val COMMENT_EMOJIS = listOf(
    "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "😉", "😊", "😇",
    "🥰", "😍", "🤩", "😘", "😋", "😜", "🤪", "🤑", "🤗", "🤭", "🤫", "🤔",
    "🤐", "🤨", "😐", "😑", "😶", "😏", "😒", "🙄", "😬", "😌", "😴", "😷",
    "👍", "👎", "👊", "✊", "✌️", "👌", "🤌", "👏", "🙌", "🎉", "🔥", "❤️"
)

@Composable
private fun CommentStickerDrawer(
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    onEmojiClick: (String) -> Unit,
    stickers: List<StickerItemDto>,
    onStickerClick: (StickerItemDto) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Tab 栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val tabs = listOf("常用表情", "社区贴纸")
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else DeepTheme.extended.surfaceElevated)
                        .clickable { onTabChange(index) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else DeepTheme.extended.textSecondary
                    )
                }
            }
        }

        if (selectedTab == 0) {
            // Emoji 列表
            LazyVerticalGrid(
                columns = GridCells.Fixed(8),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(COMMENT_EMOJIS) { emoji ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onEmojiClick(emoji) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = emoji, fontSize = 22.sp)
                    }
                }
            }
        } else {
            // 自定义贴纸
            if (stickers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "暂无可用社区贴纸", fontSize = 12.sp, color = DeepTheme.extended.textTertiary)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(stickers, key = { it.id }) { sticker ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(DeepTheme.extended.surfaceElevated)
                                .clickable { onStickerClick(sticker) }
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = sticker.finalThumbUrl,
                                contentDescription = "贴纸",
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
