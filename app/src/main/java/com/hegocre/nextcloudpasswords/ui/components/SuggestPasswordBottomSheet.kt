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
package com.hegocre.nextcloudpasswords.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hegocre.nextcloudpasswords.R
import foundation.e.elib.compose.theme.ETheme
import foundation.e.elib.R as eR

@Composable
internal fun SuggestPasswordBottomSheet(
    isLoading: Boolean,
    @StringRes errorMessageRes: Int?,
    generatedPassword: String?,
    isCredentialManager: Boolean,
    onUse: (String) -> Unit,
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ETheme {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = colorResource(eR.color.e_floating_background),
                    contentColor = contentColorFor(MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Image(
                            painterResource(R.drawable.ic_e_settings_password_app),
                            contentDescription = stringResource(R.string.suggest_password_app_name),
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = stringResource(R.string.suggest_password_app_name),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 24.dp, top = 4.dp)
                        )

                        when {
                            isLoading -> {
                                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                            }
                            errorMessageRes != null || generatedPassword == null -> {
                                Text(
                                    text = stringResource(
                                        errorMessageRes ?: R.string.suggest_password_error
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            else -> {
                                Text(
                                    text = stringResource(R.string.suggest_password_dialog_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                                Text(
                                    text = generatedPassword,
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                                Text(
                                    text = stringResource(R.string.suggest_password_dialog_summary),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Button(
                                    onClick = {
                                        if (!isCredentialManager) onUse(generatedPassword)
                                        else onCopy(generatedPassword)
                                    },
                                    modifier = Modifier
                                        .padding(top = 8.dp)
                                        .fillMaxWidth(USE_BUTTON_TEXT_WIDTH_FRACTION)
                                ) {
                                    Text(stringResource(R.string.suggest_password_dialog_use_button))
                                }
                            }
                        }

                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(
                                text = stringResource(android.R.string.cancel)
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val USE_BUTTON_TEXT_WIDTH_FRACTION = 0.8f
