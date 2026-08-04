package com.hegocre.nextcloudpasswords.ui.components

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hegocre.nextcloudpasswords.R
import com.hegocre.nextcloudpasswords.api.FoldersApi
import com.hegocre.nextcloudpasswords.data.folder.Folder
import com.hegocre.nextcloudpasswords.data.password.CustomField
import com.hegocre.nextcloudpasswords.data.password.Password
import com.hegocre.nextcloudpasswords.utils.isValidEmail
import com.hegocre.nextcloudpasswords.utils.isValidURL
import foundation.e.elib.compose.components.EOutlinedButton
import foundation.e.elib.compose.components.EOutlinedButtonRed
import foundation.e.elib.compose.theme.ETheme
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.reflect.KFunction3

class EditableCustomField(val label: String, val type: String, initialValue: String) {
    val valueState = TextFieldState(initialValue)
    val value: String get() = valueState.text.toString()

    fun toCustomField(): CustomField = CustomField(label = label, type = type, value = value)
}

class EditablePasswordState(originalPassword: Password?) {
    val passwordState = TextFieldState(originalPassword?.password ?: "")
    val labelState = TextFieldState(originalPassword?.label ?: "")
    val usernameState = TextFieldState(originalPassword?.username ?: "")
    val urlState = TextFieldState(originalPassword?.url ?: "")
    val notesState = TextFieldState(originalPassword?.notes ?: "")
    val password: String get() = passwordState.text.toString()
    val label: String get() = labelState.text.toString()
    val username: String get() = usernameState.text.toString()
    val url: String get() = urlState.text.toString()
    val notes: String get() = notesState.text.toString()
    var folder by mutableStateOf(originalPassword?.folder ?: FoldersApi.DEFAULT_FOLDER_UUID)
    var customFields =
        if (originalPassword?.customFields?.isBlank() == true) mutableStateListOf() else
        Json.decodeFromString<List<CustomField>>(originalPassword?.customFields ?: "[]")
            .map { EditableCustomField(it.label, it.type, it.value) }
            .toMutableStateList()
    var favorite by mutableStateOf(originalPassword?.favorite ?: false)
    var replyAutofill = false

    fun isValid(): Boolean {
        if (label.isBlank())
            return false
        if (password.isBlank())
            return false
        if (!url.isValidURL())
            return false
        for (customField in customFields) {
            when (customField.type) {
                CustomField.TYPE_URL -> {
                    if (!customField.value.isValidURL())
                        return false
                }

                CustomField.TYPE_EMAIL -> {
                    if (!customField.value.isValidEmail())
                        return false
                }
            }
        }
        return true
    }

    companion object {
        val Saver: Saver<EditablePasswordState, *> = listSaver(
            save = {
                listOf(
                    it.password, it.label, it.username, it.url, it.notes,
                    it.folder,
                    Json.encodeToString(it.customFields.map { field -> field.toCustomField() }),
                    it.favorite.toString(), it.replyAutofill.toString()
                )
            },
            restore = {
                EditablePasswordState(null).apply {
                    passwordState.setTextAndPlaceCursorAtEnd(it[0])
                    labelState.setTextAndPlaceCursorAtEnd(it[1])
                    usernameState.setTextAndPlaceCursorAtEnd(it[2])
                    urlState.setTextAndPlaceCursorAtEnd(it[3])
                    notesState.setTextAndPlaceCursorAtEnd(it[4])
                    folder = it[5]
                    customFields = Json.decodeFromString<List<CustomField>>(it[6])
                        .map { field -> EditableCustomField(field.label, field.type, field.value) }
                        .toMutableStateList()
                    favorite = it[7].toBooleanStrictOrNull() ?: false
                    replyAutofill = it[8].toBooleanStrictOrNull() ?: false
                }
            }
        )
    }
}

@Composable
fun rememberEditablePasswordState(password: Password? = null): EditablePasswordState =
    rememberSaveable(password, saver = EditablePasswordState.Saver) {
        EditablePasswordState(password)
    }

@Composable
fun EditablePasswordView(
    editablePasswordState: EditablePasswordState,
    folders: List<Folder>,
    isUpdating: Boolean,
    isAutofillRequest: Boolean,
    onGeneratePassword: KFunction3<Int, Boolean, Boolean, Deferred<String?>>?,
    onSavePassword: () -> Unit,
    onDeletePassword: (() -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val onBackPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    var showDeleteDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var showAddCustomFieldDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var showFolderDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var showFieldErrors by rememberSaveable {
        mutableStateOf(false)
    }
    var showDiscardDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var confirmedDiscard by rememberSaveable {
        mutableStateOf(false)
    }

    BackHandler (enabled = !confirmedDiscard) {
        showDiscardDialog = true
    }

    if (showDiscardDialog) {
        DiscardChangesDialog(
            onConfirmButton = {
                confirmedDiscard = true
                showDiscardDialog = false
                coroutineScope.launch {
                    awaitFrame()
                    onBackPressedDispatcher?.onBackPressed()
                    confirmedDiscard = false
                }
            },
            onDismissRequest = {
                showDiscardDialog = false
            }
        )
    }

    LazyColumn {
        item(key = "top_spacer") { Spacer(modifier = Modifier.width(16.dp)) }

        item(key = "password_label") {
            val contentColor by animateColorAsState(
                targetValue = if (editablePasswordState.favorite)
                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "favoriteContentColor"
            )
            Row (
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .padding(horizontal = 16.dp)
            ) {
                OutlinedTextField(
                    state = editablePasswordState.labelState,
                    label = { Text(text = stringResource(id = R.string.password_folder_attr_label)) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.weight(1f),
                    isError = showFieldErrors && editablePasswordState.label.isBlank(),
                    supportingText = if (showFieldErrors && editablePasswordState.label.isBlank()) {
                        {
                            Text(text = stringResource(id = R.string.error_field_cannot_be_empty))
                        }
                    } else null

                )
                Icon(
                    imageVector = if (editablePasswordState.favorite)
                        Icons.Default.Star
                    else
                        Icons.Outlined.StarOutline,
                    tint = contentColor,
                    contentDescription = stringResource(id = R.string.password_attr_favorite),
                    modifier = Modifier
                        .padding(start = 28.dp)
                        .clickable { editablePasswordState.favorite = !editablePasswordState.favorite }
                )
            }

        }

        item(key = "password_username") {
            OutlinedTextField(
                state = editablePasswordState.usernameState,
                label = { Text(text = stringResource(id = R.string.password_attr_username)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .padding(horizontal = 16.dp)
            )
        }


        item(key = "password_password") {
            var showPassword by rememberSaveable {
                mutableStateOf(false)
            }

            var showGenerateDialog by remember {
                mutableStateOf(false)
            }

            var isGenerating by rememberSaveable {
                mutableStateOf(false)
            }

            OutlinedSecureTextField(
                state = editablePasswordState.passwordState,
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily(Font(R.font.dejavu_sans_mono))),
                label = { Text(text = stringResource(id = R.string.password_attr_password)) },
                textObfuscationMode = if (showPassword)
                    TextObfuscationMode.Visible else TextObfuscationMode.Hidden,
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {


                        if (isGenerating) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword)
                                    Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = stringResource(R.string.text_input_show_password_toggle)
                            )
                        }

                        if (onGeneratePassword != null) {
                            IconButton(onClick = {
                                showGenerateDialog = true
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Cached,
                                    contentDescription = stringResource(id = R.string.action_generate_password)
                                )
                            }
                        }

                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .padding(horizontal = 16.dp),
                isError = showFieldErrors && editablePasswordState.password.isBlank(),
                supportingText = if (showFieldErrors && editablePasswordState.password.isBlank()) {
                    {
                        Text(text = stringResource(id = R.string.error_field_cannot_be_empty))
                    }
                } else null
            )

            if (showGenerateDialog) {
                PasswordGenerationDialog(
                    onGenerate = { strength, includeDigits, includeSymbols ->
                        if (onGeneratePassword != null) {
                            coroutineScope.launch {
                                isGenerating = true
                                val generatedPassword = onGeneratePassword(
                                    strength, includeDigits, includeSymbols
                                ).await()
                                if (generatedPassword == null) {
                                    Toast.makeText(
                                        context,
                                        R.string.error_could_not_generate_password,
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    editablePasswordState.passwordState
                                        .setTextAndPlaceCursorAtEnd(generatedPassword)
                                }
                                isGenerating = false
                            }
                            showGenerateDialog = false
                        }
                    },
                    onDismissRequest = {
                        showGenerateDialog = false
                    }
                )
            }
        }

        item(key = "password_url") {
            OutlinedTextField(
                state = editablePasswordState.urlState,
                label = { Text(text = stringResource(id = R.string.password_attr_url)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Uri),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .padding(horizontal = 16.dp),
                isError = showFieldErrors && !editablePasswordState.url.isValidURL(),
                supportingText = if (showFieldErrors && !editablePasswordState.url.isValidURL()) {
                    {
                        Text(text = stringResource(id = R.string.error_enter_valid_url))
                    }
                } else null
            )
        }

        item(key = "password_folder") {
            OutlinedClickableTextField(
                value = if (editablePasswordState.folder == FoldersApi.DEFAULT_FOLDER_UUID) {
                    stringResource(id = R.string.top_level_folder_name)
                } else {
                    folders.firstOrNull { it.id == editablePasswordState.folder }?.label
                        ?: stringResource(id = R.string.top_level_folder_name)
                },
                label = stringResource(id = R.string.folder),
                onClick = {
                    showFolderDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .padding(horizontal = 16.dp)
            )
        }

        itemsIndexed(
            items = editablePasswordState.customFields,
            key = { index, field -> "${index}_password_custom_${field.label}" }) { index, customField ->
            CustomFieldRow(
                customField = customField,
                showFieldErrors = showFieldErrors,
                onDelete = { editablePasswordState.customFields.removeAt(index) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .padding(horizontal = 16.dp)
            )
        }

        item(key = "password_notes") {
            OutlinedTextField(
                state = editablePasswordState.notesState,
                label = { Text(text = stringResource(id = R.string.password_attr_notes)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
                    .padding(horizontal = 16.dp)
            )
        }

        item(key = "custom_field_add") {
            EOutlinedButton (
                onClick = { showAddCustomFieldDialog = true },
                content = {
                    Text(text = stringResource(id = R.string.action_add_custom_field))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .padding(horizontal = 16.dp)
            )
        }

        item(key = "password_save") {
            Button(
                onClick = {
                    if (!editablePasswordState.isValid()) {
                        showFieldErrors = true
                    } else {
                        onSavePassword()
                    }
                },
                content = {
                    if (isUpdating) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Text(text = stringResource(id = R.string.action_save))
                    }
                },
                enabled = !isUpdating,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }

        if (isAutofillRequest) {
            item(key = "password_save_autofill") {
                Button(
                    onClick = {
                        if (!editablePasswordState.isValid()) {
                            showFieldErrors = true
                        } else {
                            editablePasswordState.replyAutofill = true
                            onSavePassword()
                        }
                    },
                    content = {
                        if (isUpdating) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Text(text = stringResource(id = R.string.action_save_autofill))
                        }
                    },
                    enabled = !isUpdating,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .padding(horizontal = 16.dp)
                )
            }
        }

        if (onDeletePassword != null) {
            item(key = "password_delete") {
                if (!isUpdating) {
                    EOutlinedButtonRed(
                        onClick = { showDeleteDialog = true },
                        content = {
                            Text(text = stringResource(id = R.string.action_delete_password))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .padding(horizontal = 16.dp)
                    )
                }
            }
        }

        item(key = "bottom_spacer") {
            Spacer(
                modifier = Modifier
                    .windowInsetsBottomHeight(WindowInsets.ime.add(WindowInsets.navigationBars))
                    .padding(bottom = 16.dp)
            )
        }

    }

    if (showDeleteDialog) {
        DeleteElementDialog(
            onConfirmButton = {
                showDeleteDialog = false
                onDeletePassword?.invoke()
            },
            onDismissRequest = {
                showDeleteDialog = false
            }
        )
    }

    if (showAddCustomFieldDialog) {
        AddCustomFieldDialog(
            onAddClick = { type, label ->
                editablePasswordState.customFields.add(
                    EditableCustomField(
                        type = type, label = label, initialValue = ""
                    )
                )
                showAddCustomFieldDialog = false
            },
            onDismissRequest = {
                showAddCustomFieldDialog = false
            }
        )
    }

    if (showFolderDialog) {
        SelectFolderDialog(
            folders = folders,
            currentFolder = editablePasswordState.folder,
            onSelectClick = { folder ->
                editablePasswordState.folder = folder
                showFolderDialog = false
            },
            onDismissRequest = {
                showFolderDialog = false
            }
        )
    }
}

@Composable
private fun CustomFieldRow(
    customField: EditableCustomField,
    showFieldErrors: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showValue by rememberSaveable {
        mutableStateOf(customField.type != CustomField.TYPE_SECRET)
    }

    val isError = when (customField.type) {
        CustomField.TYPE_URL -> showFieldErrors && !customField.value.isValidURL()
        CustomField.TYPE_EMAIL -> showFieldErrors && !customField.value.isValidEmail()
        else -> false
    }
    val supportingText: (@Composable () -> Unit)? = when (customField.type) {
        CustomField.TYPE_URL -> {
            if (showFieldErrors && !customField.value.isValidURL()) {
                {
                    Text(text = stringResource(id = R.string.error_enter_valid_url))
                }
            } else null
        }

        CustomField.TYPE_EMAIL -> {
            if (showFieldErrors && !customField.value.isValidEmail()) {
                {
                    Text(text = stringResource(id = R.string.error_enter_valid_email))
                }
            } else null
        }

        else -> null
    }
    val trailingIcon: @Composable () -> Unit = {
        Row {
            if (customField.type == CustomField.TYPE_SECRET) {
                IconButton(onClick = { showValue = !showValue }) {
                    Icon(
                        imageVector = if (showValue)
                            Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = stringResource(R.string.text_input_show_password_toggle)
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.action_delete)
                )
            }
        }
    }

    if (customField.type == CustomField.TYPE_SECRET) {
        OutlinedSecureTextField(
            state = customField.valueState,
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily(Font(R.font.dejavu_sans_mono))),
            label = { Text(text = customField.label) },
            textObfuscationMode = if (showValue)
                TextObfuscationMode.Visible else TextObfuscationMode.Hidden,
            trailingIcon = trailingIcon,
            modifier = modifier,
            isError = isError,
            supportingText = supportingText,
        )
    } else {
        OutlinedTextField(
            state = customField.valueState,
            label = { Text(text = customField.label) },
            lineLimits = TextFieldLineLimits.SingleLine,
            trailingIcon = trailingIcon,
            keyboardOptions = KeyboardOptions.Default.copy(
                keyboardType = when (customField.type) {
                    CustomField.TYPE_EMAIL -> KeyboardType.Email
                    CustomField.TYPE_URL -> KeyboardType.Uri
                    else -> KeyboardType.Text
                }
            ),
            modifier = modifier,
            isError = isError,
            supportingText = supportingText,
        )
    }
}

@Preview
@Composable
fun PasswordEditPreview() {
    ETheme {
        Surface {
            EditablePasswordView(
                editablePasswordState = rememberEditablePasswordState().apply {
                    customFields.add(
                        EditableCustomField(
                            type = CustomField.TYPE_TEXT,
                            label = "Custom field 1",
                            initialValue = ""
                        )
                    )
                    customFields.add(
                        EditableCustomField(
                            type = CustomField.TYPE_SECRET,
                            label = "Custom field 2",
                            initialValue = ""
                        )
                    )
                },
                folders = listOf(),
                isUpdating = false,
                isAutofillRequest = true,
                onSavePassword = { },
                onDeletePassword = { },
                onGeneratePassword = null
            )
        }
    }
}
