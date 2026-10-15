package com.ruizlenato.karabau.data.links

import com.ruizlenato.karabau.data.model.BookmarkItem
import com.ruizlenato.karabau.data.model.SavedListItem
import java.text.Normalizer

/** Étiquette Karakeep qui porte « À lire plus tard » (Karakeep n'a pas cette notion). */
const val READ_LATER_TAG = "à lire"

/** Sélection active sur l'accueil (puces sous la recherche). */
sealed interface LinkFilter {
    data object All : LinkFilter

    data object Favorites : LinkFilter

    data object ReadLater : LinkFilter

    /** Une liste Karakeep (« dossier ») ET ses sous-dossiers. */
    data class Folder(val id: String) : LinkFilter
}

/** Une puce de dossier : le dossier, son niveau d'imbrication et le nombre de liens (sous-dossiers compris). */
data class FolderChip(val folder: SavedListItem, val depth: Int, val count: Int)

private val accents = Regex("\\p{InCombiningDiacriticalMarks}+")

/** Minuscules et sans accents, pour comparer « À lire » et « a lire ». */
fun normalizeForSearch(text: String): String =
    Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD).replace(accents, "")

fun isReadLaterTag(name: String): Boolean = normalizeForSearch(name) == normalizeForSearch(READ_LATER_TAG)

fun BookmarkItem.isReadLater(): Boolean = tags.any(::isReadLaterTag)

/** Site affiché sur la ligne : éditeur/auteur fourni par Karakeep, sinon le domaine de l'URL. */
fun BookmarkItem.siteName(): String =
    subtitle?.takeIf { it.isNotBlank() } ?: UrlTools.domainOf(linkUrl.orEmpty())

/** Seules les listes manuelles dont l'utilisateur est propriétaire servent de dossiers. */
fun foldersOf(lists: List<SavedListItem>): List<SavedListItem> =
    lists.filter { it.type == "manual" && it.userRole == "owner" }

/** Dossiers dans l'ordre d'affichage : chaque dossier suivi de ses sous-dossiers, par nom. */
fun orderedFolders(lists: List<SavedListItem>): List<Pair<SavedListItem, Int>> {
    val folders = foldersOf(lists)
    val ids = folders.map { it.id }.toSet()
    val byParent = folders.groupBy { it.parentId?.takeIf { parent -> parent in ids } }
    val result = mutableListOf<Pair<SavedListItem, Int>>()

    fun visit(parent: String?, depth: Int, seen: Set<String>) {
        byParent[parent].orEmpty().sortedBy { normalizeForSearch(it.name) }.forEach { folder ->
            if (folder.id in seen) return@forEach
            result += folder to depth
            visit(folder.id, depth + 1, seen + folder.id)
        }
    }
    visit(null, 0, emptySet())
    return result
}

/** On ne crée un sous-dossier que sous un dossier de premier niveau (2 niveaux au maximum). */
fun canHaveSubfolder(folder: SavedListItem, lists: List<SavedListItem>): Boolean =
    folder.type == "manual" && folder.userRole == "owner" &&
        (folder.parentId == null || lists.none { it.id == folder.parentId })

/** Identifiants d'un dossier et de ses descendants. */
fun folderAndChildrenIds(id: String, lists: List<SavedListItem>): Set<String> {
    val ids = mutableSetOf(id)
    var added = true
    while (added) {
        added = lists.filter { it.parentId in ids }.map { it.id }.let { ids.addAll(it) }
    }
    return ids
}

private fun membersOf(id: String, lists: List<SavedListItem>, membership: Map<String, Set<String>>): Set<String> =
    folderAndChildrenIds(id, lists).flatMapTo(mutableSetOf()) { membership[it].orEmpty() }

/** Puces de dossier avec leur compteur (liens dans `bookmarks` seulement, donc cohérent avec la liste affichée). */
fun folderChips(
    bookmarks: List<BookmarkItem>,
    lists: List<SavedListItem>,
    membership: Map<String, Set<String>>
): List<FolderChip> {
    val shownIds = bookmarks.mapTo(mutableSetOf()) { it.id }
    return orderedFolders(lists).map { (folder, depth) ->
        FolderChip(folder, depth, membersOf(folder.id, lists, membership).count { it in shownIds })
    }
}

/** Du plus récent au plus ancien. */
fun sortNewestFirst(bookmarks: List<BookmarkItem>): List<BookmarkItem> =
    bookmarks.sortedByDescending { parseIsoMillis(it.createdAt) }

/**
 * Garde les liens qui passent le filtre ET contiennent tous les mots de la recherche (sans accents ni casse, dans
 * le titre, l'URL, la note et le site), du plus récent au plus ancien.
 */
fun filterLinks(
    bookmarks: List<BookmarkItem>,
    lists: List<SavedListItem>,
    membership: Map<String, Set<String>>,
    filter: LinkFilter,
    query: String
): List<BookmarkItem> {
    val folderMembers = (filter as? LinkFilter.Folder)?.let { membersOf(it.id, lists, membership) }
    val words = normalizeForSearch(query).split(' ').filter { it.isNotEmpty() }
    return sortNewestFirst(
        bookmarks.filter { bookmark ->
            val inFilter = when (filter) {
                LinkFilter.All -> true
                LinkFilter.Favorites -> bookmark.favourited
                LinkFilter.ReadLater -> bookmark.isReadLater()
                is LinkFilter.Folder -> bookmark.id in folderMembers.orEmpty()
            }
            inFilter && (words.isEmpty() || matches(bookmark, words))
        }
    )
}

private fun matches(bookmark: BookmarkItem, words: List<String>): Boolean {
    val haystack = normalizeForSearch(
        listOf(bookmark.title, bookmark.linkUrl, bookmark.note, bookmark.subtitle).joinToString(" ") { it.orEmpty() }
    )
    return words.all { it in haystack }
}
