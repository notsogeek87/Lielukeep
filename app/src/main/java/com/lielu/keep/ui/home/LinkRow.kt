package com.lielu.keep.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lielu.keep.data.db.LinkEntity
import com.lielu.keep.data.links.UrlTools
import com.lielu.keep.data.links.relativeTimeFr

/**
 * Ligne « confort » d'un lien : pastille du site, titre, `site · âge`, dossier. Swipe droite = favori,
 * swipe gauche = archiver ; les mêmes actions sont dans le menu ⋮ (accessibilité).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkRow(
    link: LinkEntity,
    folderName: String?,
    showFolder: Boolean,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleReadLater: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Le state de swipe survit aux recompositions : il doit toujours appeler les callbacks les plus récents.
    val currentFavorite by rememberUpdatedState(onToggleFavorite)
    val currentArchive by rememberUpdatedState(onArchive)
    val swipeState =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                when (value) {
                    SwipeToDismissBoxValue.StartToEnd -> currentFavorite()
                    SwipeToDismissBoxValue.EndToStart -> currentArchive()
                    SwipeToDismissBoxValue.Settled -> Unit
                }
                false // la ligne revient en place ; une archive la retire via la mise à jour de la liste
            },
        )

    SwipeToDismissBox(
        state = swipeState,
        modifier = modifier,
        backgroundContent = {
            val toFavorite = swipeState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val swipeBackground =
                if (toFavorite) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(swipeBackground)
                        .padding(horizontal = 24.dp),
                contentAlignment = if (toFavorite) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Icon(
                    if (toFavorite) Icons.Default.Star else Icons.Default.Archive,
                    contentDescription = if (toFavorite) "Favori" else "Archiver",
                )
            }
        },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .clickable(onClick = onOpen)
                    .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Monogram(link.siteName.ifEmpty { UrlTools.domainOf(link.url) })
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    link.title.ifEmpty { link.url },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val site = link.siteName.ifEmpty { UrlTools.domainOf(link.url) }
                val sub = listOfNotNull(site, relativeTimeFr(System.currentTimeMillis(), link.createdAt))
                Text(
                    sub.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showFolder && folderName != null) {
                    Text(
                        folderName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (link.favorite) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = "Favori",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            RowMenu(link, onToggleFavorite, onToggleReadLater, onCopy, onShare, onArchive, onDelete)
        }
    }
}

@Composable
private fun Monogram(site: String) {
    Box(
        modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            site.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun RowMenu(
    link: LinkEntity,
    onToggleFavorite: () -> Unit,
    onToggleReadLater: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }

    fun item(
        label: String,
        action: () -> Unit,
    ): @Composable () -> Unit =
        {
            DropdownMenuItem(
                text = { Text(label) },
                onClick = {
                    open = false
                    action()
                },
            )
        }

    IconButton(onClick = { open = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = "Plus d'actions")
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        item(if (link.favorite) "Retirer des favoris" else "Ajouter aux favoris", onToggleFavorite)()
        item(if (link.readLater) "Retirer de « À lire »" else "À lire plus tard", onToggleReadLater)()
        item("Copier le lien", onCopy)()
        item("Partager", onShare)()
        item("Archiver", onArchive)()
        item("Supprimer", onDelete)()
    }
}
