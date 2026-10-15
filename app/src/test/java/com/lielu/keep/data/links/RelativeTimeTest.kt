package com.lielu.keep.data.links

import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeTest {
    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour
    private val now = 1_000 * day

    @Test
    fun formatsEachRange() {
        assertEquals("à l'instant", relativeTimeFr(now, now - 30_000))
        assertEquals("il y a 5 min", relativeTimeFr(now, now - 5 * minute))
        assertEquals("il y a 3 h", relativeTimeFr(now, now - 3 * hour))
        assertEquals("il y a 3 j", relativeTimeFr(now, now - 3 * day))
        assertEquals("il y a 2 sem.", relativeTimeFr(now, now - 15 * day))
        assertEquals("il y a 4 mois", relativeTimeFr(now, now - 125 * day))
        assertEquals("il y a 1 an", relativeTimeFr(now, now - 400 * day))
        assertEquals("il y a 2 ans", relativeTimeFr(now, now - 800 * day))
    }

    @Test
    fun futureDateCountsAsNow() = assertEquals("à l'instant", relativeTimeFr(now, now + day))
}
