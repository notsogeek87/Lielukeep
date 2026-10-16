package com.ruizlenato.karabau.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import com.ruizlenato.karabau.data.model.TagItem

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TagsContent(
    isLoading: Boolean,
    isRefreshing: Boolean,
    errorMessage: String?,
    tags: List<TagItem>,
    selectedTag: TagItem?,
    selectedTagDetails: TagItem?,
    isTagBookmarksLoading: Boolean,
    tagBookmarks: List<BookmarkItem>,
    tagBookmarksErrorMessage: String?,
    onRefresh: () -> Unit,
    onTagClick: (TagItem) -> Unit,
    onCloseTagDetail: () -> Unit,
    onRefreshTagBookmarks: () -> Unit,
    onBookmarkClick: (BookmarkItem) -> Unit = {},
    onRenameTag: (suspend (tag: TagItem, name: String) -> String?)? = null,
    onDeleteTag: (suspend (tag: TagItem) -> String?)? = null,
    onTransferTag: (suspend (tag: TagItem, targetName: String, move: Boolean) -> String?)? = null
) {
    var renameTarget by remember { mutableStateOf<TagItem?>(null) }
    var deleteTarget by remember { mutableStateOf<TagItem?>(null) }
    var transferTarget by remember { mutableStateOf<Pair<TagItem, Boolean>?>(null) }
    val canManage = onRenameTag != null && onDeleteTag != null
    val canTransfer = canManage && onTransferTag != null

    transferTarget?.let { (source, move) ->
        if (onTransferTag != null) {
            TagPickerDialog(
                title = stringResource(if (move) R.string.tag_move_title else R.string.tag_copy_title, source.name),
                tags = tags,
                exclude = source,
                onDismiss = { transferTarget = null },
                onPick = { name -> onTransferTag(source, name, move) }
            )
        }
    }

    renameTarget?.let { target ->
        if (onRenameTag != null) {
            RenameTagDialog(
                tag = target,
                existing = tags,
                onDismiss = { renameTarget = null },
                onRename = { name -> onRenameTag(target, name) }
            )
        }
    }
    deleteTarget?.let { target ->
        if (onDeleteTag != null) {
            DeleteTagDialog(
                tag = target,
                onDismiss = { deleteTarget = null },
                onDelete = { onDeleteTag(target) }
            )
        }
    }

    if (selectedTag != null) {
        TagDetailContent(
            fallbackTag = selectedTag,
            tagDetails = selectedTagDetails,
            isLoading = isTagBookmarksLoading,
            errorMessage = tagBookmarksErrorMessage,
            bookmarks = tagBookmarks,
            onBack = onCloseTagDetail,
            onRetry = onRefreshTagBookmarks,
            onBookmarkClick = onBookmarkClick,
            onRename = if (canManage) ({ renameTarget = selectedTagDetails ?: selectedTag }) else null,
            onDelete = if (canManage) ({ deleteTarget = selectedTagDetails ?: selectedTag }) else null,
            onTransfer = if (canTransfer) ({ move -> transferTarget = (selectedTagDetails ?: selectedTag) to move }) else null
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        MediumFlexibleTopAppBar(
            title = stringResource(R.string.tags_tab),
            modifier = Modifier.padding(horizontal = 16.dp)
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
                ErrorState(
                    title = stringResource(R.string.failed_load_tags),
                    detail = errorMessage,
                    onRetry = onRefresh
                )
            }

            tags.isEmpty() -> {
                StatePanel(
                    icon = Icons.Outlined.LocalOffer,
                    title = stringResource(R.string.no_tags),
                    message = stringResource(R.string.tags_help_text)
                )
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
                        items(tags, key = { it.id }) { tag ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceContainer
                            ) {
                                ListItem(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { onTagClick(tag) },
                                    trailingContent = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = tag.numBookmarks.toString(),
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (canManage) {
                                                TagMenu(
                                                    onRename = { renameTarget = tag },
                                                    onDelete = { deleteTarget = tag },
                                                    onTransfer = if (canTransfer) ({ move -> transferTarget = tag to move }) else null
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    colors = ListItemDefaults.colors(
                                        containerColor = Color.Transparent
                                    )
                                ) {
                                    Text(
                                        text = tag.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TagDetailContent(
    fallbackTag: TagItem,
    tagDetails: TagItem?,
    isLoading: Boolean,
    errorMessage: String?,
    bookmarks: List<BookmarkItem>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onBookmarkClick: (BookmarkItem) -> Unit = {},
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onTransfer: ((move: Boolean) -> Unit)? = null
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
        val title = tagDetails?.name ?: fallbackTag.name

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
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (onRename != null && onDelete != null) {
                TagMenu(onRename = onRename, onDelete = onDelete, onTransfer = onTransfer)
            }
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
                ErrorState(
                    title = stringResource(R.string.failed_load_bookmarks),
                    detail = errorMessage,
                    onRetry = onRetry
                )
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

@Composable
private fun MediumFlexibleTopAppBar(
    title: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TagMenu(onRename: () -> Unit, onDelete: () -> Unit, onTransfer: ((move: Boolean) -> Unit)? = null) {
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
        if (onTransfer != null) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.tag_menu_move)) },
                onClick = {
                    open = false
                    onTransfer(true)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.tag_menu_copy)) },
                onClick = {
                    open = false
                    onTransfer(false)
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

/** Renommage d'une étiquette ; [onRename] renvoie un message d'erreur (détail du serveur) ou null. */
@Composable
private fun RenameTagDialog(
    tag: TagItem,
    existing: List<TagItem>,
    onDismiss: () -> Unit,
    onRename: suspend (name: String) -> String?
) {
    var name by rememberSaveable { mutableStateOf(tag.name) }
    var error by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val trimmed = name.trim()
    val nameTaken = trimmed.isNotEmpty() && existing.any { it.id != tag.id && it.name.equals(trimmed, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(stringResource(R.string.tag_rename_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    error = null
                },
                label = { Text(stringResource(R.string.tag_name_label)) },
                singleLine = true,
                isError = nameTaken || error != null,
                supportingText = {
                    val message = if (nameTaken) stringResource(R.string.tag_name_taken) else error
                    if (message != null) Text(message)
                }
            )
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving && trimmed.isNotEmpty() && !nameTaken && trimmed != tag.name,
                onClick = {
                    scope.launch {
                        isSaving = true
                        val failure = onRename(trimmed)
                        isSaving = false
                        if (failure == null) onDismiss() else error = failure
                    }
                }
            ) {
                Text(stringResource(R.string.folder_rename))
            }
        },
        dismissButton = {
            TextButton(enabled = !isSaving, onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun DeleteTagDialog(
    tag: TagItem,
    onDismiss: () -> Unit,
    onDelete: suspend () -> String?
) {
    var error by remember { mutableStateOf<String?>(null) }
    var isDeleting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        title = { Text(stringResource(R.string.tag_delete_title)) },
        text = {
            Column {
                Text(stringResource(R.string.tag_delete_message, tag.name))
                error?.let {
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
            TextButton(
                enabled = !isDeleting,
                onClick = {
                    scope.launch {
                        isDeleting = true
                        val failure = onDelete()
                        isDeleting = false
                        if (failure == null) onDismiss() else error = failure
                    }
                }
            ) {
                Text(stringResource(R.string.menu_delete))
            }
        },
        dismissButton = {
            TextButton(enabled = !isDeleting, onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
