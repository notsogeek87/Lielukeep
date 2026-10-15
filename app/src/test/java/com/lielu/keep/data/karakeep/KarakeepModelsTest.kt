package com.lielu.keep.data.karakeep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KarakeepModelsTest {
    @Test
    fun normalizesWhatTheUserTypes() {
        assertEquals("https://keep.lielu.eu", normalizeServerUrl("keep.lielu.eu"))
        assertEquals("https://keep.lielu.eu", normalizeServerUrl("  https://keep.lielu.eu/  "))
        assertEquals("https://keep.lielu.eu", normalizeServerUrl("https://keep.lielu.eu/api/v1"))
        assertEquals("https://keep.lielu.eu:8443/sous", normalizeServerUrl("https://keep.lielu.eu:8443/sous/"))
    }

    @Test
    fun rejectsInsecureOrInvalidAddresses() {
        assertNull(normalizeServerUrl("http://keep.lielu.eu"))
        assertNull(normalizeServerUrl(""))
        assertNull(normalizeServerUrl("pas une adresse"))
        assertNull(normalizeServerUrl("https://localhost"))
    }
}
