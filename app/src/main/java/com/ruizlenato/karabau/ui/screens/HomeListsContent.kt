package com.ruizlenato.karabau.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.ruizlenato.karabau.data.links.canHaveSubfolder
import com.ruizlenato.karabau.data.links.orderedFolders
import kotlinx.coroutines.launch
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ruizlenato.karabau.R
import com.ruizlenato.karabau.data.model.BookmarkItem
import com.ruizlenato.karabau.data.model.SavedListItem

@Composable
internal fun ListsContent(
    isLoading: Boolean,
    isRefreshing: Boolean,
    errorMessage: String?,
    showListIcons: Boolean,
    lists: List<SavedListItem>,
    selectedList: SavedListItem?,
    selectedListDetails: SavedListItem?,
    isListBookmarksLoading: Boolean,
    listBookmarks: List<BookmarkItem>,
    listBookmarksErrorMessage: String?,
    onRefresh: () -> Unit,
    onOpenFavorites: () -> Unit,
    onListClick: (SavedListItem) -> Unit,
    onCloseListDetail: () -> Unit,
    onRefreshListBookmarks: () -> Unit,
    onBookmarkClick: (BookmarkItem) -> Unit = {},
    onCreateFolder: (suspend (name: String, parentId: String?) -> String?)? = null,
    onRenameFolder: (suspend (folder: SavedListItem, name: String) -> String?)? = null,
    onDeleteFolder: (suspend (folder: SavedListItem) -> String?)? = null
) {
    // Dossiers (listes manuelles dont on est propriétaire) d'abord, par niveau ; les autres listes ensuite.
    val rows = remember(lists) {
        val folders = orderedFolders(lists)
        val folderIds = folders.mapTo(mutableSetOf()) { it.first.id }
        folders + lists.filter { it.id !in folderIds }.map { it to 0 }
    }
    var createParentId by remember { mutableStateOf<String?>(null) }
    var isCreateOpen by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<SavedListItem?>(null) }
    var deleteTarget by remember { mutableStateOf<SavedListItem?>(null) }
    val deleteScope = rememberCoroutineScope()
    var deleteError by remember { mutableStateOf<String?>(null) }

    if (selectedList != null) {
        ListDetailContent(
            fallbackList = selectedList,
            listDetails = selectedListDetails,
            isLoading = isListBookmarksLoading,
            errorMessage = listBookmarksErrorMessage,
            bookmarks = listBookmarks,
            onBack = onCloseListDetail,
            onRetry = onRefreshListBookmarks,
            onBookmarkClick = onBookmarkClick
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ListsTopAppBar(
            title = stringResource(R.string.lists_tab),
            modifier = Modifier.padding(horizontal = 16.dp),
            action = onCreateFolder?.let {
                {
                    TextButton(onClick = {
                        createParentId = null
                        isCreateOpen = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            text = stringResource(R.string.folder_new),
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            }
        )

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    ContainedLoadingIndicator(modifier = Modifier.size(56.dp))
                }
            }

            errorMessage != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.failed_load_lists),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(
                            onClick = onRefresh,
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }

            lists.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.no_lists),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = stringResource(R.string.lists_help_text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            else -> {
                val pullToRefreshState = rememberPullToRefreshState()
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
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val threshold = PullToRefreshDefaults.PositionalThreshold.roundToPx()
                                translationY = pullToRefreshState.distanceFraction.coerceIn(0f, 1f) * threshold
                            },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        item(key = "favorites") {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clickable(onClick = onOpenFavorites),
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceContainer
                            ) {
                                ListItem(
                                    modifier = Modifier.fillMaxWidth(),
                                    leadingContent = if (showListIcons) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.tertiary
                                            )
                                        }
                                    } else null,
                                    trailingContent = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    headlineContent = {
                                        Text(
                                            text = stringResource(R.string.favorites),
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            text = stringResource(R.string.favourited_bookmarks_desc),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    colors = ListItemDefaults.colors(
                                        containerColor = Color.Transparent
                                    )
                                )
                            }
                        }

                        items(rows, key = { it.first.id }) { (list, depth) ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = (depth * 24).dp, top = 2.dp, bottom = 2.dp)
                                    .clickable(onClick = { onListClick(list) }),
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceContainer
                            ) {
                                ListItem(
                                    modifier = Modifier.fillMaxWidth(),
                                    leadingContent = if (showListIcons) {
                                        {
                                            Text(
                                                text = list.icon,
                                                style = MaterialTheme.typography.titleLarge
                                            )
                                        }
                                    } else null,
                                    trailingContent = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (onRenameFolder != null && onDeleteFolder != null &&
                                                list.type == "manual" && list.userRole == "owner"
                                            ) {
                                                FolderMenu(
                                                    canAddSubfolder = canHaveSubfolder(list, lists),
                                                    onRename = { renameTarget = list },
                                                    onAddSubfolder = {
                                                        createParentId = list.id
                                                        isCreateOpen = true
                                                    },
                                                    onDelete = {
                                                        deleteError = null
                                                        deleteTarget = list
                                                    }
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    headlineContent = {
                                        Text(
                                            text = list.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    supportingContent = list.description?.takeIf { it.isNotBlank() }?.let { description ->
                                        {
                                            Text(
                                                text = description,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    colors = ListItemDefaults.colors(
                                        containerColor = Color.Transparent
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (isCreateOpen && onCreateFolder != null) {
        NewFolderDialog(
            lists = lists,
            initialParentId = createParentId,
            onDismiss = { isCreateOpen = false },
            onCreate = onCreateFolder
        )
    }

    renameTarget?.let { target ->
        if (onRenameFolder != null) {
            RenameFolderDialog(
                folder = target,
                lists = lists,
                onDismiss = { renameTarget = null },
                onRename = { name -> onRenameFolder(target, name) }
            )
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.folder_delete_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.folder_delete_message, target.name))
                    deleteError?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteScope.launch {
                        val failure = onDeleteFolder?.invoke(target)
                        if (failure == null) deleteTarget = null else deleteError = failure
                    }
                }) {
                    Text(stringResource(R.string.menu_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun ListsTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        action?.invoke()
    }
}

@Composable
private fun FolderMenu(
    canAddSubfolder: Boolean,
    onRename: () -> Unit,
    onAddSubfolder: () -> Unit,
    onDelete: () -> Unit
) {
    var open by remember { mutableStateOf(false) }

    IconButton(onClick = { open = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_actions))
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.folder_rename)) },
            onClick = {
                open = false
                onRename()
            }
        )
        if (canAddSubfolder) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.folder_add_subfolder)) },
                onClick = {
                    open = false
                    onAddSubfolder()
                }
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_delete)) },
            onClick = {
                open = false
                onDelete()
            }
        )
    }
}

@Composable
private fun ListDetailContent(
    fallbackList: SavedListItem,
    listDetails: SavedListItem?,
    isLoading: Boolean,
    errorMessage: String?,
    bookmarks: List<BookmarkItem>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onBookmarkClick: (BookmarkItem) -> Unit = {}
) {
    val pullToRefreshState = rememberPullToRefreshState()
    val cardColors = CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    )
    val cardElevation = CardDefaults.elevatedCardElevation(
        defaultElevation = 1.dp,
        hoveredElevation = 2.dp,
        pressedElevation = 1.dp,
        draggedElevation = 4.dp
    )

    Column(modifier = Modifier.fillMaxSize()) {
        val title = listDetails?.name ?: fallbackList.name

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        PullToRefreshBox(
            isRefreshing = isLoading && bookmarks.isNotEmpty(),
            onRefresh = onRetry,
            state = pullToRefreshState,
            indicator = {
                PullToRefreshExpressiveIndicator(
                    isRefreshing = isLoading && bookmarks.isNotEmpty(),
                    state = pullToRefreshState
                )
            }
        ) {
            when {
                isLoading && bookmarks.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        ContainedLoadingIndicator(modifier = Modifier.size(56.dp))
                    }
                }

                errorMessage != null && bookmarks.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.failed_load_bookmarks),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Button(
                                onClick = onRetry,
                                modifier = Modifier.padding(top = 12.dp)
                            ) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }

                bookmarks.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.no_bookmarks),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val threshold = PullToRefreshDefaults.PositionalThreshold.roundToPx()
                                translationY = pullToRefreshState.distanceFraction.coerceIn(0f, 1f) * threshold
                            },
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(bookmarks, key = { it.id }) { bookmark ->
                            BookmarkListItem(
                                bookmark = bookmark,
                                cardColors = cardColors,
                                cardElevation = cardElevation,
                                onBookmarkClick = onBookmarkClick
                            )
                        }
                    }
                }
            }
        }
    }
}
