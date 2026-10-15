package com.ruizlenato.karabau.data.links

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlToolsTest {
    @Test
    fun `extractUrl trouve le lien dans une phrase partagee`() {
        assertEquals("https://exemple.fr/a?b=1", UrlTools.extractUrl("Regarde ça : https://exemple.fr/a?b=1 !"))
    }

    @Test
    fun `extractUrl retire la ponctuation finale et la parenthese orpheline`() {
        assertEquals("https://exemple.fr/page", UrlTools.extractUrl("(voir https://exemple.fr/page)."))
        assertEquals("https://fr.wikipedia.org/wiki/Foo_(bar)", UrlTools.extractUrl("https://fr.wikipedia.org/wiki/Foo_(bar)"))
    }

    @Test
    fun `extractUrl renvoie null sans lien ou sans hote`() {
        assertNull(UrlTools.extractUrl("pas de lien ici"))
        assertNull(UrlTools.extractUrl(null))
        assertNull(UrlTools.extractUrl("https://"))
    }

    @Test
    fun `clean retire utm et identifiants de suivi`() {
        assertEquals(
            "https://exemple.fr/a?id=3",
            UrlTools.clean("https://exemple.fr/a?utm_source=x&id=3&fbclid=abc&UTM_Medium=y")
        )
    }

    @Test
    fun `clean supprime le point d'interrogation quand il ne reste rien et garde l'ancre`() {
        assertEquals("https://exemple.fr/a#top", UrlTools.clean("https://exemple.fr/a?utm_source=x&gclid=1#top"))
    }

    @Test
    fun `clean laisse intacte une url sans suivi`() {
        val url = "https://exemple.fr/a?b=1&c=%20x#frag"
        assertEquals(url, UrlTools.clean(url))
        assertEquals("https://exemple.fr/a", UrlTools.clean("https://exemple.fr/a"))
    }

    @Test
    fun `domainOf enleve www port et identifiants`() {
        assertEquals("exemple.fr", UrlTools.domainOf("https://www.Exemple.fr:8080/a?b#c"))
        assertEquals("exemple.fr", UrlTools.domainOf("https://user:pw@exemple.fr/"))
        assertEquals("", UrlTools.domainOf("pas une url"))
    }
}
