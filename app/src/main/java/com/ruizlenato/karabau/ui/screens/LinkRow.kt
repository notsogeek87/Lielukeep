package com.ruizlenato.karabau.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import com.ruizlenato.karabau.ui.viewmodel.SyncFailure
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material3.Checkbox
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ruizlenato.karabau.R
import com.ruizlenato.karabau.data.links.FolderChip
import com.ruizlenato.karabau.data.links.LinkFilter
import com.ruizlenato.karabau.data.links.isReadLater
import com.ruizlenato.karabau.data.links.parseIsoMillis
import com.ruizlenato.karabau.data.links.relativeTimeFr
import com.ruizlenato.karabau.data.links.siteName
import com.ruizlenato.karabau.data.model.BookmarkItem

/** Actions disponibles sur un lien : mêmes opérations par glissement et par le menu ⋮ (accessibilité). */
internal class LinkActions(
    val onOpen: (BookmarkItem) -> Unit,
    val onShowDetails: (BookmarkItem) -> Unit,
    val onEdit: (BookmarkItem) -> Unit,
    val onToggleFavourite: (BookmarkItem) -> Unit,
    val onToggleReadLater: (BookmarkItem) -> Unit,
    val onCopy: (BookmarkItem) -> Unit,
    val onShare: (BookmarkItem) -> Unit,
    val onArchive: (BookmarkItem) -> Unit,
    val onDelete: (BookmarkItem) -> Unit
)

/** Liste des liens de l'accueil : puces de filtre, puis une ligne « confort » par lien. */
@Composable
internal fun LinkListContent(
    bookmarks: List<BookmarkItem>,
    filter: LinkFilter,
    folderChips: List<FolderChip>,
    folderNames: Map<String, String>,
    folderMembership: Map<String, Set<String>>,
    totalCount: Int,
    favouriteCount: Int,
    readLaterCount: Int,
    actions: LinkActions,
    batchActions: BatchActions,
    onFilterChange: (LinkFilter) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyGridState = rememberLazyGridState(),
    syncFailure: SyncFailure? = null,
    onRetrySync: () -> Unit = {},
    showFolderChips: Boolean = true
) {
    // Avec le rail de navigation (écran large) l'onglet « Listes » est déjà à gauche : les dossiers en puces feraient
    // doublon. On garde seulement celui qui sert de filtre actif, pour qu'il reste visible et désactivable.
    val visibleFolderChips = if (showFolderChips) folderChips else folderChips.filter { filter == LinkFilter.Folder(it.folder.id) }
    // Multisélection : on garde les identifiants, et on oublie ceux qui ont quitté la liste (archivage, filtre…).
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var selecting by remember { mutableStateOf(false) }
    val visibleIds = remember(bookmarks) { bookmarks.mapTo(HashSet()) { it.id } }
    val effective = selected.filterTo(HashSet()) { it in visibleIds }
    var folderDialog by remember { mutableStateOf(false) }
    var tagDialog by remember { mutableStateOf(false) }
    fun exitSelection() {
        selecting = false
        selected = emptySet()
    }
    BackHandler(enabled = selecting) { exitSelection() }

    if (folderDialog) {
        FolderApplyDialog(
            count = effective.size,
            lists = batchActions.lists,
            onCreateFolder = batchActions.onCreateFolder,
            onDismiss = { folderDialog = false },
            onApply = { folderId ->
                batchActions.onApplyFolder(effective.toList(), folderId).also { if (it == null) exitSelection() }
            }
        )
    }
    if (tagDialog) {
        TagPickerDialog(
            title = stringResource(R.string.selection_tag_title, effective.size),
            tags = batchActions.tags,
            onDismiss = { tagDialog = false },
            onPick = { name ->
                batchActions.onApplyTag(effective.toList(), name).also { if (it == null) exitSelection() }
            }
        )
    }

    Column(modifier = modifier) {
        if (selecting) {
            SelectionBar(
                count = effective.size,
                onClose = ::exitSelection,
                onSelectAll = { selected = visibleIds },
                onFolder = { folderDialog = true },
                onTag = { tagDialog = true }
            )
        }
        AnimatedVisibility(visible = syncFailure != null) {
            // Pendant l'animation de sortie `syncFailure` est déjà null : le repli évite un contenu vide.
            SyncFailureBanner(failure = syncFailure ?: SyncFailure.Network, onRetry = onRetrySync)
        }
        if (!selecting) FilterChipsRow(
            filter = filter,
            folderChips = visibleFolderChips,
            totalCount = totalCount,
            favouriteCount = favouriteCount,
            readLaterCount = readLaterCount,
            onFilterChange = onFilterChange
        )

        if (bookmarks.isEmpty()) {
            StatePanel(
                icon = Icons.Outlined.FilterAlt,
                title = stringResource(R.string.filter_empty),
                message = stringResource(R.string.filter_empty_hint),
                actionLabel = if (filter != LinkFilter.All) stringResource(R.string.filter_show_all) else null,
                onAction = if (filter != LinkFilter.All) ({ onFilterChange(LinkFilter.All) }) else null
            )
        } else {
            val now = remember(bookmarks) { System.currentTimeMillis() }
            // Une colonne sur téléphone, plusieurs sur écran large (Fold ouvert, tablette) selon la place disponible.
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 320.dp),
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(bookmarks, key = { it.id }) { bookmark ->
                    // Le nom du dossier n'a de sens que dans « Tous » : sous un filtre de dossier il serait redondant.
                    val folderName = if (filter == LinkFilter.All) {
                        folderMembership.entries.firstOrNull { bookmark.id in it.value }?.key?.let(folderNames::get)
                    } else {
                        null
                    }
                    LinkRow(
                        bookmark = bookmark,
                        now = now,
                        folderName = folderName,
                        actions = actions,
                        selectionMode = selecting,
                        selected = bookmark.id in effective,
                        onToggleSelect = {
                            selected = if (bookmark.id in effective) effective - bookmark.id else effective + bookmark.id
                        },
                        onStartSelect = {
                            selecting = true
                            selected = setOf(bookmark.id)
                        },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChipsRow(
    filter: LinkFilter,
    folderChips: List<FolderChip>,
    totalCount: Int,
    favouriteCount: Int,
    readLaterCount: Int,
    onFilterChange: (LinkFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = filter == LinkFilter.All,
            onClick = { onFilterChange(LinkFilter.All) },
            label = { Text(stringResource(R.string.filter_all, totalCount)) },
            leadingIcon = { ChipIcon(Icons.Outlined.Bookmarks) },
            shape = ChipShape
        )
        FilterChip(
            selected = filter == LinkFilter.Favorites,
            onClick = { onFilterChange(LinkFilter.Favorites) },
            label = { Text(stringResource(R.string.filter_favorites, favouriteCount)) },
            leadingIcon = { ChipIcon(Icons.Outlined.StarBorder) },
            shape = ChipShape
        )
        FilterChip(
            selected = filter == LinkFilter.ReadLater,
            onClick = { onFilterChange(LinkFilter.ReadLater) },
            label = { Text(stringResource(R.string.filter_read_later, readLaterCount)) },
            leadingIcon = { ChipIcon(Icons.Outlined.Schedule) },
            shape = ChipShape
        )
        folderChips.forEach { chip ->
            FilterChip(
                selected = filter == LinkFilter.Folder(chip.folder.id),
                onClick = { onFilterChange(LinkFilter.Folder(chip.folder.id)) },
                label = { Text("${chip.folder.name} (${chip.count})") },
                leadingIcon = { ChipIcon(Icons.Outlined.Folder) },
                shape = ChipShape
            )
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onFolder: () -> Unit,
    onTag: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.selection_close))
        }
        Text(
            text = stringResource(R.string.selection_count, count),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onSelectAll) {
            Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.selection_all))
        }
        IconButton(onClick = onFolder, enabled = count > 0) {
            Icon(Icons.Outlined.Folder, contentDescription = stringResource(R.string.selection_folder))
        }
        IconButton(onClick = onTag, enabled = count > 0) {
            Icon(Icons.Outlined.LocalOffer, contentDescription = stringResource(R.string.selection_tag))
        }
    }
}

private val ChipShape = RoundedCornerShape(12.dp)

@Composable
private fun ChipIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
}

/**
 * Ligne « confort » : pastille du site, titre, `site · âge`, dossier. Glisser vers la droite = favori, vers la
 * gauche = archiver (la ligne revient en place, une archive la retire via la mise à jour de la liste).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun LinkRow(
    bookmark: BookmarkItem,
    now: Long,
    folderName: String?,
    actions: LinkActions,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onStartSelect: () -> Unit = {}
) {
    // L'état de glissement survit aux recompositions : il doit toujours appeler les derniers callbacks.
    val currentFavourite by rememberUpdatedState(actions.onToggleFavourite)
    val currentArchive by rememberUpdatedState(actions.onArchive)
    val currentBookmark by rememberUpdatedState(bookmark)
    val haptics = LocalHapticFeedback.current
    val swipeState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> currentFavourite(currentBookmark)
                SwipeToDismissBoxValue.EndToStart -> currentArchive(currentBookmark)
                SwipeToDismissBoxValue.Settled -> Unit
            }
            if (value != SwipeToDismissBoxValue.Settled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            false
        }
    )
    val cardShape = RoundedCornerShape(20.dp)

    SwipeToDismissBox(
        state = swipeState,
        enableDismissFromStartToEnd = !selectionMode,
        enableDismissFromEndToStart = !selectionMode,
        modifier = modifier.clip(cardShape),
        backgroundContent = {
            val toFavourite = swipeState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            // Le fond prend la couleur de l'action et l'icône grossit dès que le geste est assez long pour être validé.
            val armed = swipeState.targetValue != SwipeToDismissBoxValue.Settled
            val background by animateColorAsState(
                targetValue = when {
                    !armed -> MaterialTheme.colorScheme.surfaceContainerHighest
                    toFavourite -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.tertiaryContainer
                },
                label = "swipeBackground"
            )
            val iconScale by animateFloatAsState(if (armed) 1.3f else 0.9f, label = "swipeIconScale")
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(background)
                    .padding(horizontal = 24.dp),
                contentAlignment = if (toFavourite) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = if (toFavourite) Icons.Default.Star else Icons.Default.Archive,
                    contentDescription = stringResource(
                        if (toFavourite) R.string.action_favourite else R.string.action_archive
                    ),
                    modifier = Modifier.scale(iconScale)
                )
            }
        }
    ) {
        val site = bookmark.siteName()
        val age = relativeTimeFr(now, parseIsoMillis(bookmark.createdAt))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (selected) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerLow
                )
                .combinedClickable(
                    onClick = { if (selectionMode) onToggleSelect() else actions.onOpen(bookmark) },
                    onLongClick = { if (selectionMode) onToggleSelect() else actions.onShowDetails(bookmark) }
                )
                .padding(start = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LinkThumbnail(bookmark, site)
            Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(
                    text = bookmark.title?.takeIf { it.isNotBlank() }
                        ?: site.ifEmpty { bookmark.linkUrl ?: stringResource(R.string.untitled_link) },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOf(site, age).filter { it.isNotEmpty() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
                if (folderName != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        Text(
                            text = folderName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
            } else {
                RowMenu(bookmark, actions, onStartSelect)
            }
        }
    }
}

/**
 * Vignette de la ligne : image de l'article, sinon favicon du site. La pastille avec l'initiale reste dessous : elle
 * sert d'attente et de repli quand le lien n'a pas d'image ou qu'elle ne se charge pas.
 */
@Composable
private fun LinkThumbnail(bookmark: BookmarkItem, site: String) {
    val context = LocalContext.current
    val imageUrl = bookmark.imageUrl?.takeIf { it.isNotBlank() } ?: bookmark.faviconUrl?.takeIf { it.isNotBlank() }
    val request = remember(context, imageUrl) {
        imageUrl?.let { ImageRequest.Builder(context).data(it).crossfade(true).build() }
    }
    val isFavicon = bookmark.imageUrl.isNullOrBlank()
    Box(modifier = Modifier.size(64.dp)) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = site.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = if (isFavicon) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (isFavicon) Modifier.padding(14.dp) else Modifier)
            )
        }
    }
    if (bookmark.favourited) {
        // Badge sur le coin de la vignette : libère la place du titre à droite de la ligne.
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.TopEnd).size(22.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = stringResource(R.string.favourited),
                modifier = Modifier.padding(4.dp)
            )
        }
    }
    }
}

@Composable
private fun RowMenu(bookmark: BookmarkItem, actions: LinkActions, onStartSelect: () -> Unit) {
    var open by remember { mutableStateOf(false) }

    @Composable
    fun Item(label: String, action: (BookmarkItem) -> Unit) {
        DropdownMenuItem(
            text = { Text(label) },
            onClick = {
                open = false
                action(bookmark)
            }
        )
    }

    IconButton(onClick = { open = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_actions))
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        Item(
            stringResource(if (bookmark.favourited) R.string.menu_unfavourite else R.string.menu_favourite),
            actions.onToggleFavourite
        )
        Item(
            stringResource(if (bookmark.isReadLater()) R.string.menu_unread_later else R.string.menu_read_later),
            actions.onToggleReadLater
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_select)) },
            onClick = {
                open = false
                onStartSelect()
            }
        )
        Item(stringResource(R.string.menu_edit), actions.onEdit)
        Item(stringResource(R.string.menu_copy_link), actions.onCopy)
        Item(stringResource(R.string.menu_share), actions.onShare)
        Item(stringResource(R.string.action_archive), actions.onArchive)
        Item(stringResource(R.string.menu_delete), actions.onDelete)
    }
}
