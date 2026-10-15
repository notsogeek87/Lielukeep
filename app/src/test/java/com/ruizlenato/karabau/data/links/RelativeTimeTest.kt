package com.ruizlenato.karabau.data.links

import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeTest {
    private val min = 60_000L
    private val hour = 60 * min
    private val day = 24 * hour

    @Test
    fun `age en francais selon l'echelle`() {
        assertEquals("à l'instant", relativeTimeFr(1_000, 1_000))
        assertEquals("il y a 5 min", relativeTimeFr(5 * min, 0))
        assertEquals("il y a 3 h", relativeTimeFr(3 * hour, 0))
        assertEquals("il y a 2 j", relativeTimeFr(2 * day, 0))
        assertEquals("il y a 2 sem.", relativeTimeFr(14 * day, 0))
        assertEquals("il y a 3 mois", relativeTimeFr(95 * day, 0))
        assertEquals("il y a 1 an", relativeTimeFr(400 * day, 0))
        assertEquals("il y a 2 ans", relativeTimeFr(800 * day, 0))
    }

    @Test
    fun `une date dans le futur donne a l'instant`() {
        assertEquals("à l'instant", relativeTimeFr(0, 10 * min))
    }

    @Test
    fun `parseIsoMillis lit les dates Karakeep et tolere l'invalide`() {
        assertEquals(1_700_000_000_000L, parseIsoMillis("2023-11-14T22:13:20.000Z"))
        assertEquals(0L, parseIsoMillis("n'importe quoi"))
    }
}
