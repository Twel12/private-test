package com.hegocre.nextcloudpasswords.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinRecordTest {

    @Test
    fun `setting key is the prefix plus 32 hex characters, stable per owner`() {
        val key = PinRecord.settingKey(BACKUP)

        assertTrue(key.startsWith("client.murena.companion."))
        assertTrue(key.removePrefix("client.murena.companion.").matches(Regex("^[0-9a-f]{32}$")))
        assertEquals(key, PinRecord.settingKey(BACKUP.copy()))
        assertNotEquals(key, PinRecord.settingKey(FMD))
    }

    @Test
    fun `a record round-trips through its JSON`() {
        val record = PinRecord(id = "3f1c2a4e-0000-4000-8000-000000000001", fp = "k1:abc")
        val encoded = PinRecord.encode(record)

        assertTrue(encoded.contains("\"v\":1"))
        assertEquals(record, PinRecord.parse(encoded))
    }

    @Test
    fun `a legacy pin is a bare uuid without fingerprint`() {
        val id = "3f1c2a4e-0000-4000-8000-000000000001"
        assertEquals(PinRecord(id = id), PinRecord.parse(id))
    }

    @Test
    fun `blank or malformed values are no pin`() {
        assertNull(PinRecord.parse(null))
        assertNull(PinRecord.parse(""))
        assertNull(PinRecord.parse("not-a-uuid"))
        assertNull(PinRecord.parse("{broken"))
    }
}
