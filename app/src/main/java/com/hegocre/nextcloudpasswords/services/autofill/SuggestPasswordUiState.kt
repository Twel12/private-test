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
package com.hegocre.nextcloudpasswords.services.autofill

import androidx.annotation.StringRes
import com.hegocre.nextcloudpasswords.R
import foundation.e.autofill.GeneratePasswordResult

internal data class SuggestPasswordUiState(
    val isLoading: Boolean,
    @param:StringRes val errorMessageRes: Int?,
    val generatedPassword: String?
) {
    val hasError: Boolean get() = errorMessageRes != null

    companion object {
        val Loading = SuggestPasswordUiState(
            isLoading = true,
            errorMessageRes = null,
            generatedPassword = null
        )

        fun error(@StringRes messageRes: Int) = SuggestPasswordUiState(
            isLoading = false,
            errorMessageRes = messageRes,
            generatedPassword = null
        )

        fun success(password: String) = SuggestPasswordUiState(
            isLoading = false,
            errorMessageRes = null,
            generatedPassword = password
        )
    }
}

internal fun GeneratePasswordResult.toUiState(): SuggestPasswordUiState = when (this) {
    is GeneratePasswordResult.Success -> SuggestPasswordUiState.success(password)
    GeneratePasswordResult.VaultLocked ->
        SuggestPasswordUiState.error(R.string.suggest_password_error_locked)
    GeneratePasswordResult.Offline ->
        SuggestPasswordUiState.error(R.string.suggest_password_error_offline)
    GeneratePasswordResult.Failed ->
        SuggestPasswordUiState.error(R.string.suggest_password_error)
}
