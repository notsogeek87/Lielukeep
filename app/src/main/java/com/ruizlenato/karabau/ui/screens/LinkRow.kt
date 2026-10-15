package com.ruizlenato.karabau.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.MoreVert
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
    onFilterChange: (LinkFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        FilterChipsRow(
            filter = filter,
            folderChips = folderChips,
            totalCount = totalCount,
            favouriteCount = favouriteCount,
            readLaterCount = readLaterCount,
            onFilterChange = onFilterChange
        )

        if (bookmarks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.filter_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val now = remember(bookmarks) { System.currentTimeMillis() }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                items(bookmarks, key = { it.id }) { bookmark ->
                    // Le nom du dossier n'a de sens que dans « Tous » : sous un filtre de dossier il serait redondant.
                    val folderName = if (filter == LinkFilter.All) {
                        folderMembership.entries.firstOrNull { bookmark.id in it.value }?.key?.let(folderNames::get)
                    } else {
                        null
                    }
                    LinkRow(bookmark = bookmark, now = now, folderName = folderName, actions = actions)
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
            label = { Text(stringResource(R.string.filter_all, totalCount)) }
        )
        FilterChip(
            selected = filter == LinkFilter.Favorites,
            onClick = { onFilterChange(LinkFilter.Favorites) },
            label = { Text(stringResource(R.string.filter_favorites, favouriteCount)) }
        )
        FilterChip(
            selected = filter == LinkFilter.ReadLater,
            onClick = { onFilterChange(LinkFilter.ReadLater) },
            label = { Text(stringResource(R.string.filter_read_later, readLaterCount)) }
        )
        folderChips.forEach { chip ->
            FilterChip(
                selected = filter == LinkFilter.Folder(chip.folder.id),
                onClick = { onFilterChange(LinkFilter.Folder(chip.folder.id)) },
                label = { Text("${chip.folder.name} (${chip.count})") }
            )
        }
    }
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
    modifier: Modifier = Modifier
) {
    // L'état de glissement survit aux recompositions : il doit toujours appeler les derniers callbacks.
    val currentFavourite by rememberUpdatedState(actions.onToggleFavourite)
    val currentArchive by rememberUpdatedState(actions.onArchive)
    val currentBookmark by rememberUpdatedState(bookmark)
    val swipeState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> currentFavourite(currentBookmark)
                SwipeToDismissBoxValue.EndToStart -> currentArchive(currentBookmark)
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        }
    )

    SwipeToDismissBox(
        state = swipeState,
        modifier = modifier,
        backgroundContent = {
            val toFavourite = swipeState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (toFavourite) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .padding(horizontal = 24.dp),
                contentAlignment = if (toFavourite) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = if (toFavourite) Icons.Default.Star else Icons.Default.Archive,
                    contentDescription = stringResource(
                        if (toFavourite) R.string.action_favourite else R.string.action_archive
                    )
                )
            }
        }
    ) {
        val site = bookmark.siteName()
        val age = relativeTimeFr(now, parseIsoMillis(bookmark.createdAt))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .combinedClickable(
                    onClick = { actions.onOpen(bookmark) },
                    onLongClick = { actions.onShowDetails(bookmark) }
                )
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LinkThumbnail(bookmark, site)
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    text = bookmark.title?.takeIf { it.isNotBlank() }
                        ?: site.ifEmpty { bookmark.linkUrl ?: stringResource(R.string.untitled_link) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOf(site, age).filter { it.isNotEmpty() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (folderName != null) {
                    Text(
                        text = folderName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (bookmark.favourited) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = stringResource(R.string.favourited),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            RowMenu(bookmark, actions)
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
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(10.dp))
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
}

@Composable
private fun RowMenu(bookmark: BookmarkItem, actions: LinkActions) {
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
        Item(stringResource(R.string.menu_copy_link), actions.onCopy)
        Item(stringResource(R.string.menu_share), actions.onShare)
        Item(stringResource(R.string.action_archive), actions.onArchive)
        Item(stringResource(R.string.menu_delete), actions.onDelete)
    }
}
