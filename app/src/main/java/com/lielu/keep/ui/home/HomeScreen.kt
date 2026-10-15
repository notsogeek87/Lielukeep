package com.lielu.keep.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lielu.keep.R
import com.lielu.keep.data.links.LinkFilter
import com.lielu.keep.data.links.LinksRepository
import com.lielu.keep.data.links.UrlTools
import com.lielu.keep.ui.share.ShareSheet
import com.lielu.keep.ui.share.ShareViewModel
import kotlinx.coroutines.launch

/** Accueil : la liste des liens sauvegardés, avec recherche, puces de dossiers et ajout rapide. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    linksRepository: LinksRepository,
    onSettingsClick: () -> Unit,
    onServerClick: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsState()
    val showWelcome by viewModel.showWelcome.collectAsState()
    val message by viewModel.message.collectAsState()
    val configured by viewModel.configured.collectAsState()
    val sync by viewModel.syncStatus.collectAsState()
    var folderDialog by remember { mutableStateOf<FolderDialog?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    var addDialogUrl by remember { mutableStateOf<String?>(null) }
    var addUrl by remember { mutableStateOf<String?>(null) }

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) viewModel.onImportRequested(context.contentResolver, uri)
        }

    // Récupère les changements faits ailleurs (web, autre appareil) à chaque retour dans l'app.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.sync() }

    LaunchedEffect(sync.lastError) {
        sync.lastError?.let { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.onMessageShown()
    }

    fun showUndo(
        text: String,
        onUndo: () -> Unit,
    ) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(text, "Annuler", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) onUndo()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Paramètres")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { addDialogUrl = clipboardUrl(context).orEmpty() }) {
                Icon(Icons.Default.Add, contentDescription = "Ajouter un lien")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (!configured) ConnectBanner(onServerClick)
            SearchField(state.query, viewModel::onQueryChange)
            FilterRow(state, viewModel::onFilterSelected, onCreateFolder = { folderDialog = FolderDialog.Create(null) })
            (state.filter as? LinkFilter.Folder)?.let { selected ->
                state.folderOptions.firstOrNull { it.folder.id == selected.id }?.let { option ->
                    FolderActions(
                        name = option.folder.name,
                        canAddSubfolder = option.folder.parentId == null,
                        onRename = { folderDialog = FolderDialog.Rename(option.folder.id, option.folder.name) },
                        onAddSubfolder = { folderDialog = FolderDialog.Create(option.folder.id) },
                        onDelete = { folderDialog = FolderDialog.Delete(option.folder.id, option.folder.name) },
                    )
                }
            }
            PullToRefreshBox(
                isRefreshing = sync.syncing,
                onRefresh = viewModel::sync,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    !state.loaded -> Unit
                    state.links.isEmpty() -> EmptyState(hasLinks = state.totalCount > 0)
                    else ->
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 88.dp),
                        ) {
                            items(state.links, key = { it.id }) { link ->
                                LinkRow(
                                    link = link,
                                    folderName = link.folderId?.let { state.folderNames[it] },
                                    showFolder = state.filter !is LinkFilter.Folder,
                                    onOpen = {
                                        openLink(context, link.url)
                                        viewModel.onOpened(link.id)
                                    },
                                    onToggleFavorite = { viewModel.toggleFavorite(link.id) },
                                    onToggleReadLater = { viewModel.toggleReadLater(link.id) },
                                    onCopy = {
                                        copyText(context, link.url)
                                        scope.launch { snackbarHostState.showSnackbar("Lien copié") }
                                    },
                                    onShare = { shareText(context, link.url) },
                                    onArchive = {
                                        viewModel.archive(link.id)
                                        showUndo("Lien archivé") { viewModel.unarchive(link.id) }
                                    },
                                    onDelete = {
                                        viewModel.trash(link.id)
                                        showUndo("Lien supprimé") { viewModel.restoreFromTrash(link.id) }
                                    },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                }
            }
        }
    }

    folderDialog?.let { dialog ->
        FolderDialogs(
            dialog = dialog,
            onDismiss = { folderDialog = null },
            onCreate = { name, parentId ->
                folderDialog = null
                viewModel.createFolder(name, parentId)
            },
            onRename = { id, name ->
                folderDialog = null
                viewModel.renameFolder(id, name)
            },
            onDelete = { id ->
                folderDialog = null
                viewModel.deleteFolder(id)
            },
        )
    }

    addDialogUrl?.let { initial ->
        AddLinkDialog(
            initial = initial,
            onConfirm = { url ->
                addDialogUrl = null
                addUrl = url
            },
            onDismiss = { addDialogUrl = null },
        )
    }

    addUrl?.let { url ->
        val shareViewModel: ShareViewModel =
            viewModel(
                key = "add-$url",
                factory = viewModelFactory { initializer { ShareViewModel(linksRepository, url, null) } },
            )
        val shareState by shareViewModel.state.collectAsState()
        LaunchedEffect(shareState.saved) {
            if (!shareState.saved) return@LaunchedEffect
            addUrl = null
            val where = shareState.savedFolderName?.let { " dans « $it »" }.orEmpty()
            snackbarHostState.showSnackbar("Lien enregistré$where")
        }
        ShareSheet(
            state = shareState,
            onTitleChange = shareViewModel::onTitleChange,
            onFolderSelected = shareViewModel::onFolderSelected,
            onCreateFolder = shareViewModel::onCreateFolder,
            onSave = shareViewModel::onSave,
            onDismiss = { addUrl = null },
        )
    }

    if (showWelcome) {
        AlertDialog(
            onDismissRequest = viewModel::onWelcomeDismissed,
            title = { Text("Bienvenue") },
            text = {
                Text(
                    "Si vous réinstallez l'app ou changez d'appareil, vous pouvez restaurer vos données " +
                        "depuis un fichier de sauvegarde. Vous pourrez aussi le faire plus tard dans Paramètres.",
                )
            },
            confirmButton = {
                TextButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
                    Text("Restaurer un fichier…")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onWelcomeDismissed) { Text("Plus tard") }
            },
        )
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Rechercher un lien") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Effacer la recherche")
                }
            }
        },
        singleLine = true,
        shape = CircleShape,
        keyboardOptions =
            androidx.compose.foundation.text
                .KeyboardOptions(imeAction = ImeAction.Search),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
}

@Composable
private fun FilterRow(
    state: HomeUiState,
    onFilterSelected: (LinkFilter) -> Unit,
    onCreateFolder: () -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = state.filter == LinkFilter.All,
                onClick = { onFilterSelected(LinkFilter.All) },
                label = { Text("Tous ${state.totalCount}") },
            )
        }
        item {
            FilterChip(
                selected = state.filter == LinkFilter.Favorites,
                onClick = { onFilterSelected(LinkFilter.Favorites) },
                label = { Text("Favoris ${state.favoriteCount}") },
                leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) },
            )
        }
        item {
            FilterChip(
                selected = state.filter == LinkFilter.ReadLater,
                onClick = { onFilterSelected(LinkFilter.ReadLater) },
                label = { Text("À lire ${state.readLaterCount}") },
                leadingIcon = { Icon(Icons.Outlined.BookmarkAdd, contentDescription = null) },
            )
        }
        items(state.folderOptions, key = { it.folder.id }) { option ->
            FilterChip(
                selected = state.filter == LinkFilter.Folder(option.folder.id),
                onClick = { onFilterSelected(LinkFilter.Folder(option.folder.id)) },
                label = {
                    Text(listOfNotNull(option.folder.emoji, option.label, option.count.toString()).joinToString(" "))
                },
            )
        }
        item {
            FilterChip(
                selected = false,
                onClick = onCreateFolder,
                label = { Text("Dossier") },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
            )
        }
    }
}

@Composable
private fun ConnectBanner(onServerClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Connecte ton serveur Karakeep pour retrouver tes liens.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onServerClick) { Text("Connecter") }
    }
}

@Composable
private fun FolderActions(
    name: String,
    canAddSubfolder: Boolean,
    onRename: () -> Unit,
    onAddSubfolder: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        TextButton(onClick = onRename) { Text("Renommer") }
        if (canAddSubfolder) TextButton(onClick = onAddSubfolder) { Text("Sous-dossier") }
        TextButton(onClick = onDelete) { Text("Supprimer") }
    }
}

private sealed interface FolderDialog {
    data class Create(
        val parentId: Long?,
    ) : FolderDialog

    data class Rename(
        val id: Long,
        val currentName: String,
    ) : FolderDialog

    data class Delete(
        val id: Long,
        val name: String,
    ) : FolderDialog
}

@Composable
private fun FolderDialogs(
    dialog: FolderDialog,
    onDismiss: () -> Unit,
    onCreate: (String, Long?) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
) {
    if (dialog is FolderDialog.Delete) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Supprimer « ${dialog.name} » ?") },
            text = { Text("La liste est supprimée sur ton serveur. Les liens qu'elle contient sont conservés.") },
            confirmButton = { TextButton(onClick = { onDelete(dialog.id) }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
        )
        return
    }
    var name by remember { mutableStateOf((dialog as? FolderDialog.Rename)?.currentName.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (dialog is FolderDialog.Rename) "Renommer le dossier" else "Nouveau dossier") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nom du dossier") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    when (dialog) {
                        is FolderDialog.Rename -> onRename(dialog.id, name)
                        is FolderDialog.Create -> onCreate(name, dialog.parentId)
                        is FolderDialog.Delete -> Unit
                    }
                },
            ) { Text(if (dialog is FolderDialog.Rename) "Renommer" else "Créer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun EmptyState(hasLinks: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (hasLinks) "Aucun résultat" else "Aucun lien pour l'instant",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            if (hasLinks) {
                "Essaie un autre mot ou une autre sélection."
            } else {
                "Dans ton navigateur, ouvre une page, touche Partager, puis choisis Lielukeep.\n" +
                    "Ou touche + pour coller un lien copié."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun AddLinkDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter un lien") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    invalid = false
                },
                label = { Text("Adresse du lien") },
                placeholder = { Text("https://…") },
                isError = invalid,
                supportingText = { if (invalid) Text("Ce texte ne contient pas de lien valide.") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val url = UrlTools.extractUrl(text)
                    if (url == null) invalid = true else onConfirm(url)
                },
            ) { Text("Continuer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Première URL du presse-papiers, lue au tap sur « + » (l'app est au premier plan, donc c'est autorisé). */
private fun clipboardUrl(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val text =
        clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
    return UrlTools.extractUrl(text)
}

private fun copyText(
    context: Context,
    text: String,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Lien", text))
}

private fun shareText(
    context: Context,
    text: String,
) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null))
}

/** Onglet Chrome intégré : le retour ramène à la liste. Repli sur le navigateur si aucun n'est compatible. */
private fun openLink(
    context: Context,
    url: String,
) {
    val uri = android.net.Uri.parse(url)
    try {
        CustomTabsIntent
            .Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(context, uri)
    } catch (_: android.content.ActivityNotFoundException) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
