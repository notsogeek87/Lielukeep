package com.ruizlenato.karabau.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ruizlenato.karabau.R
import com.ruizlenato.karabau.data.model.BookmarkItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkDetailBottomSheet(
    bookmark: BookmarkItem,
    onDismiss: () -> Unit,
    onOpenLink: (String) -> Unit,
    onShare: (BookmarkItem) -> Unit,
    onEdit: (BookmarkItem) -> Unit,
    onDelete: (BookmarkItem) -> Unit,
    onToggleFavourite: (BookmarkItem) -> Unit,
    onToggleArchived: (BookmarkItem) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val clipboardManager = remember(context) { context.getSystemService(ClipboardManager::class.java) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        if (!bookmark.imageUrl.isNullOrBlank()) {
            val imageRequest = remember(bookmark.imageUrl) {
                ImageRequest.Builder(context)
                    .data(bookmark.imageUrl)
                    .crossfade(true)
                    .build()
            }
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(24.dp))
            ) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Léger dégradé vers le bas : l'image se fond dans la feuille au lieu de s'arrêter net.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.35f))
                            )
                        )
                )
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            val titleText = bookmark.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.untitled_link)
            Text(
                text = titleText,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 20.dp)
            )

            if (!bookmark.subtitle.isNullOrBlank()) {
                Text(
                    text = bookmark.subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 10,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            if (!bookmark.note.isNullOrBlank()) {
                Text(
                    text = bookmark.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 10,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            if (bookmark.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    bookmark.tags.forEach { tag ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text(tag) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            shape = MaterialTheme.shapes.small
                        )
                    }
                }
            }

            if (!bookmark.linkUrl.isNullOrBlank()) {
                val displayUrl = bookmark.linkUrl
                    .removePrefix("https://")
                    .removePrefix("http://")

                Row(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedCard(
                        onClick = { onOpenLink(bookmark.linkUrl) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInBrowser,
                                contentDescription = stringResource(R.string.open_link),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = displayUrl,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(top = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                BottomSheetActionItem(
                    icon = if (bookmark.favourited) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    label = stringResource(R.string.action_favourite),
                    active = bookmark.favourited,
                    contentColor = if (bookmark.favourited) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { onToggleFavourite(bookmark) }
                )

                BottomSheetActionItem(
                    icon = Icons.Outlined.Edit,
                    label = stringResource(R.string.menu_edit),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { onEdit(bookmark) }
                )

                BottomSheetActionItem(
                    icon = Icons.Outlined.ContentCopy,
                    label = stringResource(R.string.copy),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = {
                        bookmark.linkUrl?.let { url ->
                            clipboardManager?.setPrimaryClip(ClipData.newPlainText("bookmark_link", url))
                        }
                    }
                )

                BottomSheetActionItem(
                    icon = Icons.Outlined.Share,
                    label = stringResource(R.string.menu_share),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { onShare(bookmark) }
                )

                BottomSheetActionItem(
                    icon = Icons.Outlined.Archive,
                    label = stringResource(R.string.action_archive),
                    active = bookmark.archived,
                    contentColor = if (bookmark.archived) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { onToggleArchived(bookmark) }
                )

                BottomSheetActionItem(
                    icon = Icons.Outlined.DeleteOutline,
                    label = stringResource(R.string.menu_delete),
                    destructive = true,
                    contentColor = MaterialTheme.colorScheme.error,
                    onClick = { onDelete(bookmark) }
                )
            }

            Row(
                modifier = Modifier
                    .padding(top = 16.dp, bottom = 24.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (bookmark.archived) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = stringResource(R.string.archived),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                val formattedDate = remember(bookmark.createdAt) {
                    try {
                        val instant = Instant.parse(bookmark.createdAt)
                        val localDateTime = instant.atZone(ZoneId.systemDefault())
                        val locale = Locale.getDefault()
                        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                            .withLocale(locale)
                        localDateTime.format(formatter)
                    } catch (_: Exception) {
                        bookmark.createdAt
                    }
                }

                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.navigationBarsPadding())
    }
}

@Composable
private fun BottomSheetActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    contentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    active: Boolean = false,
    destructive: Boolean = false
) {
    // Disque tonal : l'état actif (favori, archivé) se voit sans lire le libellé.
    val container = when {
        active && contentColor == MaterialTheme.colorScheme.onTertiaryContainer -> MaterialTheme.colorScheme.tertiaryContainer
        active -> MaterialTheme.colorScheme.primaryContainer
        destructive -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(shape = CircleShape, color = container, modifier = Modifier.size(52.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
