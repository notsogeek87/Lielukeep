package com.lielu.keep.data.links

import com.lielu.keep.data.db.FolderEntity
import com.lielu.keep.data.db.LinkEntity
import java.text.Normalizer

/** Sélection active sur l'accueil (puces sous la recherche). */
sealed interface LinkFilter {
    data object All : LinkFilter

    data object Favorites : LinkFilter

    data object ReadLater : LinkFilter

    /** Un dossier ET ses sous-dossiers. */
    data class Folder(
        val id: Long,
    ) : LinkFilter
}

/** Identifiants d'un dossier et de ses descendants. */
fun folderAndChildrenIds(
    id: Long,
    folders: List<FolderEntity>,
): Set<Long> {
    val ids = mutableSetOf(id)
    var added = true
    while (added) {
        added = folders.filter { it.parentId in ids }.map { it.id }.let { ids.addAll(it) }
    }
    return ids
}

/** Garde les liens qui passent le filtre ET contiennent tous les mots de la recherche (sans accents ni casse). */
fun filterLinks(
    links: List<LinkEntity>,
    folders: List<FolderEntity>,
    filter: LinkFilter,
    query: String,
): List<LinkEntity> {
    val folderIds = (filter as? LinkFilter.Folder)?.let { folderAndChildrenIds(it.id, folders) }
    val words = normalizeForSearch(query).split(' ').filter { it.isNotEmpty() }
    return links.filter { link ->
        val inFilter =
            when (filter) {
                LinkFilter.All -> true
                LinkFilter.Favorites -> link.favorite
                LinkFilter.ReadLater -> link.readLater
                is LinkFilter.Folder -> link.folderId in folderIds.orEmpty()
            }
        inFilter && (words.isEmpty() || matches(link, words))
    }
}

private fun matches(
    link: LinkEntity,
    words: List<String>,
): Boolean {
    val haystack = normalizeForSearch("${link.title} ${link.url} ${link.note} ${link.siteName} ${link.description}")
    return words.all { it in haystack }
}

private val accents = Regex("\\p{InCombiningDiacriticalMarks}+")

fun normalizeForSearch(text: String): String =
    Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD).replace(accents, "")
