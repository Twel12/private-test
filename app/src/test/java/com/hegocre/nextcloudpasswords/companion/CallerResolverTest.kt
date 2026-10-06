package com.hegocre.nextcloudpasswords.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallerResolverTest {

    @Test
    fun `exactly one package identifies the caller`() {
        assertEquals("foundation.e.backup", CallerResolver.resolve(arrayOf("foundation.e.backup")))
    }

    @Test
    fun `no package or a shared uid is refused`() {
        assertNull(CallerResolver.resolve(null))
        assertNull(CallerResolver.resolve(emptyArray()))
        assertNull(CallerResolver.resolve(arrayOf("a.b", "c.d")))
        assertNull(CallerResolver.resolve(arrayOf(" ")))
    }
}
