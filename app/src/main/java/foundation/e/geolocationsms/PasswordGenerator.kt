package foundation.e.geolocationsms

import java.util.Random

class PasswordGenerator() {

    companion object {
        private const val ALLOWED_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789" // Removed I, L, 1, O, 0
        private const val MIN_DIGITS = 2
        const val PASSWORD_LENGTH = 8
    }

    /* Generate a password according to the following rules:
    the password is generated automatically according to the following rules:
          8 characters long
          only letters and numbers
          it does not contain characters that can get confused such as: i/l/1 or o/0
          at least 2 digits must be included
          the letters are displayed as capital letters for readability
          the password is not case sensitive */
    fun generatePassword(): String {
        val random = Random()
        val password = StringBuilder(PASSWORD_LENGTH)
        var digitCount = 0

        // Ensure at least 2 digits are included
        for (i in 0 until MIN_DIGITS) {
            val digit = (random.nextInt(8) + 2).toString() // Generate digits 2-9
            password.append(digit)
            digitCount++
        }

        // Fill the rest of the password with random characters
        while (password.length < Companion.PASSWORD_LENGTH) {
            val randomIndex = random.nextInt(ALLOWED_CHARS.length)
            val randomChar = ALLOWED_CHARS[randomIndex]
            password.append(randomChar)
            if (randomChar.isDigit()) {
                digitCount++
            }
        }

        // Shuffle the password to mix digits and letters
        val shuffledPassword = password.toString().toCharArray().apply { shuffle() }

        return String(shuffledPassword)
    }
}
