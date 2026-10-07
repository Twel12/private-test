package com.hegocre.nextcloudpasswords.companion

import com.hegocre.nextcloudpasswords.data.password.CustomField

data class VaultEntry(
    val id: String,
    val revision: String,
    val label: String,
    val username: String,
    val secret: String,
    val url: String,
    val notes: String,
    val customFields: List<CustomField>,
    val folder: String,
    val favorite: Boolean,
    val hidden: Boolean,
    val trashed: Boolean,
    val edited: Int,
    val created: Long,
) {
    override fun toString() = "VaultEntry(id=$id, trashed=$trashed)"
}

data class EntryDraft(
    val label: String,
    val username: String,
    val secret: String,
    val url: String,
    val notes: String,
    val customFields: List<CustomField>,
) {
    override fun toString() = "EntryDraft(label=$label)"
}
