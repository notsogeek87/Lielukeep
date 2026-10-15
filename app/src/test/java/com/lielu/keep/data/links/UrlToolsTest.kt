package com.lielu.keep.data.links

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlToolsTest {
    @Test
    fun extractsUrlFromSharedSentence() {
        assertEquals(
            "https://exemple.fr/article?id=3",
            UrlTools.extractUrl("Regarde ça : https://exemple.fr/article?id=3 super non ?"),
        )
    }

    @Test
    fun dropsTrailingPunctuationAndUnbalancedParenthesis() {
        assertEquals("https://exemple.fr/a", UrlTools.extractUrl("(voir https://exemple.fr/a)."))
        assertEquals(
            "https://fr.wikipedia.org/wiki/Kotlin_(langage)",
            UrlTools.extractUrl("https://fr.wikipedia.org/wiki/Kotlin_(langage)"),
        )
    }

    @Test
    fun returnsNullWithoutUrl() {
        assertNull(UrlTools.extractUrl("pas de lien ici"))
        assertNull(UrlTools.extractUrl(null))
        assertNull(UrlTools.extractUrl("https://"))
    }

    @Test
    fun removesTrackingParametersOnly() {
        assertEquals(
            "https://exemple.fr/p?id=1&page=2#haut",
            UrlTools.clean("https://exemple.fr/p?utm_source=x&id=1&fbclid=abc&page=2&UTM_Medium=y#haut"),
        )
        assertEquals("https://exemple.fr/p#a", UrlTools.clean("https://exemple.fr/p?utm_source=x#a"))
        assertEquals("https://exemple.fr/p", UrlTools.clean("https://exemple.fr/p"))
    }

    @Test
    fun readsDomain() {
        assertEquals("exemple.fr", UrlTools.domainOf("https://www.Exemple.fr:8080/a/b?c=d"))
        assertEquals("exemple.fr", UrlTools.domainOf("https://user@exemple.fr#x"))
    }
}
