package com.ruizlenato.karabau.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ruizlenato.karabau.R
import com.ruizlenato.karabau.data.links.canHaveSubfolder
import com.ruizlenato.karabau.data.links.folderLabel
import com.ruizlenato.karabau.data.links.foldersOf
import com.ruizlenato.karabau.data.links.isValidNewFolderName
import com.ruizlenato.karabau.data.links.orderedFolders
import com.ruizlenato.karabau.data.model.SavedListItem
import kotlinx.coroutines.launch

/** Choix du dossier (liste Karakeep manuelle) à l'enregistrement d'un lien, avec création à la volée. */
@Composable
internal fun FolderPickerSection(
    lists: List<SavedListItem>,
    selectedFolderId: String?,
    onSelect: (String?) -> Unit,
    onCreateFolder: suspend (name: String, parentId: String?) -> String?
) {
    var isCreateOpen by rememberSaveable { mutableStateOf(false) }

    Column {
        Text(
            text = stringResource(R.string.folder_label),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFolderId == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.folder_none)) }
            )
            orderedFolders(lists).forEach { (folder, _) ->
                FilterChip(
                    selected = selectedFolderId == folder.id,
                    onClick = { onSelect(folder.id) },
                    label = { Text(folderLabel(folder, lists)) }
                )
            }
            AssistChip(
                onClick = { isCreateOpen = true },
                label = { Text(stringResource(R.string.folder_new_chip)) }
            )
        }
    }

    if (isCreateOpen) {
        NewFolderDialog(
            lists = lists,
            initialParentId = null,
            onDismiss = { isCreateOpen = false },
            onCreate = onCreateFolder
        )
    }
}

/**
 * Création d'un dossier. [onCreate] renvoie un message d'erreur à afficher, ou null quand c'est fait.
 * Les sous-dossiers sont limités à 2 niveaux : seul un dossier de premier niveau peut servir de parent.
 */
@Composable
internal fun NewFolderDialog(
    lists: List<SavedListItem>,
    initialParentId: String?,
    onDismiss: () -> Unit,
    onCreate: suspend (name: String, parentId: String?) -> String?
) {
    var name by rememberSaveable { mutableStateOf("") }
    var parentId by rememberSaveable { mutableStateOf(initialParentId) }
    var error by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val parents = remember(lists) { foldersOf(lists).filter { canHaveSubfolder(it, lists) } }
    val nameTaken = name.isNotBlank() && !isValidNewFolderName(name, parentId, lists)

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(stringResource(R.string.folder_new_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.folder_name_label)) },
                    singleLine = true,
                    isError = nameTaken || error != null,
                    supportingText = {
                        val message = if (nameTaken) stringResource(R.string.folder_name_taken) else error
                        if (message != null) Text(message)
                    }
                )
                if (parents.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.folder_parent_label),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = parentId == null,
                            onClick = { parentId = null },
                            label = { Text(stringResource(R.string.folder_parent_none)) }
                        )
                        parents.forEach { parent ->
                            FilterChip(
                                selected = parentId == parent.id,
                                onClick = { parentId = parent.id },
                                label = { Text(parent.name) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving && name.isNotBlank() && !nameTaken,
                onClick = {
                    scope.launch {
                        isSaving = true
                        val failure = onCreate(name.trim(), parentId)
                        isSaving = false
                        if (failure == null) onDismiss() else error = failure
                    }
                }
            ) {
                Text(stringResource(R.string.folder_create))
            }
        },
        dismissButton = {
            TextButton(enabled = !isSaving, onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

/** Renommage d'un dossier ; [onRename] renvoie un message d'erreur ou null. */
@Composable
internal fun RenameFolderDialog(
    folder: SavedListItem,
    lists: List<SavedListItem>,
    onDismiss: () -> Unit,
    onRename: suspend (name: String) -> String?
) {
    var name by rememberSaveable { mutableStateOf(folder.name) }
    var error by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val others = remember(lists, folder) { lists.filterNot { it.id == folder.id } }
    val nameTaken = name.isNotBlank() && !isValidNewFolderName(name, folder.parentId, others)

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(stringResource(R.string.folder_rename_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    error = null
                },
                label = { Text(stringResource(R.string.folder_name_label)) },
                singleLine = true,
                isError = nameTaken || error != null,
                supportingText = {
                    val message = if (nameTaken) stringResource(R.string.folder_name_taken) else error
                    if (message != null) Text(message)
                }
            )
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving && name.isNotBlank() && !nameTaken && name.trim() != folder.name,
                onClick = {
                    scope.launch {
                        isSaving = true
                        val failure = onRename(name.trim())
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
