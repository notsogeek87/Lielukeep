package com.lielu.keep.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.lielu.keep.data.links.UrlTools

/** Feuille du bas ouverte par le menu Partager : aperçu du lien, titre, choix du dossier, bouton Enregistrer. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShareSheet(
    state: ShareUiState,
    onTitleChange: (String) -> Unit,
    onFolderSelected: (Long?) -> Unit,
    onCreateFolder: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Enregistrer le lien", style = MaterialTheme.typography.titleLarge)

            if (state.url == null) {
                Text(
                    "Aucun lien trouvé dans ce partage.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onDismiss) { Text("Fermer") }
                return@Column
            }

            Column {
                Text(UrlTools.domainOf(state.url), style = MaterialTheme.typography.titleMedium)
                Text(
                    state.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }

            OutlinedTextField(
                value = state.title,
                onValueChange = onTitleChange,
                label = { Text("Titre (facultatif)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                keyboardOptions =
                    KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            )

            Text("Dossier", style = MaterialTheme.typography.labelLarge)
            FolderChips(state, onFolderSelected, onCreateFolder)

            if (state.duplicate != null) {
                Text(
                    "Ce lien est déjà enregistré.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(onClick = onSave, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Check, contentDescription = null)
                Text(
                    if (state.duplicate != null) "Enregistrer quand même" else "Enregistrer",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FolderChips(
    state: ShareUiState,
    onFolderSelected: (Long?) -> Unit,
    onCreateFolder: (String) -> Unit,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    var newName by rememberSaveable { mutableStateOf("") }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = state.selectedFolderId == null,
            onClick = { onFolderSelected(null) },
            label = { Text("Sans dossier") },
        )
        state.folders.forEach { folder ->
            FilterChip(
                selected = state.selectedFolderId == folder.id,
                onClick = { onFolderSelected(folder.id) },
                label = { Text(listOfNotNull(folder.emoji, folder.name).joinToString(" ")) },
            )
        }
        FilterChip(
            selected = false,
            onClick = { creating = true },
            label = { Text("Nouveau dossier") },
            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
        )
    }

    if (creating) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("Nom du dossier") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions =
                    KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            )
            TextButton(
                onClick = {
                    onCreateFolder(newName)
                    newName = ""
                    creating = false
                },
                enabled = newName.isNotBlank(),
            ) { Text("Créer") }
        }
    }
}
