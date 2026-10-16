package com.ruizlenato.karabau.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.ruizlenato.karabau.data.links.folderAndChildrenIds
import com.ruizlenato.karabau.data.links.folderLabel
import com.ruizlenato.karabau.data.links.orderedFolders
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ruizlenato.karabau.R
import com.ruizlenato.karabau.data.links.normalizeForSearch
import com.ruizlenato.karabau.data.model.SavedListItem
import com.ruizlenato.karabau.data.model.TagItem
import kotlinx.coroutines.launch

/** Actions de la multisélection ; chaque `onApply…` renvoie un message d'erreur à afficher, ou null. */
internal class BatchActions(
    val lists: List<SavedListItem>,
    val tags: List<TagItem>,
    val onApplyFolder: suspend (ids: Collection<String>, folderId: String?) -> String?,
    val onApplyTag: suspend (ids: Collection<String>, tagName: String) -> String?,
    val onCreateFolder: suspend (name: String, parentId: String?) -> String?
)

/**
 * Choix d'une étiquette existante ou saisie d'un nouveau nom. [exclude] masque l'étiquette source d'un transfert.
 * [onPick] renvoie un message d'erreur (détail du serveur) ou null quand c'est fait.
 */
@Composable
internal fun TagPickerDialog(
    title: String,
    tags: List<TagItem>,
    exclude: TagItem? = null,
    onDismiss: () -> Unit,
    onPick: suspend (name: String) -> String?
) {
    var query by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isWorking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val trimmed = query.trim()
    val needle = normalizeForSearch(trimmed)
    val candidates = remember(tags, exclude, needle) {
        tags.filter { it.id != exclude?.id && (needle.isEmpty() || normalizeForSearch(it.name).contains(needle)) }
    }
    val exactExists = tags.any { normalizeForSearch(it.name) == needle }
    val canCreate = trimmed.isNotEmpty() && !exactExists

    fun pick(name: String) {
        if (isWorking) return
        scope.launch {
            isWorking = true
            error = null
            val failure = onPick(name)
            isWorking = false
            if (failure == null) onDismiss() else error = failure
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isWorking) onDismiss() },
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.tag_name_label)) },
                    singleLine = true,
                    enabled = !isWorking,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isWorking) {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
                }
                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                LazyColumn(modifier = Modifier.heightIn(max = 280.dp).padding(top = 8.dp)) {
                    if (canCreate) {
                        item(key = "create") {
                            Text(
                                text = stringResource(R.string.tag_create_named, trimmed),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isWorking) { pick(trimmed) }
                                    .padding(vertical = 12.dp)
                            )
                        }
                    }
                    items(candidates, key = { it.id }) { tag ->
                        Text(
                            text = tag.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isWorking) { pick(tag.name) }
                                .padding(vertical = 12.dp)
                        )
                    }
                    if (candidates.isEmpty() && !canCreate) {
                        item(key = "empty") {
                            Text(
                                text = stringResource(R.string.tag_none_other),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(enabled = !isWorking, onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

/** Choix du dossier à appliquer à la sélection ; « Aucun dossier » retire les liens de leurs dossiers. */
@Composable
internal fun FolderApplyDialog(
    count: Int,
    lists: List<SavedListItem>,
    onCreateFolder: suspend (name: String, parentId: String?) -> String?,
    onDismiss: () -> Unit,
    onApply: suspend (folderId: String?) -> String?
) {
    var chosen by remember { mutableStateOf<String?>(null) }
    var touched by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var isWorking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isWorking) onDismiss() },
        title = { Text(stringResource(R.string.selection_folder_title, count)) },
        text = {
            Column {
                FolderPickerSection(
                    lists = lists,
                    selectedFolderId = chosen,
                    onSelect = {
                        chosen = it
                        touched = true
                        error = null
                    },
                    onCreateFolder = onCreateFolder
                )
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
                enabled = touched && !isWorking,
                onClick = {
                    scope.launch {
                        isWorking = true
                        val failure = onApply(chosen)
                        isWorking = false
                        if (failure == null) onDismiss() else error = failure
                    }
                }
            ) {
                Text(stringResource(R.string.selection_apply))
            }
        },
        dismissButton = {
            TextButton(enabled = !isWorking, onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

/**
 * Choix du dossier qui reçoit les liens de [source] avant sa suppression (le dossier et ses sous-dossiers sont exclus).
 * [onPick] renvoie un message d'erreur ou null quand le transfert (et la suppression qui suit) est terminé.
 */
@Composable
internal fun FolderTransferDialog(
    title: String,
    source: SavedListItem,
    lists: List<SavedListItem>,
    onCreateFolder: (suspend (name: String, parentId: String?) -> String?)?,
    onDismiss: () -> Unit,
    onPick: suspend (targetId: String) -> String?
) {
    var error by remember { mutableStateOf<String?>(null) }
    var isWorking by remember { mutableStateOf(false) }
    var isCreateOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val excluded = remember(source, lists) { folderAndChildrenIds(source.id, lists) }
    val targets = remember(lists, excluded) { orderedFolders(lists).map { it.first }.filter { it.id !in excluded } }

    AlertDialog(
        onDismissRequest = { if (!isWorking) onDismiss() },
        title = { Text(title) },
        text = {
            Column {
                if (isWorking) CircularProgressIndicator(modifier = Modifier.padding(bottom = 12.dp))
                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    if (onCreateFolder != null) {
                        item(key = "new") {
                            Text(
                                text = stringResource(R.string.folder_new_chip),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isWorking) { isCreateOpen = true }
                                    .padding(vertical = 12.dp)
                            )
                        }
                    }
                    items(targets, key = { it.id }) { folder ->
                        Text(
                            text = folderLabel(folder, lists),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isWorking) {
                                    scope.launch {
                                        isWorking = true
                                        error = null
                                        val failure = onPick(folder.id)
                                        isWorking = false
                                        if (failure == null) onDismiss() else error = failure
                                    }
                                }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(enabled = !isWorking, onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )

    if (isCreateOpen && onCreateFolder != null) {
        NewFolderDialog(
            lists = lists,
            initialParentId = null,
            onDismiss = { isCreateOpen = false },
            onCreate = onCreateFolder
        )
    }
}
