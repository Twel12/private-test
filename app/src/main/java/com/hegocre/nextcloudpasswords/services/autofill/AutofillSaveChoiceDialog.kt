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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.hegocre.nextcloudpasswords.R
import foundation.e.elib.compose.theme.ETheme
import foundation.e.elib.R as eR

@Composable
internal fun AutofillSaveChoiceDialog(
    candidates: List<NCPAutofillSaveCandidate>,
    onSelectCandidate: (NCPAutofillSaveCandidate) -> Unit,
    onCreateNew: () -> Unit,
    onDismissRequest: () -> Unit
) {
    ETheme {
        Dialog(onDismissRequest = onDismissRequest) {
            Surface(
                color = colorResource(eR.color.e_floating_background),
                contentColor = contentColorFor(backgroundColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(modifier = Modifier.padding(vertical = 16.dp)) {
                    Text(
                        text = stringResource(R.string.autofill_save_choice_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )

                    LazyColumn {
                        items(candidates, key = { it.id }) { candidate ->
                            SaveCandidateRow(
                                candidate = candidate,
                                onClick = { onSelectCandidate(candidate) }
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(top = 8.dp),
                        thickness = DividerDefaults.Thickness,
                        color = DividerDefaults.color
                    )

                    TextButton(
                        onClick = onCreateNew,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(text = stringResource(R.string.autofill_save_create_new_entry))
                    }
                }
            }
        }
    }
}

@Composable
private fun SaveCandidateRow(
    candidate: NCPAutofillSaveCandidate,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Text(
            text = stringResource(
                R.string.autofill_save_update_entry,
                candidate.label.ifBlank { candidate.username }
            ),
            style = MaterialTheme.typography.bodyLarge
        )
        if (candidate.url.isNotBlank()) {
            Text(
                text = candidate.url,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
