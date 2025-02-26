package foundation.e.geolocationsms

import org.junit.Test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class PasswordGeneratorTest {

    @Test
    fun `test generatePassword - length`() {
        val password = PasswordGenerator().generatePassword()
        assertEquals(PasswordGenerator.PASSWORD_LENGTH, password.length)
    }

    @Test
    fun `test generatePassword - only letters and numbers`() {
        val password = PasswordGenerator().generatePassword()
        assertTrue(password.all { it.isLetterOrDigit() })
    }

    @Test
    fun `test generatePassword - no confusing characters`() {
        val password = PasswordGenerator().generatePassword()
        assertTrue(password.none { CONFUSING_CHARS.contains(it) })
    }

    @Test
    fun `test generatePassword - at least two digits`() {
        val password = PasswordGenerator().generatePassword()
        assertTrue(password.count { it.isDigit() } >= 2)
    }

    @Test
    fun `test generatePassword - all letters are uppercase`() {
        val password = PasswordGenerator().generatePassword()
        assertTrue(password.all { !it.isLowerCase() || it.isDigit() })
    }

    @Test
    fun `test generatePassword - multiple times`() {
        val passwordGenerator = PasswordGenerator()
        (1..NB_TESTS).forEach { _ ->
            val password = passwordGenerator.generatePassword()
            assertTrue(password.length == PasswordGenerator.PASSWORD_LENGTH)
            assertTrue(password.all { it.isLetterOrDigit() })
            assertTrue(password.none { CONFUSING_CHARS.contains(it) })
            assertTrue(password.count { it.isDigit() } >= 2)
            assertTrue(password.all { !it.isLowerCase() || it.isDigit() })
        }
    }

    companion object {
        private const val NB_TESTS = 1000
        // Must be linked to the PasswordGenerator ALLOWED_CHARS
        private val CONFUSING_CHARS = listOf('I', 'L', '1', 'O', '0')
    }
}
