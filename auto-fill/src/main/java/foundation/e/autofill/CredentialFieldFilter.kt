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

internal object CredentialFieldFilter {

    fun isLabelledAsCode(
        hint: String?,
        text: String?,
        autofillHints: List<String?>?
    ): Boolean {
        val labels = listOfNotNull(hint) + autofillHints.orEmpty()
        return labels.any { it.containsAny(AutoFillConsts.CODE_LABEL_KEYWORDS) } ||
            (labels + text).any { it.containsAny(AutoFillConsts.CODE_TEXT_KEYWORDS) }
    }

    fun isTooShortToBeAPassword(isNumericInput: Boolean, maxLength: Int): Boolean =
        isNumericInput && maxLength in AutoFillConsts.SHORT_NUMERIC_LENGTHS

    private fun String?.containsAny(keywords: Array<String>): Boolean {
        val value = this?.lowercase() ?: return false
        return keywords.any(value::contains)
    }
}
