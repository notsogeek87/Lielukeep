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

/** Mots de la recherche : sans accents ni casse, ponctuation traitée comme un espace (« d'Afrique » = « d Afrique »). */
fun searchWords(query: String): List<String> =
    normalizeForSearch(query).split(nonAlphanumeric).filter { it.isNotEmpty() }

private val nonAlphanumeric = Regex("[^\\p{L}\\p{N}]+")

/**
 * Garde les liens qui passent le filtre ET correspondent à la recherche, la plus large possible : les mots sont
 * cherchés (sans accents, casse ni ponctuation) dans le titre, l'URL, la note, le site, les étiquettes et les noms de
 * dossiers. On exige d'abord tous les mots ; si rien ne correspond, on retombe sur « au moins un mot », les liens
 * qui en contiennent le plus venant en premier. Sans recherche : du plus récent au plus ancien.
 */
fun filterLinks(
    bookmarks: List<BookmarkItem>,
    lists: List<SavedListItem>,
    membership: Map<String, Set<String>>,
    filter: LinkFilter,
    query: String
): List<BookmarkItem> {
    val folderMembers = (filter as? LinkFilter.Folder)?.let { membersOf(it.id, lists, membership) }
    val inFilter = bookmarks.filter { bookmark ->
        when (filter) {
            LinkFilter.All -> true
            LinkFilter.Favorites -> bookmark.favourited
            LinkFilter.ReadLater -> bookmark.isReadLater()
            is LinkFilter.Folder -> bookmark.id in folderMembers.orEmpty()
        }
    }
    val words = searchWords(query)
    if (words.isEmpty()) return sortNewestFirst(inFilter)

    val folderNamesByLink = folderNamesByLink(lists, membership)
    val scored = inFilter.map { it to matchCount(it, folderNamesByLink[it.id].orEmpty(), words) }
    val all = scored.filter { it.second == words.size }
    val chosen = if (all.isNotEmpty()) all else scored.filter { it.second > 0 }
    return chosen
        .sortedWith(compareByDescending<Pair<BookmarkItem, Int>> { it.second }.thenByDescending { parseIsoMillis(it.first.createdAt) })
        .map { it.first }
}

private fun folderNamesByLink(lists: List<SavedListItem>, membership: Map<String, Set<String>>): Map<String, String> {
    val names = foldersOf(lists).associate { it.id to it.name }
    val result = mutableMapOf<String, String>()
    membership.forEach { (folderId, linkIds) ->
        val name = names[folderId] ?: return@forEach
        linkIds.forEach { id -> result[id] = result[id]?.let { "$it $name" } ?: name }
    }
    return result
}

private fun matchCount(bookmark: BookmarkItem, folderNames: String, words: List<String>): Int {
    val haystack = normalizeForSearch(
        (listOf(bookmark.title, bookmark.linkUrl, bookmark.note, bookmark.subtitle, folderNames) + bookmark.tags)
            .joinToString(" ") { it.orEmpty() }
    ).replace(nonAlphanumeric, " ")
    return words.count { it in haystack }
}
