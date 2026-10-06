package com.hegocre.nextcloudpasswords.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallerResolverTest {

    @Test
    fun `exactly one package identifies the caller`() {
        assertEquals("foundation.e.backup", CallerResolver.resolve(true, arrayOf("foundation.e.backup")))
    }

    @Test
    fun `no package or a shared uid is refused`() {
        assertNull(CallerResolver.resolve(true, null))
        assertNull(CallerResolver.resolve(true, emptyArray()))
        assertNull(CallerResolver.resolve(true, arrayOf("a.b", "c.d")))
        assertNull(CallerResolver.resolve(true, arrayOf(" ")))
    }

    @Test
    fun `a caller without the companion permission is refused`() {
        assertNull(CallerResolver.resolve(false, arrayOf("foundation.e.backup")))
    }
}
