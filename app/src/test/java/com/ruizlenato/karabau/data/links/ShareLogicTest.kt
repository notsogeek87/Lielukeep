package com.ruizlenato.karabau.data.links

import com.ruizlenato.karabau.data.model.SavedListItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareLogicTest {
    private fun folder(id: String, name: String, parentId: String? = null, type: String = "manual") =
        SavedListItem(id, name, null, "📁", parentId, type, null, false, false, "owner")

    @Test
    fun `texte partage devient une url propre`() {
        assertEquals(
            "https://exemple.fr/a?id=1",
            urlFromSharedText("Super article https://exemple.fr/a?id=1&utm_source=share.")
        )
        assertNull(urlFromSharedText("rien du tout"))
    }

    @Test
    fun `le dernier dossier est preselectionne seulement s'il existe encore`() {
        val lists = listOf(folder("a", "A"), folder("s", "Smart", type = "smart"))
        assertEquals("a", initialFolderId("a", lists))
        assertNull(initialFolderId("disparu", lists))
        assertNull(initialFolderId("s", lists))
        assertNull(initialFolderId(null, lists))
    }

    @Test
    fun `libelle d'un sous-dossier`() {
        val lists = listOf(folder("a", "Cuisine"), folder("b", "Desserts", parentId = "a"))
        assertEquals("Cuisine", folderLabel(lists[0], lists))
        assertEquals("Cuisine › Desserts", folderLabel(lists[1], lists))
    }

    @Test
    fun `nom de dossier invalide si vide ou deja pris au meme niveau`() {
        val lists = listOf(folder("a", "Cuisine"), folder("b", "Desserts", parentId = "a"))
        assertFalse(isValidNewFolderName("  ", null, lists))
        assertFalse(isValidNewFolderName("cuisine", null, lists))
        assertTrue(isValidNewFolderName("Cuisine", "a", lists))
        assertFalse(isValidNewFolderName("desserts", "a", lists))
        assertTrue(isValidNewFolderName("Voyages", null, lists))
    }
}
