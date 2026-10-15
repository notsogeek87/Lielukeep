package com.ruizlenato.karabau.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ruizlenato.karabau.R
import com.ruizlenato.karabau.data.links.UrlTools
import com.ruizlenato.karabau.data.links.normalizeForSearch
import com.ruizlenato.karabau.data.model.BookmarkItem
import com.ruizlenato.karabau.data.model.SavedListItem
import kotlinx.coroutines.launch

/** Nombre maximal de suggestions d'étiquettes affichées sous le champ de saisie. */
private const val MAX_TAG_SUGGESTIONS = 12

/**
 * Modification d'un lien : titre, URL, note, dossier et étiquettes.
 * [onSave] renvoie un message d'erreur à afficher, ou null quand c'est enregistré (le dialogue se ferme alors).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditBookmarkDialog(
    bookmark: BookmarkItem,
    lists: List<SavedListItem>,
    knownTags: List<String>,
    initialFolderId: String?,
    onDismiss: () -> Unit,
    onCreateFolder: suspend (name: String, parentId: String?) -> String?,
    onSave: suspend (title: String, url: String, note: String, tags: List<String>, folderId: String?) -> String?
) {
    var title by rememberSaveable { mutableStateOf(bookmark.title.orEmpty()) }
    var url by rememberSaveable { mutableStateOf(bookmark.linkUrl.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(bookmark.note.orEmpty()) }
    var folderId by rememberSaveable { mutableStateOf(initialFolderId) }
    var tags by rememberSaveable { mutableStateOf(bookmark.tags.toList()) }
    var tagInput by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val hasUrl = bookmark.linkUrl != null
    val urlInvalid = hasUrl && UrlTools.extractUrl(url.trim()) != url.trim()

    fun addTag(raw: String) {
        val name = raw.trim().trimStart('#')
        if (name.isEmpty()) return
        val existing = knownTags.firstOrNull { normalizeForSearch(it) == normalizeForSearch(name) } ?: name
        if (tags.none { normalizeForSearch(it) == normalizeForSearch(existing) }) tags = tags + existing
        tagInput = ""
    }

    fun save() {
        if (isSaving || urlInvalid) return
        // Une étiquette tapée mais pas encore validée compte quand même.
        val pending = tagInput.trim().trimStart('#')
        val finalTags = if (pending.isEmpty()) tags else tags + pending
        isSaving = true
        error = null
        scope.launch {
            val message = onSave(title, url, note, finalTags, folderId)
            isSaving = false
            if (message == null) onDismiss() else error = message
        }
    }

    val suggestions = remember(knownTags, tags, tagInput) {
        val query = normalizeForSearch(tagInput)
        knownTags
            .filter { name -> tags.none { normalizeForSearch(it) == normalizeForSearch(name) } }
            .filter { query.isEmpty() || normalizeForSearch(it).contains(query) }
            .take(MAX_TAG_SUGGESTIONS)
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(stringResource(R.string.edit_link_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.edit_title_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (hasUrl) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text(stringResource(R.string.edit_url_label)) },
                        singleLine = true,
                        isError = urlInvalid,
                        supportingText = { if (urlInvalid) Text(stringResource(R.string.edit_url_invalid)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.edit_note_label)) },
                    minLines = 2,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))
                FolderPickerSection(
                    lists = lists,
                    selectedFolderId = folderId,
                    onSelect = { folderId = it },
                    onCreateFolder = onCreateFolder
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.tags_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tags.forEach { tag ->
                            InputChip(
                                selected = true,
                                onClick = { tags = tags - tag },
                                label = { Text(tag) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.edit_tag_remove, tag)
                                    )
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = tagInput,
                        onValueChange = { tagInput = it },
                        label = { Text(stringResource(R.string.edit_tag_input)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { addTag(tagInput) }, enabled = tagInput.isNotBlank()) {
                        Text(stringResource(R.string.edit_tag_add))
                    }
                }
                if (suggestions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        suggestions.forEach { name ->
                            AssistChip(onClick = { addTag(name) }, label = { Text(name) })
                        }
                    }
                }

                error?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = ::save, enabled = !isSaving && !urlInvalid) {
                Text(stringResource(R.string.edit_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
