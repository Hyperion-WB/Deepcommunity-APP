package top.deepcommunity.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import top.deepcommunity.app.core.designsystem.component.ImageViewerDialog
import top.deepcommunity.app.ui.profile.UserProfileBottomSheet
import top.deepcommunity.app.ui.home.LeaderboardsBottomSheet
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import top.deepcommunity.app.core.common.Constants
import top.deepcommunity.app.core.designsystem.component.UserAvatar
import top.deepcommunity.app.core.designsystem.theme.AccentLike
import top.deepcommunity.app.core.designsystem.theme.DeepMotion
import top.deepcommunity.app.core.designsystem.theme.DeepTheme
import top.deepcommunity.app.data.model.CategoryDto
import top.deepcommunity.app.data.model.PostItemDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onPostClick: (String) -> Unit,
    onOpenDrawer: () -> Unit = {},
    refreshTrigger: Long = 0L,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    var showLeaderboards by remember { mutableStateOf(false) }
    var selectedAuthorId by remember { mutableStateOf<String?>(null) }
    var selectedAuthorUsername by remember { mutableStateOf<String?>(null) }

    // 监听外部刷新触发（如底栏主页按钮点击）
    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0L) {
            listState.animateScrollToItem(0)
            viewModel.loadPosts(isRefresh = true)
        }
    }

    // 自动触底加载下一页监听
    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItemsCount = listState.layoutInfo.totalItemsCount
            val lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItemsCount > 0 && lastVisibleItemIndex >= totalItemsCount - 3
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !uiState.isLoading && !uiState.isLoadingMore && !uiState.isEndReached) {
            viewModel.loadNextPage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶部 Header 状态（支持呼出侧边栏设置、搜索与全站榜单）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenDrawer) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "打开设置侧边栏",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(2.dp))
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            coroutineScope.launch {
                                listState.animateScrollToItem(0)
                                viewModel.loadPosts(isRefresh = true)
                            }
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "深度社区",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "DEEP COMMUNITY",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                        color = DeepTheme.extended.textTertiary
                    )
                }
            }

            // 右侧功能操作区：搜索与榜单
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 搜索按钮
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (uiState.isSearchActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else DeepTheme.extended.surfaceElevated
                        )
                        .border(
                            0.8.dp,
                            if (uiState.isSearchActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            else DeepTheme.extended.border,
                            CircleShape
                        )
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.toggleSearch()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (uiState.isSearchActive) Icons.Default.Close else Icons.Outlined.Search,
                        contentDescription = "搜索",
                        tint = if (uiState.isSearchActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // 榜单胶囊按钮
                Row(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(DeepTheme.extended.surfaceElevated)
                        .border(0.8.dp, DeepTheme.extended.border, RoundedCornerShape(18.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showLeaderboards = true
                        }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Leaderboard,
                        contentDescription = "全站榜单",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "榜单",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        // 次级功能栏：“推荐 / 关注” 专属动态流双 Tab 与刷新状态
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // “推荐 / 关注” 专属动态流双 Tab
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(DeepTheme.extended.surfaceElevated)
                    .border(0.8.dp, DeepTheme.extended.border, RoundedCornerShape(14.dp))
                    .padding(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isRecommend = uiState.feedType == "recommend"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (isRecommend) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.selectFeedType("recommend")
                        }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "推荐",
                        fontSize = 13.sp,
                        fontWeight = if (isRecommend) FontWeight.Bold else FontWeight.Medium,
                        color = if (isRecommend) MaterialTheme.colorScheme.onPrimary else DeepTheme.extended.textSecondary
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (!isRecommend) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.selectFeedType("following")
                        }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "关注",
                        fontSize = 13.sp,
                        fontWeight = if (!isRecommend) FontWeight.Bold else FontWeight.Medium,
                        color = if (!isRecommend) MaterialTheme.colorScheme.onPrimary else DeepTheme.extended.textSecondary
                    )
                }
            }

            // 刷新指示器 / 点击刷新
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        coroutineScope.launch {
                            listState.animateScrollToItem(0)
                            viewModel.loadPosts(isRefresh = true)
                        }
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(13.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                }
                Text(
                    text = if (uiState.isLoading) "刷新中…" else "点击刷新",
                    fontSize = 11.sp,
                    color = DeepTheme.extended.textTertiary
                )
            }
        }

        // 展开实时搜索输入条与历史/推荐
        AnimatedVisibility(visible = uiState.isSearchActive) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索文章、日常、关键词…", fontSize = 13.sp, color = DeepTheme.extended.textTertiary) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.commitSearch(uiState.searchQuery) }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = DeepTheme.extended.border,
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                    )
                )

                // 搜索建议与历史区域（当输入为空时展示）
                if (uiState.searchQuery.isEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))

                    // 1. 搜索历史
                    if (searchHistory.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.History,
                                    contentDescription = null,
                                    tint = DeepTheme.extended.textSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "搜索历史",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DeepTheme.extended.textSecondary
                                )
                            }
                            IconButton(
                                onClick = viewModel::clearSearchHistory,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = "清空历史",
                                    tint = DeepTheme.extended.textTertiary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            searchHistory.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DeepTheme.extended.surfaceElevated)
                                        .border(1.dp, DeepTheme.extended.border, RoundedCornerShape(8.dp))
                                        .clickable { viewModel.commitSearch(item) }
                                        .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { viewModel.removeSearchHistory(item) },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "删除",
                                            tint = DeepTheme.extended.textTertiary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                    }

                }
            }
        }

        // 全站系统广播公告胶囊横幅
        var isAnnouncementDismissed by remember { mutableStateOf(false) }
        val ann = uiState.announcement
        if (ann != null && ann.content.isNotBlank() && !isAnnouncementDismissed) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    .border(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Campaign,
                        contentDescription = "系统公告",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = ann.content,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = { isAnnouncementDismissed = true },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭公告",
                        tint = DeepTheme.extended.textTertiary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // 排序筛选与分类下拉胶囊栏
        FilterChipsRow(
            selectedFilter = uiState.selectedFilter,
            onFilterSelected = viewModel::selectFilter,
            categories = uiState.categories,
            selectedCategoryId = uiState.selectedCategoryId,
            onCategorySelected = viewModel::selectCategory
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 帖子流展示区
        if (uiState.isLoading && uiState.posts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp, start = 16.dp, end = 16.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (uiState.posts.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Outlined.Article,
                                    contentDescription = null,
                                    tint = DeepTheme.extended.textTertiary,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = uiState.errorMessage ?: "暂无相关帖子",
                                    fontSize = 13.sp,
                                    color = DeepTheme.extended.textTertiary
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "刷新重试",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                        .border(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                        .clickable { viewModel.loadPosts(isRefresh = true) }
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                } else {
                    items(uiState.posts, key = { it.id }) { post ->
                        PostCard(
                            post = post,
                            onClick = { onPostClick(post.id) },
                            onLikeClick = { viewModel.toggleLike(post.id) },
                            onImageClick = { previewImageUrl = it },
                            onAuthorClick = { uName, uId ->
                                selectedAuthorUsername = uName
                                selectedAuthorId = uId
                            }
                        )
                    }
                }

                if (uiState.isLoadingMore) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else if (uiState.isEndReached && uiState.posts.isNotEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "—— 已加载全部动态 ——",
                                fontSize = 11.sp,
                                color = DeepTheme.extended.textTertiary
                            )
                        }
                    }
                }
            }
        }

        // 沉浸式专业看图弹层（双指捏合缩放/双击放大/下拉退出/一键保存相册）
        ImageViewerDialog(
            imageUrl = previewImageUrl,
            onDismissRequest = { previewImageUrl = null }
        )

        // 全站排行榜与打卡绿格子热力图半屏抽屉（帖子为重，抽屉唤起）
        if (showLeaderboards) {
            LeaderboardsBottomSheet(
                postRepository = viewModel.postRepo,
                userRepository = viewModel.userRepo,
                onDismissRequest = { showLeaderboards = false },
                onUserClick = { userIdent ->
                    showLeaderboards = false
                    selectedAuthorUsername = userIdent
                }
            )
        }

        // 他人个人主页半屏弹窗
        (selectedAuthorUsername ?: selectedAuthorId)?.let { ident ->
            UserProfileBottomSheet(
                username = selectedAuthorUsername,
                userId = selectedAuthorId,
                userRepository = viewModel.userRepo,
                onDismissRequest = {
                    selectedAuthorId = null
                    selectedAuthorUsername = null
                }
            )
        }
    }
}

@Composable
private fun FilterChipsRow(
    selectedFilter: String,
    onFilterSelected: (String) -> Unit,
    categories: List<CategoryDto>,
    selectedCategoryId: Int?,
    onCategorySelected: (Int?) -> Unit
) {
    val filters = listOf(
        "latest" to "最新动态",
        "hot" to "24H 热门",
        "comments" to "讨论最多"
    )

    var isCategoryDropdownExpanded by remember { mutableStateOf(false) }
    val currentSelectedCategoryName = remember(categories, selectedCategoryId) {
        if (selectedCategoryId == null) null else categories.find { it.id == selectedCategoryId }?.name
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(filters) { (key, label) ->
            val isSelected = selectedFilter == key
            val bgColor = if (isSelected) MaterialTheme.colorScheme.primary else DeepTheme.extended.surfaceElevated
            val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else DeepTheme.extended.textTertiary

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(bgColor)
                    .clickable { onFilterSelected(key) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = textColor
                )
            }
        }

        // 帖子分类下拉菜单胶囊（紧挨在“讨论最多”旁边）
        item {
            val isCategoryActive = selectedCategoryId != null
            val catBgColor = if (isCategoryActive) MaterialTheme.colorScheme.primary else DeepTheme.extended.surfaceElevated
            val catTextColor = if (isCategoryActive) MaterialTheme.colorScheme.onPrimary else DeepTheme.extended.textTertiary

            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(catBgColor)
                        .clickable { isCategoryDropdownExpanded = true }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (currentSelectedCategoryName != null) "分类: $currentSelectedCategoryName" else "帖子分类",
                        fontSize = 12.sp,
                        fontWeight = if (isCategoryActive) FontWeight.SemiBold else FontWeight.Normal,
                        color = catTextColor
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "选择分类",
                        tint = catTextColor,
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = isCategoryDropdownExpanded,
                    onDismissRequest = { isCategoryDropdownExpanded = false },
                    modifier = Modifier
                        .background(DeepTheme.extended.surfaceElevated)
                        .border(1.dp, DeepTheme.extended.border, RoundedCornerShape(12.dp))
                ) {
                    // 全部板块选项
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "全部板块",
                                    fontSize = 13.sp,
                                    fontWeight = if (selectedCategoryId == null) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedCategoryId == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                if (selectedCategoryId == null) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        onClick = {
                            isCategoryDropdownExpanded = false
                            onCategorySelected(null)
                        }
                    )

                    // 各个具体分类选项
                    categories.forEach { cat ->
                        val isCatSelected = selectedCategoryId == cat.id
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = cat.name,
                                        fontSize = 13.sp,
                                        fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isCatSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isCatSelected) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            onClick = {
                                isCategoryDropdownExpanded = false
                                onCategorySelected(cat.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

private val HTML_TAG_REGEX = Regex("<[^>]*>")

@Composable
fun PostCard(
    post: PostItemDto,
    onClick: () -> Unit,
    onLikeClick: () -> Unit,
    onImageClick: (String) -> Unit = {},
    onAuthorClick: ((username: String?, authorId: String?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(0.8.dp, DeepTheme.extended.border, RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(16.dp)
    ) {
        Column {
            // 作者信息行
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = onAuthorClick != null) {
                        onAuthorClick?.invoke(post.effectiveAuthorUsername, post.effectiveAuthorId)
                    }
            ) {
                val authorName = post.authorNickname ?: post.authorUsername ?: post.author?.nickname ?: "居民"
                UserAvatar(
                    avatarUrl = post.finalAvatarUrl,
                    displayName = authorName,
                    size = 38.dp
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = authorName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
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
                        text = post.createdAt.take(10),
                        fontSize = 11.sp,
                        color = DeepTheme.extended.textTertiary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 置顶 / 精选 / 分类标识行
            val hasBadge = post.isPinned || post.isEssence || !post.categoryName.isNullOrBlank()
            if (hasBadge) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (post.isPinned) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "置顶",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    if (post.isEssence) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFE5B842).copy(alpha = 0.16f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "精选",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD97706)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    if (!post.categoryName.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(DeepTheme.extended.surfaceElevated)
                                .border(0.6.dp, DeepTheme.extended.border, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = post.categoryName!!,
                                fontSize = 10.sp,
                                color = DeepTheme.extended.textSecondary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // 帖子标题
            Text(
                text = post.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // 正文摘要
            val contentSnippet = post.contentMd ?: post.contentHtml
            if (!contentSnippet.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                val cleanSnippet = remember(contentSnippet) {
                    contentSnippet.replace(HTML_TAG_REGEX, "").take(120)
                }
                Text(
                    text = cleanSnippet,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 九宫格相册网格或单图封面（支持点击大图预览）
            val singleImg = post.coverImage?.takeIf { it.isNotBlank() } ?: post.displayImage?.takeIf { it.isNotBlank() }
            if (!post.imageUrls.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                PostImageGrid(
                    images = post.imageUrls!!,
                    onImageClick = onImageClick
                )
            } else if (singleImg != null) {
                Spacer(modifier = Modifier.height(10.dp))
                val fullUrl = if (singleImg.startsWith("http")) singleImg else Constants.BASE_URL.removeSuffix("/") + if (singleImg.startsWith("/")) singleImg else "/$singleImg"
                AsyncImage(
                    model = fullUrl,
                    contentDescription = "封面图",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onImageClick(fullUrl) },
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 底部点赞、评论、浏览交互行
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val heartScale by animateFloatAsState(
                    targetValue = if (post.isLiked) 1.25f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "heart_anim"
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onLikeClick
                    )
                ) {
                    Icon(
                        imageVector = if (post.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "点赞",
                        tint = if (post.isLiked) AccentLike else DeepTheme.extended.textTertiary,
                        modifier = Modifier
                            .scale(heartScale)
                            .size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = post.likeCount.toString(),
                        fontSize = 12.sp,
                        color = if (post.isLiked) AccentLike else DeepTheme.extended.textTertiary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = "评论",
                        tint = DeepTheme.extended.textTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = post.commentCount.toString(),
                        fontSize = 12.sp,
                        color = DeepTheme.extended.textTertiary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Visibility,
                        contentDescription = "浏览",
                        tint = DeepTheme.extended.textTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = post.viewCount.toString(),
                        fontSize = 12.sp,
                        color = DeepTheme.extended.textTertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun PostImageGrid(
    images: List<String>,
    onImageClick: (String) -> Unit
) {
    val displayImages = images.take(9)
    val columns = when {
        displayImages.size == 1 -> 1
        displayImages.size in 2..4 -> 2
        else -> 3
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        displayImages.chunked(columns).forEach { rowImages ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                rowImages.forEach { imgUrl ->
                    val fullUrl = if (imgUrl.startsWith("http://") || imgUrl.startsWith("https://")) imgUrl
                        else Constants.BASE_URL.trimEnd('/') + "/" + imgUrl.trimStart('/')
                    AsyncImage(
                        model = fullUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(if (columns == 1) 1.6f else 1f)
                            .clip(RoundedCornerShape(8.dp))
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
