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
package foundation.e.autofill

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialFieldFilterTest {

    private fun suppress(
        hint: String? = null,
        text: String? = null,
        autofillHints: List<String?>? = null,
        isNumericInput: Boolean = false,
        maxLength: Int = -1
    ) = CredentialFieldFilter.isLabelledAsCode(hint, text, autofillHints) ||
        CredentialFieldFilter.isTooShortToBeAPassword(isNumericInput, maxLength)

    @Test
    fun `platform sms otp hint is an otp field`() {
        assertTrue(suppress(autofillHints = listOf("smsOTPCode")))
    }

    @Test
    fun `w3c one-time-code hint is an otp field`() {
        assertTrue(suppress(autofillHints = listOf("one-time-code")))
    }

    @Test
    fun `otp keywords in the hint are matched`() {
        listOf("OTP", "totp", "2FA", "mfa", "tfa", "Verification", "Confirmation code")
            .forEach { assertTrue(it, suppress(hint = it)) }
    }

    @Test
    fun `masked otp fields are still detected`() {
        // Apps routinely mask one-time codes with inputType="numberPassword". Treating those as
        // password fields would offer the stored password for filling and let the save prompt
        // overwrite it with the typed code.
        assertTrue(suppress(hint = "Enter OTP"))
        assertTrue(suppress(autofillHints = listOf("smsOTPCode")))
        assertTrue(suppress(text = "Enter the confirmation code"))
    }

    @Test
    fun `otp keywords in the visible text are matched`() {
        assertTrue(suppress(text = "Enter the confirmation code"))
    }

    @Test
    fun `bare code is not enough`() {
        assertFalse(suppress(hint = "Code"))
    }

    @Test
    fun `short keywords do not match the value the user is typing`() {
        // "text" is the field's current value. Matching short fragments there would suppress
        // autofill mid-typing for anyone whose username contains one.
        listOf("botpilot", "shiftfast", "user2fa", "Amfam", "swiftfare", "jackpotparty")
            .forEach { assertFalse(it, suppress(text = it)) }
    }

    @Test
    fun `short keywords still match developer-set labels`() {
        assertTrue(suppress(hint = "OTP"))
        assertTrue(suppress(autofillHints = listOf("2fa")))
    }

    @Test
    fun `fields that merely contain the word code are not otp fields`() {
        listOf("Postal code", "Area code", "Promo code", "Country code")
            .forEach { assertFalse(it, suppress(hint = it)) }
    }

    @Test
    fun `an ordinary password field is not an otp field`() {
        assertFalse(suppress(hint = "Password"))
        assertFalse(suppress(hint = "Confirm password"))
    }

    @Test
    fun `a short numeric field is only a code once the caller finds no username`() {
        // isTooShortToBeAPassword is shape only. LoginFieldParser gates it on usernameIds being
        // empty, so a numeric password on a login form keeps working.
        assertTrue(CredentialFieldFilter.isTooShortToBeAPassword(isNumericInput = true, maxLength = 6))
        assertFalse(
            CredentialFieldFilter.isLabelledAsCode(
                hint = null,
                text = null,
                autofillHints = listOf("passwordAuto")
            )
        )
    }

    @Test
    fun `instagram confirmation code field is detected`() {
        // Live capture from com.instagram.android: inputType=18 (numberPassword), maxLen=6, and
        // autofillHints=["passwordAuto"]. No hint, no id, no content description — the length and
        // numeric class are the only signals that it is a code and not a password.
        assertTrue(
            suppress(
                // The device reports a null entry alongside the hint, so keep it here verbatim.
                autofillHints = listOf(null, "passwordAuto"),
                isNumericInput = true,
                maxLength = 6
            )
        )
    }

    @Test
    fun `an uncapped numeric field is not an otp field`() {
        assertFalse(suppress(isNumericInput = true, maxLength = -1))
        assertFalse(suppress(isNumericInput = true, maxLength = 16))
    }

    @Test
    fun `a short text password is not an otp field`() {
        // Only numeric-class fields qualify on length alone.
        assertFalse(suppress(isNumericInput = false, maxLength = 6))
    }

    @Test
    fun `phone hint is not an otp field`() {
        // Regression: the previous check ignored any hint containing "one", and
        // AUTOFILL_HINT_PHONE is the literal "phone".
        assertFalse(suppress(autofillHints = listOf("phone")))
        assertFalse(suppress(autofillHints = listOf("phoneNumber")))
    }

    @Test
    fun `ordinary credential fields are untouched`() {
        assertFalse(suppress(hint = "Username"))
        assertFalse(suppress(hint = "Email address"))
        assertFalse(suppress(autofillHints = listOf("username")))
    }

}
