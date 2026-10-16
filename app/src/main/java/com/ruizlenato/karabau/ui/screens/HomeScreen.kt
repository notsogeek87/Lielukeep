package com.ruizlenato.karabau.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.runtime.derivedStateOf
import com.ruizlenato.karabau.ui.viewmodel.ActionFailure
import com.ruizlenato.karabau.ui.viewmodel.SyncFailure
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ruizlenato.karabau.data.links.LinkFilter
import com.ruizlenato.karabau.data.links.folderChips
import com.ruizlenato.karabau.data.links.foldersOf
import com.ruizlenato.karabau.data.links.isReadLater
import com.ruizlenato.karabau.data.model.BookmarkItem
import com.ruizlenato.karabau.ui.util.copyToClipboard
import com.ruizlenato.karabau.ui.util.openLink
import com.ruizlenato.karabau.ui.util.shareText
import com.ruizlenato.karabau.data.local.SettingsDataStore
import com.ruizlenato.karabau.data.model.SavedListItem
import com.ruizlenato.karabau.data.model.Settings
import com.ruizlenato.karabau.ui.viewmodel.HomeViewModel
import com.ruizlenato.karabau.R
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.core.net.toUri

private data class HomeDestination(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val homeDestinations = listOf(
    HomeDestination(
        labelRes = R.string.home_tab,
        selectedIcon = Icons.Default.Home,
        unselectedIcon = Icons.Outlined.Home
    ),
    HomeDestination(
        labelRes = R.string.lists_tab,
        selectedIcon = Icons.AutoMirrored.Filled.FormatListBulleted,
        unselectedIcon = Icons.AutoMirrored.Outlined.FormatListBulleted
    ),
    HomeDestination(
        labelRes = R.string.tags_tab,
        selectedIcon = Icons.Default.LocalOffer,
        unselectedIcon = Icons.Outlined.LocalOffer
    )
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun HomeScreen(
    onLogout: () -> Unit,
    onAddBookmark: () -> Unit = {},
    onManageCustomHeaders: () -> Unit = {},
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: androidx.compose.animation.AnimatedContentScope,
    savedStateHandle: androidx.lifecycle.SavedStateHandle? = null
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val homeViewModel: HomeViewModel = viewModel()
    val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val displayedBookmarks by homeViewModel.displayedBookmarks.collectAsStateWithLifecycle()
    var selectedBookmark by remember { mutableStateOf<BookmarkItem?>(null) }
    var editingBookmark by remember { mutableStateOf<BookmarkItem?>(null) }
    val context = LocalContext.current
    val settingsDataStore = remember(context) { SettingsDataStore(context.applicationContext) }
    val currentSettings by settingsDataStore.settingsFlow.collectAsStateWithLifecycle(initialValue = Settings())
    val snackBarHostState = remember { SnackbarHostState() }
    val snackBarScope = rememberCoroutineScope()
    val bookmarkDeletedMessage = stringResource(R.string.link_deleted)
    val undoActionLabel = stringResource(R.string.action_undo)
    val favouriteAddedMessage = stringResource(R.string.link_favourite_added)
    val favouriteRemovedMessage = stringResource(R.string.link_favourite_removed)
    val archivedMessage = stringResource(R.string.link_archived)
    val readLaterAddedMessage = stringResource(R.string.link_read_later_added)
    val readLaterRemovedMessage = stringResource(R.string.link_read_later_removed)
    val copiedMessage = stringResource(R.string.link_copied)
    val updatedMessage = stringResource(R.string.link_updated)
    val clipboardLabel = stringResource(R.string.link_clipboard_label)
    val failedNetworkMessage = stringResource(R.string.action_failed_network)
    val failedServerTemplate = stringResource(R.string.action_failed_server)
    val wide = isWideScreen()
    val listState = rememberLazyGridState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    fun dismissSelectedBookmark() {
        selectedBookmark = null
    }

    fun showMessage(message: String, undo: (() -> Unit)? = null) {
        snackBarScope.launch {
            snackBarHostState.currentSnackbarData?.dismiss()
            val result = snackBarHostState.showSnackbar(
                message = message,
                actionLabel = undo?.let { undoActionLabel },
                withDismissAction = true
            )
            if (result == SnackbarResult.ActionPerformed) undo?.invoke()
        }
    }

    val onActionFailed: ActionFailure = { isNetwork, detail ->
        showMessage(
            if (isNetwork) failedNetworkMessage else String.format(failedServerTemplate, detail.take(120))
        )
    }

    fun deleteWithUndo(item: BookmarkItem) {
        dismissSelectedBookmark()
        homeViewModel.deleteBookmarkWithUndo(item, onActionFailed)
        showMessage(bookmarkDeletedMessage) { homeViewModel.undoDeleteBookmark(item) }
    }

    val linkActions = LinkActions(
        // Un lien sans URL (note, fichier) n'a rien à ouvrir dans un onglet : on montre sa fiche.
        onOpen = { item ->
            val url = item.linkUrl
            if (url.isNullOrBlank()) selectedBookmark = item else openLink(context, url, currentSettings.openLinksInApp)
        },
        onShowDetails = { selectedBookmark = it },
        onEdit = { editingBookmark = it },
        onToggleFavourite = { item ->
            homeViewModel.toggleBookmarkFavourite(item, onActionFailed) { updated ->
                showMessage(if (updated.favourited) favouriteAddedMessage else favouriteRemovedMessage) {
                    homeViewModel.toggleBookmarkFavourite(updated, onActionFailed)
                }
            }
        },
        onToggleReadLater = { item ->
            homeViewModel.toggleBookmarkReadLater(item, onActionFailed) { updated ->
                showMessage(if (updated.isReadLater()) readLaterAddedMessage else readLaterRemovedMessage) {
                    homeViewModel.toggleBookmarkReadLater(updated, onActionFailed)
                }
            }
        },
        onCopy = { item ->
            copyToClipboard(context, clipboardLabel, item.linkUrl ?: item.title ?: item.id)
            showMessage(copiedMessage)
        },
        onShare = { item -> shareText(context, item.linkUrl ?: item.title ?: item.id, item.title) },
        onArchive = { item ->
            homeViewModel.toggleBookmarkArchived(item, onActionFailed) {
                showMessage(archivedMessage) { homeViewModel.unarchiveBookmark(item, onActionFailed) }
            }
        },
        onDelete = ::deleteWithUndo
    )

    fun updateSelectedBookmark(updated: BookmarkItem) {
        selectedBookmark = updated
    }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch { homeViewModel.loadSavedItems() }
            launch { homeViewModel.loadTags() }
            launch { homeViewModel.loadLists() }
        }
    }

    LaunchedEffect(homeUiState.hasCompletedInitialBookmarksLoad) {
        if (homeUiState.hasCompletedInitialBookmarksLoad) {
            homeViewModel.syncArchiveDisplayBehaviourAfterInitialLoad()
        }
    }

    LaunchedEffect(savedStateHandle) {
        savedStateHandle?.let { handle ->
            val flow = handle.getStateFlow("bookmark_created", false)
            flow.collect { created ->
                if (created) {
                    handle["bookmark_created"] = false
                    homeViewModel.refreshSavedItems()
                }
            }
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) {
            homeViewModel.loadLists()
        } else if (selectedTab == 2) {
            homeViewModel.loadTags()
        }
    }

    LaunchedEffect(homeUiState.profileImage, homeUiState.profileImageHeaders) {
        val profileImage = homeUiState.profileImage
        if (!profileImage.isNullOrBlank()) {
            context.imageLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(profileImage)
                    .apply {
                        homeUiState.profileImageHeaders.forEach { (key, value) ->
                            setHeader(key, value)
                        }
                    }
                    .build()
            )
        }
    }

    BackHandler(enabled = selectedTab == 0 && homeUiState.isSearchActive) {
        homeViewModel.onSearchActiveChange(false)
        homeViewModel.onSearchQueryChange("")
    }

    BackHandler(enabled = selectedTab == 3) {
        selectedTab = 0
    }

    BackHandler(enabled = selectedTab == 2 && homeUiState.selectedTag != null) {
        homeViewModel.closeTagDetail()
    }

    BackHandler(enabled = selectedTab == 1 && homeUiState.selectedList != null) {
        homeViewModel.closeListDetail()
    }

    AnimatedContent(
        targetState = selectedTab,
        transitionSpec = {
            val emphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
            val emphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

            when {
                targetState == 3 && initialState != 3 -> {
                    (slideInHorizontally(
                        initialOffsetX = { fullWidth -> (fullWidth * 0.30f).toInt() },
                        animationSpec = tween(durationMillis = 400, easing = emphasizedDecelerate)
                    ) + fadeIn(animationSpec = tween(200, delayMillis = 50, easing = LinearOutSlowInEasing)))
                        .togetherWith(
                            slideOutHorizontally(
                                targetOffsetX = { fullWidth -> -(fullWidth * 0.10f).toInt() },
                                animationSpec = tween(durationMillis = 350, easing = emphasizedAccelerate)
                            ) + fadeOut(animationSpec = tween(150, easing = FastOutLinearInEasing))
                        ).using(SizeTransform(clip = false))
                }

                initialState == 3 && targetState != 3 -> {
                    (slideInHorizontally(
                        initialOffsetX = { fullWidth -> -(fullWidth * 0.10f).toInt() },
                        animationSpec = tween(durationMillis = 400, easing = emphasizedDecelerate)
                    ) + fadeIn(animationSpec = tween(250, delayMillis = 100, easing = LinearOutSlowInEasing)))
                        .togetherWith(
                            slideOutHorizontally(
                                targetOffsetX = { fullWidth -> (fullWidth * 0.30f).toInt() },
                                animationSpec = tween(durationMillis = 350, easing = emphasizedAccelerate)
                            ) + fadeOut(animationSpec = tween(200, easing = FastOutLinearInEasing))
                        ).using(SizeTransform(clip = false))
                }

                else -> {
                    fadeIn(animationSpec = tween(120))
                        .togetherWith(fadeOut(animationSpec = tween(90)))
                }
            }
        },
        label = "mainTabTransition"
    ) { activeTab ->
        if (activeTab == 3) {
            ReadableWidth {
SettingsContent(
                onLogout = onLogout,
                onBack = { selectedTab = 0 },
                onCustomHeadersClick = onManageCustomHeaders
            )
}
        } else {
          Row(modifier = Modifier.fillMaxSize()) {
            // Le rail est à côté du Scaffold, pas dedans : il reste à la même place d'un onglet à l'autre,
            // que l'onglet ait une barre du haut (Accueil) ou non (Listes, Étiquettes).
            if (wide && !homeUiState.isSearchActive) {
                NavigationRail {
                    homeDestinations.forEachIndexed { index, destination ->
                        NavigationRailItem(
                            selected = activeTab == index,
                            onClick = { selectedTab = index },
                            icon = {
                                Icon(
                                    imageVector = if (activeTab == index) destination.selectedIcon else destination.unselectedIcon,
                                    contentDescription = stringResource(destination.labelRes)
                                )
                            },
                            label = { Text(stringResource(destination.labelRes)) }
                        )
                    }
                }
            }
            Scaffold(
                modifier = Modifier.weight(1f),
                snackbarHost = { SnackbarHost(hostState = snackBarHostState) },
                topBar = {
                    if (activeTab == 0) {
                        HomeTopBar(
                            query = homeUiState.searchQuery,
                            isSearchActive = homeUiState.isSearchActive,
                            profileName = homeUiState.profileName,
                            profileImage = homeUiState.profileImage,
                            profileImageHeaders = homeUiState.profileImageHeaders,
                            onQueryChange = homeViewModel::onSearchQueryChange,
                            onSearchActiveChange = homeViewModel::onSearchActiveChange,
                            onClearQuery = { homeViewModel.onSearchQueryChange("") },
                            onProfileClick = {
                                homeViewModel.onSearchActiveChange(false)
                                homeViewModel.onSearchQueryChange("")
                                selectedTab = 3
                            }
                        )
                    }
                },
                floatingActionButton = {
                    if (activeTab == 0 && !homeUiState.isSearchActive) {
                        with(sharedTransitionScope) {
                            ExtendedFloatingActionButton(
                                onClick = onAddBookmark,
                                expanded = fabExpanded,
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(26.dp)
                                    )
                                },
                                text = { Text(stringResource(R.string.add_link)) },
                                modifier = Modifier
                                    .sharedBounds(
                                        sharedContentState = rememberSharedContentState(key = "create_bookmark_container"),
                                        animatedVisibilityScope = animatedContentScope,
                                        boundsTransform = BoundsTransform { _, _ ->
                                            tween(
                                                durationMillis = 600,
                                                easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
                                            )
                                        },
                                        clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(16.dp)),
                                        enter = fadeIn(tween(250, delayMillis = 200, easing = FastOutSlowInEasing)),
                                        exit = fadeOut(tween(100, easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)))
                                    ),
                                shape = RoundedCornerShape(16.dp),
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                },
                floatingActionButtonPosition = FabPosition.End,
                bottomBar = {
                    if (!homeUiState.isSearchActive && !wide) {
                        val navigationBarColor = NavigationBarDefaults.containerColor
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = navigationBarColor
                        ) {
                            NavigationBar(
                                modifier = Modifier.navigationBarsPadding(),
                                containerColor = navigationBarColor,
                                tonalElevation = 0.dp,
                                windowInsets = WindowInsets(0.dp)
                            ) {
                                homeDestinations.forEachIndexed { index, destination ->
                                    NavigationBarItem(
                                        selected = activeTab == index,
                                        onClick = { selectedTab = index },
                                        icon = {
                                            Icon(
                                                imageVector = if (activeTab == index) {
                                                    destination.selectedIcon
                                                } else {
                                                    destination.unselectedIcon
                                                },
                                                contentDescription = stringResource(destination.labelRes)
                                            )
                                        },
                                        label = { Text(stringResource(destination.labelRes)) }
                                    )
                                }
                            }
                        }
                    }
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    when (activeTab) {
                        0 -> HomeContent(
                            isLoading = homeUiState.isLoading,
                            isRefreshing = homeUiState.isRefreshing,
                            hasCompletedInitialLoad = homeUiState.hasCompletedInitialBookmarksLoad,
                            errorMessage = homeUiState.errorMessage,
                            errorIsNetwork = homeUiState.errorIsNetwork,
                            syncFailure = homeUiState.syncFailure,
                            listState = listState,
                            bookmarks = displayedBookmarks,
                            isSearchActive = homeUiState.isSearchActive,
                            searchQuery = homeUiState.searchQuery,
                            filter = homeUiState.homeFilter,
                            allBookmarks = homeUiState.bookmarks,
                            lists = homeUiState.lists,
                            folderMembership = homeUiState.folderMembership,
                            linkActions = linkActions,
                            batchActions = BatchActions(
                                lists = homeUiState.lists,
                                tags = homeUiState.tags,
                                onApplyFolder = homeViewModel::applyFolderToBookmarks,
                                onApplyTag = homeViewModel::addTagToBookmarks,
                                onCreateFolder = homeViewModel::createFolder
                            ),
                            onFilterChange = homeViewModel::onFilterChange,
                            onRetry = { homeViewModel.loadSavedItems() },
                            onRefresh = { homeViewModel.refreshSavedItems() }
                        )

                        2 -> ReadableWidth(maxWidth = 840.dp) {
TagsContent(
                            isLoading = homeUiState.isTagsLoading,
                            isRefreshing = homeUiState.isTagsRefreshing,
                            errorMessage = homeUiState.tagsErrorMessage,
                            tags = homeUiState.tags,
                            selectedTag = homeUiState.selectedTag,
                            selectedTagDetails = homeUiState.selectedTagDetails,
                            isTagBookmarksLoading = homeUiState.isTagBookmarksLoading,
                            tagBookmarks = homeUiState.tagBookmarks,
                            tagBookmarksErrorMessage = homeUiState.tagBookmarksErrorMessage,
                            onRefresh = { homeViewModel.refreshTags() },
                            onTagClick = homeViewModel::openTag,
                            onCloseTagDetail = homeViewModel::closeTagDetail,
                            onRefreshTagBookmarks = homeViewModel::refreshTagBookmarks,
                            onRenameTag = homeViewModel::renameTag,
                            onDeleteTag = homeViewModel::deleteTag,
                            onTransferTag = homeViewModel::transferTagBookmarks,
                            onBookmarkClick = { selectedBookmark = it }
                        )
}

                        1 -> ReadableWidth(maxWidth = 840.dp) {
ListsContent(
                            isLoading = homeUiState.isListsLoading,
                            isRefreshing = homeUiState.isListsRefreshing,
                            errorMessage = homeUiState.listsErrorMessage,
                            showListIcons = currentSettings.showListIcons,
                            lists = homeUiState.lists,
                            selectedList = homeUiState.selectedList,
                            selectedListDetails = homeUiState.selectedListDetails,
                            isListBookmarksLoading = homeUiState.isListBookmarksLoading,
                            listBookmarks = homeUiState.listBookmarks,
                            listBookmarksErrorMessage = homeUiState.listBookmarksErrorMessage,
                            onRefresh = { homeViewModel.refreshLists() },
                            onOpenFavorites = homeViewModel::openFavoritesList,
                            onListClick = homeViewModel::openList,
                            onCloseListDetail = homeViewModel::closeListDetail,
                            onRefreshListBookmarks = homeViewModel::refreshListBookmarks,
                            onBookmarkClick = { selectedBookmark = it },
                            onCreateFolder = homeViewModel::createFolder,
                            onRenameFolder = homeViewModel::renameFolder,
                            onDeleteFolder = homeViewModel::deleteFolder,
                            onTransferFolder = homeViewModel::transferFolderBookmarks
                        )
}
                    }
                }
            }
          }
        }
    }

    editingBookmark?.let { bookmark ->
        EditBookmarkDialog(
            bookmark = bookmark,
            lists = homeUiState.lists,
            knownTags = homeUiState.tags.map { it.name },
            initialFolderId = remember(bookmark.id) { homeViewModel.folderIdsOf(bookmark.id).firstOrNull() },
            onDismiss = { editingBookmark = null },
            onCreateFolder = homeViewModel::createFolder,
            onSave = { title, url, note, tags, folderId ->
                val result = homeViewModel.editBookmark(bookmark, title, url, note, tags, folderId)
                result.fold(
                    onSuccess = { updated ->
                        if (selectedBookmark?.id == updated.id) updateSelectedBookmark(updated)
                        showMessage(updatedMessage)
                        null
                    },
                    onFailure = { it.message ?: "Error" }
                )
            }
        )
    }

    selectedBookmark?.let { bookmark ->
        BookmarkDetailBottomSheet(
            bookmark = bookmark,
            onDismiss = { dismissSelectedBookmark() },
            onOpenLink = { url -> openLink(context, url, currentSettings.openLinksInApp) },
            onShare = { item ->
                val shareText = item.linkUrl ?: item.title ?: item.id
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                    item.title?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
                }
                context.startActivity(Intent.createChooser(shareIntent, null))
            },
            onEdit = { editingBookmark = it },
            onDelete = ::deleteWithUndo,
            onToggleFavourite = { item ->
                homeViewModel.toggleBookmarkFavourite(item, onActionFailed) { updated ->
                    updateSelectedBookmark(updated)
                }
            },
            onToggleArchived = { item ->
                homeViewModel.toggleBookmarkArchived(item, onActionFailed) { updated ->
                    updateSelectedBookmark(updated)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    isLoading: Boolean,
    isRefreshing: Boolean,
    hasCompletedInitialLoad: Boolean,
    errorMessage: String?,
    errorIsNetwork: Boolean,
    syncFailure: SyncFailure?,
    listState: LazyGridState,
    bookmarks: List<BookmarkItem>,
    isSearchActive: Boolean,
    searchQuery: String,
    filter: LinkFilter,
    allBookmarks: List<BookmarkItem>,
    lists: List<SavedListItem>,
    folderMembership: Map<String, Set<String>>,
    linkActions: LinkActions,
    batchActions: BatchActions,
    onFilterChange: (LinkFilter) -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit
) {
    val pullToRefreshState = rememberPullToRefreshState()
    val chips = remember(allBookmarks, lists, folderMembership) { folderChips(allBookmarks, lists, folderMembership) }
    val folderNames = remember(lists) { foldersOf(lists).associate { it.id to it.name } }
    val favouriteCount = remember(allBookmarks) { allBookmarks.count { it.favourited } }
    val readLaterCount = remember(allBookmarks) { allBookmarks.count { it.isReadLater() } }

    when {
        isLoading -> LinkSkeletonList()

        errorMessage != null -> ErrorState(
            title = stringResource(R.string.failed_load_bookmarks),
            detail = errorMessage,
            isNetwork = errorIsNetwork,
            onRetry = onRetry
        )

        bookmarks.isEmpty() && isSearchActive && searchQuery.isNotBlank() && filter == LinkFilter.All -> {
            SearchEmptyState()
        }

        allBookmarks.isEmpty() && !hasCompletedInitialLoad -> LinkSkeletonList()

        allBookmarks.isEmpty() -> StatePanel(
            icon = Icons.Outlined.Bookmarks,
            title = stringResource(R.string.no_bookmarks),
            message = stringResource(R.string.saved_bookmarks_here)
        )

        else -> {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                state = pullToRefreshState,
                indicator = {
                    PullToRefreshExpressiveIndicator(
                        isRefreshing = isRefreshing,
                        state = pullToRefreshState
                    )
                }
            ) {
                val contentModifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val threshold = PullToRefreshDefaults.PositionalThreshold.roundToPx()
                        translationY = pullToRefreshState.distanceFraction.coerceIn(0f, 1f) * threshold
                    }

                LinkListContent(
                    bookmarks = bookmarks,
                    filter = filter,
                    folderChips = chips,
                    folderNames = folderNames,
                    folderMembership = folderMembership,
                    totalCount = allBookmarks.size,
                    favouriteCount = favouriteCount,
                    readLaterCount = readLaterCount,
                    actions = linkActions,
                    batchActions = batchActions,
                    onFilterChange = onFilterChange,
                    modifier = contentModifier,
                    listState = listState,
                    syncFailure = syncFailure,
                    onRetrySync = onRefresh,
                    showFolderChips = !isWideScreen()
                )
            }
        }
    }
}
