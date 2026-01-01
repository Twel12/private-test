/*
 * Copyright © MURENA SAS 2025.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package foundation.e.findmydevice.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import foundation.e.findmydevice.storage.PersistentStorage
import foundation.e.findmydevice.util.hasSimSupport
import foundation.e.findmydevice.util.hasTelephony

class FindMyDeviceContentProvider: ContentProvider() {

    companion object {
        const val AUTHORITY = "foundation.e.findmydevice.provider"
        private const val STATUS = "status"
        private const val STATUS_CODE = 1
        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, STATUS, STATUS_CODE)
        }
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        val context = context ?: return null
        val persistentStorage = PersistentStorage(context)

        return when (uriMatcher.match(uri)) {
            STATUS_CODE -> {
                var statusValue = persistentStorage.getStatus()
                if (!hasSimSupport(context) || !hasTelephony(context)) {
                    // If no SIM present (or not supported) do not offer to configure FMD from PaCo
                    // Consider it configured
                    statusValue = true
                }

                MatrixCursor(arrayOf(STATUS)).apply {
                    addRow(arrayOf(if (statusValue) 1 else 0))
                    setNotificationUri(context.contentResolver, uri)
                }
            }
            else -> {
                throw IllegalArgumentException("Unknown URI: $uri")
            }
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException("Not supported")
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        throw UnsupportedOperationException("Not supported")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        throw UnsupportedOperationException("Not supported")
    }

    override fun getType(uri: Uri): String {
        return when (uriMatcher.match(uri)) {
            STATUS_CODE -> "vnd.android.cursor.item/$AUTHORITY.$STATUS"
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
    }
}
