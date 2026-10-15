package com.lielu.keep.data.links

import com.lielu.keep.data.db.FolderEntity
import com.lielu.keep.data.db.LinkEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class LinkFilterTest {
    private val folders =
        listOf(
            FolderEntity(1, "Lecture"),
            FolderEntity(2, "Tech", parentId = 1),
            FolderEntity(3, "Cuisine"),
        )
    private val links =
        listOf(
            LinkEntity(1, "https://a.fr/kotlin", "Apprendre Kotlin", folderId = 2, favorite = true, createdAt = 1),
            LinkEntity(2, "https://b.fr/gateau", "Gâteau au chocolat", folderId = 3, readLater = true, createdAt = 2),
            LinkEntity(3, "https://c.fr/essai", "Essai", note = "à relire", folderId = 1, createdAt = 3),
            LinkEntity(4, "https://d.fr", "Sans dossier", createdAt = 4),
        )

    private fun ids(
        filter: LinkFilter,
        query: String = "",
    ) = filterLinks(links, folders, filter, query).map { it.id }

    @Test
    fun allKeepsEverything() = assertEquals(listOf(1L, 2L, 3L, 4L), ids(LinkFilter.All))

    @Test
    fun favoritesAndReadLater() {
        assertEquals(listOf(1L), ids(LinkFilter.Favorites))
        assertEquals(listOf(2L), ids(LinkFilter.ReadLater))
    }

    @Test
    fun folderIncludesItsSubfolders() {
        assertEquals(listOf(1L, 3L), ids(LinkFilter.Folder(1)))
        assertEquals(listOf(1L), ids(LinkFilter.Folder(2)))
    }

    @Test
    fun searchIgnoresCaseAndAccentsAndNeedsEveryWord() {
        assertEquals(listOf(2L), ids(LinkFilter.All, "GATEAU"))
        assertEquals(listOf(2L), ids(LinkFilter.All, "chocolat gateau"))
        assertEquals(emptyList<Long>(), ids(LinkFilter.All, "chocolat kotlin"))
    }

    @Test
    fun searchLooksInNoteAndUrlAndCombinesWithFilter() {
        assertEquals(listOf(3L), ids(LinkFilter.All, "relire"))
        assertEquals(listOf(1L), ids(LinkFilter.All, "a.fr"))
        assertEquals(emptyList<Long>(), ids(LinkFilter.Favorites, "gateau"))
    }
}
