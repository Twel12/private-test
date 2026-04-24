/*
 * Copyright (C) 2026 e Foundation
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.hegocre.nextcloudpasswords.ui.models

import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import com.hegocre.nextcloudpasswords.R
import com.hegocre.nextcloudpasswords.ui.theme.statusBreached
import com.hegocre.nextcloudpasswords.ui.theme.statusGood
import com.hegocre.nextcloudpasswords.ui.theme.statusWeak

data class PasswordStrength(
    val color: Color,
    val label: String,
    val description: String,
)

@Composable
fun passwordStrengthForStatus(status: Int): PasswordStrength? = when (status) {
    0 -> PasswordStrength(
        color = MaterialTheme.colorScheme.statusGood,
        label = stringResource(R.string.password_strength_very_strong),
        description = stringResource(R.string.password_security_description_safe)
    )
    1 -> PasswordStrength(
        color = MaterialTheme.colorScheme.statusWeak,
        label = stringResource(R.string.password_strength_weak),
        description = stringResource(R.string.password_security_description_weak)
    )
    2 -> PasswordStrength(
        color = MaterialTheme.colorScheme.statusBreached,
        label = stringResource(R.string.password_strength_leaked),
        description = stringResource(R.string.password_security_description_leaked)
    )
    else -> null
}
