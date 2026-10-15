package com.ruizlenato.karabau.data.links

import com.ruizlenato.karabau.data.model.BookmarkItem
import com.ruizlenato.karabau.data.model.SavedListItem
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkFilterTest {
    private fun link(
        id: String,
        title: String? = null,
        url: String? = null,
        site: String? = null,
        note: String? = null,
        tags: List<String> = emptyList(),
        favourited: Boolean = false,
        createdAt: String = "2024-01-01T00:00:00.000Z"
    ) = BookmarkItem(
        id = id,
        title = title,
        tags = tags.toImmutableList(),
        subtitle = site,
        linkUrl = url,
        favourited = favourited,
        createdAt = createdAt,
        note = note
    )

    private fun folder(
        id: String,
        name: String,
        parentId: String? = null,
        type: String = "manual",
        role: String = "owner"
    ) = SavedListItem(id, name, null, "📚", parentId, type, null, false, false, role)

    private fun ids(list: List<BookmarkItem>) = list.map { it.id }

    @Test
    fun `la recherche ignore accents et casse`() {
        val links = listOf(link("1", title = "Éléphants d'Afrique"), link("2", title = "Autre"))
        assertEquals(listOf("1"), ids(filterLinks(links, emptyList(), emptyMap(), LinkFilter.All, "ELEPHANT")))
    }

    @Test
    fun `tous les mots sont exiges d'abord, dans titre url note ou site`() {
        val links = listOf(
            link("1", title = "Recette", url = "https://cuisine.fr/tarte", note = "pour dimanche"),
            link("2", title = "Recette", site = "Marmiton")
        )
        assertEquals(listOf("1"), ids(filterLinks(links, emptyList(), emptyMap(), LinkFilter.All, "tarte dimanche")))
        assertEquals(listOf("2"), ids(filterLinks(links, emptyList(), emptyMap(), LinkFilter.All, "recette marmiton")))
    }

    @Test
    fun `sans resultat avec tous les mots, retombe sur au moins un mot`() {
        val links = listOf(
            link("1", title = "Recette", url = "https://cuisine.fr/tarte"),
            link("2", title = "Recette", site = "Marmiton"),
            link("3", title = "Voiture")
        )
        assertEquals(
            setOf("1", "2"),
            filterLinks(links, emptyList(), emptyMap(), LinkFilter.All, "tarte marmiton").map { it.id }.toSet()
        )
        assertTrue(filterLinks(links, emptyList(), emptyMap(), LinkFilter.All, "zzz").isEmpty())
    }

    @Test
    fun `la recherche couvre etiquettes, dossiers et ponctuation`() {
        val links = listOf(
            link("1", title = "Un lien", tags = listOf("Cuisine")),
            link("2", title = "Autre"),
            link("3", title = "L'éléphant")
        )
        val lists = listOf(folder("f", "Voyages"))
        val membership = mapOf("f" to setOf("2"))
        assertEquals(listOf("1"), ids(filterLinks(links, lists, membership, LinkFilter.All, "cuisine")))
        assertEquals(listOf("2"), ids(filterLinks(links, lists, membership, LinkFilter.All, "voyage")))
        assertEquals(listOf("3"), ids(filterLinks(links, lists, membership, LinkFilter.All, "l elephant")))
    }

    @Test
    fun `tri du plus recent au plus ancien`() {
        val links = listOf(
            link("vieux", createdAt = "2023-01-01T00:00:00.000Z"),
            link("recent", createdAt = "2024-06-01T00:00:00.000Z"),
            link("milieu", createdAt = "2024-01-01T00:00:00.000Z")
        )
        assertEquals(
            listOf("recent", "milieu", "vieux"),
            ids(filterLinks(links, emptyList(), emptyMap(), LinkFilter.All, ""))
        )
    }

    @Test
    fun `filtres favoris et a lire`() {
        val links = listOf(
            link("fav", favourited = true),
            link("lire", tags = listOf("À Lire")),
            link("rien", tags = listOf("autre"))
        )
        assertEquals(listOf("fav"), ids(filterLinks(links, emptyList(), emptyMap(), LinkFilter.Favorites, "")))
        assertEquals(listOf("lire"), ids(filterLinks(links, emptyList(), emptyMap(), LinkFilter.ReadLater, "")))
    }

    @Test
    fun `un dossier inclut ses sous-dossiers`() {
        val lists = listOf(folder("a", "A"), folder("b", "B", parentId = "a"), folder("c", "C"))
        val membership = mapOf("a" to setOf("1"), "b" to setOf("2"), "c" to setOf("3"))
        val links = listOf(link("1"), link("2"), link("3"))
        assertEquals(
            setOf("1", "2"),
            filterLinks(links, lists, membership, LinkFilter.Folder("a"), "").map { it.id }.toSet()
        )
        assertEquals(listOf("3"), ids(filterLinks(links, lists, membership, LinkFilter.Folder("c"), "")))
    }

    @Test
    fun `puces de dossier ordonnees avec compteur et sans listes intelligentes ni partagees`() {
        val lists = listOf(
            folder("b", "Bêta"),
            folder("a", "Alpha"),
            folder("a1", "Sous", parentId = "a"),
            folder("s", "Smart", type = "smart"),
            folder("p", "Partagée", role = "viewer")
        )
        val membership = mapOf("a" to setOf("1"), "a1" to setOf("2", "ghost"), "b" to setOf("3"))
        val chips = folderChips(listOf(link("1"), link("2"), link("3")), lists, membership)
        assertEquals(listOf("a", "a1", "b"), chips.map { it.folder.id })
        assertEquals(listOf(0, 1, 0), chips.map { it.depth })
        assertEquals(listOf(2, 1, 1), chips.map { it.count })
    }

    @Test
    fun `sous-dossier seulement sous un dossier de premier niveau`() {
        val lists = listOf(folder("a", "A"), folder("a1", "A1", parentId = "a"), folder("v", "V", role = "viewer"))
        assertTrue(canHaveSubfolder(lists[0], lists))
        assertFalse(canHaveSubfolder(lists[1], lists))
        assertFalse(canHaveSubfolder(lists[2], lists))
    }

    @Test
    fun `un cycle de parents ne boucle pas`() {
        val lists = listOf(folder("a", "A", parentId = "b"), folder("b", "B", parentId = "a"))
        assertEquals(emptyList<String>(), orderedFolders(lists).map { it.first.id })
        assertEquals(setOf("a", "b"), folderAndChildrenIds("a", lists))
    }

    @Test
    fun `nom du site retombe sur le domaine`() {
        assertEquals("Marmiton", link("1", site = "Marmiton", url = "https://www.x.fr/a").siteName())
        assertEquals("x.fr", link("1", url = "https://www.x.fr/a").siteName())
        assertEquals("", link("1", tags = persistentListOf<String>()).siteName())
    }
}
