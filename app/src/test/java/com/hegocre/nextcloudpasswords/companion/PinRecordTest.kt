package com.hegocre.nextcloudpasswords.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinRecordTest {

    @Test
    fun `setting key is the prefix plus 24 hex characters, stable per owner`() {
        val key = PinRecord.settingKey(BACKUP)

        assertTrue(key.startsWith("client.murena.companion."))
        assertTrue(key.removePrefix("client.murena.companion.").matches(Regex("^[0-9a-f]{24}$")))
        assertEquals(key, PinRecord.settingKey(BACKUP.copy()))
        assertNotEquals(key, PinRecord.settingKey(FMD))
    }

    @Test
    fun `setting keys fit the server's key limit`() {
        val owners = listOf(
            BACKUP,
            Owner("foundation.e.findmydevice", "0123456789abcdef0123456789abcdef"),
            Owner("foundation.e.findmydevice", "a".repeat(64)),
        )

        owners.forEach { assertTrue(PinRecord.settingKey(it).length <= PinRecord.MAX_SETTING_KEY) }
        assertEquals(55, PinRecord.MAX_SETTING_KEY)
    }

    @Test
    fun `a pin with a uuid and a fingerprint fits the server's value limit`() {
        val record = PinRecord(id = "3f1c2a4e-0000-4000-8000-000000000001", fp = "0b1c2d3e:${"f".repeat(32)}")

        assertTrue(PinRecord.encode(record).length <= PinRecord.MAX_SETTING_VALUE)
        assertEquals(128, PinRecord.MAX_SETTING_VALUE)
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
