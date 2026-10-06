/*
 *  Copyright MURENA SAS 2026
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */
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
