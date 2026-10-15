package com.ruizlenato.karabau.data.links

import com.ruizlenato.karabau.data.model.SavedListItem

/** URL à enregistrer pour un texte partagé : première URL http(s) trouvée, sans paramètres de suivi. */
fun urlFromSharedText(text: String?): String? = UrlTools.extractUrl(text)?.let(UrlTools::clean)

/** Dossier présélectionné : le dernier utilisé, s'il existe encore parmi les dossiers de l'utilisateur. */
fun initialFolderId(lastFolderId: String?, lists: List<SavedListItem>): String? =
    lastFolderId?.takeIf { id -> foldersOf(lists).any { it.id == id } }

/** Libellé d'un dossier dans un sélecteur : « Parent › Enfant » pour un sous-dossier. */
fun folderLabel(folder: SavedListItem, lists: List<SavedListItem>): String {
    val parent = lists.firstOrNull { it.id == folder.parentId } ?: return folder.name
    return "${parent.name} › ${folder.name}"
}

/** Un nom de dossier valide n'est pas vide et n'existe pas déjà au même niveau (sans accents ni casse). */
fun isValidNewFolderName(name: String, parentId: String?, lists: List<SavedListItem>): Boolean {
    val wanted = normalizeForSearch(name)
    return wanted.isNotEmpty() && foldersOf(lists).none {
        it.parentId == parentId && normalizeForSearch(it.name) == wanted
    }
}
