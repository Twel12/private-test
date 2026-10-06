package com.hegocre.nextcloudpasswords.companion

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.hegocre.nextcloudpasswords.R
import java.text.DateFormat
import java.util.Date

private const val MILLIS_PER_SECOND = 1000L

@Composable
fun CompanionConflictDialog(
    candidates: List<VaultEntry>,
    onKeep: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var selected by remember(candidates) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.companion_conflict_title)) },
        text = {
            Column {
                Text(stringResource(R.string.companion_conflict_message))
                candidates.forEach { entry ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.selectable(
                            selected = selected == entry.id,
                            onClick = { selected = entry.id },
                        ),
                    ) {
                        RadioButton(selected = selected == entry.id, onClick = { selected = entry.id })
                        Column {
                            Text(entry.username)
                            Text(
                                stringResource(
                                    R.string.companion_conflict_saved_on,
                                    DateFormat.getDateTimeInstance()
                                        .format(Date(entry.created * MILLIS_PER_SECOND)),
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = selected != null, onClick = { selected?.let(onKeep) }) {
                Text(stringResource(R.string.companion_conflict_keep))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}
