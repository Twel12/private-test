package foundation.e.passwords.companion

import android.app.PendingIntent
import android.os.Bundle
import foundation.e.passwords.companion.CompanionProtocol.KEY_ACTION
import foundation.e.passwords.companion.CompanionProtocol.KEY_CODE
import foundation.e.passwords.companion.CompanionProtocol.KEY_CREATED
import foundation.e.passwords.companion.CompanionProtocol.KEY_CREATED_AT
import foundation.e.passwords.companion.CompanionProtocol.KEY_EXTRA_FIELDS
import foundation.e.passwords.companion.CompanionProtocol.KEY_ID
import foundation.e.passwords.companion.CompanionProtocol.KEY_INTENT
import foundation.e.passwords.companion.CompanionProtocol.KEY_LABEL
import foundation.e.passwords.companion.CompanionProtocol.KEY_MODE
import foundation.e.passwords.companion.CompanionProtocol.KEY_NOTES
import foundation.e.passwords.companion.CompanionProtocol.KEY_REASON
import foundation.e.passwords.companion.CompanionProtocol.KEY_RETRYABLE
import foundation.e.passwords.companion.CompanionProtocol.KEY_SECRET
import foundation.e.passwords.companion.CompanionProtocol.KEY_STATUS
import foundation.e.passwords.companion.CompanionProtocol.KEY_USERNAME
import foundation.e.passwords.companion.CompanionProtocol.KEY_VERSION

object CompanionCodec {

    private const val FOUND = "FOUND"
    private const val NOT_FOUND = "NOT_FOUND"
    private const val SAVED = "SAVED"
    private const val ALREADY_EXISTS = "ALREADY_EXISTS"
    private const val DELETED = "DELETED"
    private const val NEEDS_USER = "NEEDS_USER"
    private const val FAILED = "FAILED"

    fun getRequest(id: String): Bundle = versioned().apply { putString(KEY_ID, id) }

    fun deleteRequest(id: String): Bundle = getRequest(id)

    fun saveRequest(request: SaveRequest): Bundle = versioned().apply {
        putString(KEY_ID, request.id)
        putString(KEY_SECRET, request.secret)
        putString(KEY_MODE, request.mode.name)
        putString(KEY_LABEL, request.presentation.label)
        putString(KEY_USERNAME, request.presentation.username)
        putString(KEY_NOTES, request.presentation.notes)
        putBundle(KEY_EXTRA_FIELDS, Bundle().apply {
            request.presentation.extraDataFields.forEach { (label, value) -> putString(label, value) }
        })
    }

    fun readId(bundle: Bundle?): String? = bundle?.getString(KEY_ID)

    fun readSaveRequest(bundle: Bundle?): SaveRequest? {
        if (bundle == null) return null
        val id = bundle.getString(KEY_ID) ?: return null
        val secret = bundle.getString(KEY_SECRET) ?: return null
        val mode = SaveMode.entries.firstOrNull { it.name == bundle.getString(KEY_MODE) } ?: return null
        val extras = bundle.getBundle(KEY_EXTRA_FIELDS)
        val fields = extras?.keySet()?.associateWith { extras.getString(it).orEmpty() }.orEmpty()
        return SaveRequest(
            id = id,
            secret = secret,
            mode = mode,
            presentation = EntryPresentation(
                label = bundle.getString(KEY_LABEL).orEmpty(),
                username = bundle.getString(KEY_USERNAME).orEmpty(),
                notes = bundle.getString(KEY_NOTES).orEmpty(),
                extraDataFields = fields,
            ),
        )
    }

    fun encode(result: CompanionResult): Bundle = versioned().apply {
        when (result) {
            is Found -> {
                putString(KEY_STATUS, FOUND)
                putString(KEY_SECRET, result.secret)
                putLong(KEY_CREATED_AT, result.createdAt)
            }
            NotFound -> putString(KEY_STATUS, NOT_FOUND)
            is Saved -> {
                putString(KEY_STATUS, SAVED)
                putBoolean(KEY_CREATED, result.created)
            }
            AlreadyExists -> putString(KEY_STATUS, ALREADY_EXISTS)
            Deleted -> putString(KEY_STATUS, DELETED)
            is NeedsUser -> {
                putString(KEY_STATUS, NEEDS_USER)
                putParcelable(KEY_INTENT, result.intent)
                putString(KEY_ACTION, result.action)
                putString(KEY_REASON, result.reason)
            }
            is Failed -> {
                putString(KEY_STATUS, FAILED)
                putString(KEY_CODE, result.code)
                putBoolean(KEY_RETRYABLE, result.retryable)
            }
        }
    }

    fun decodeGet(bundle: Bundle?): GetResult {
        if (bundle == null) return Failed(FailureCode.CANCELED)
        return when (bundle.getString(KEY_STATUS)) {
            FOUND -> bundle.getString(KEY_SECRET)?.let { Found(it, bundle.getLong(KEY_CREATED_AT)) } ?: unknown()
            NOT_FOUND -> NotFound
            else -> decodeCommon(bundle)
        }
    }

    fun decodeSave(bundle: Bundle?): SaveResult {
        if (bundle == null) return Failed(FailureCode.CANCELED)
        return when (bundle.getString(KEY_STATUS)) {
            SAVED -> Saved(bundle.getBoolean(KEY_CREATED))
            ALREADY_EXISTS -> AlreadyExists
            else -> decodeCommon(bundle)
        }
    }

    fun decodeDelete(bundle: Bundle?): DeleteResult {
        if (bundle == null) return Failed(FailureCode.CANCELED)
        return when (bundle.getString(KEY_STATUS)) {
            DELETED -> Deleted
            NOT_FOUND -> NotFound
            else -> decodeCommon(bundle)
        }
    }

    private fun decodeCommon(bundle: Bundle): CommonResult = when (bundle.getString(KEY_STATUS)) {
        NEEDS_USER -> {
            @Suppress("DEPRECATION")
            val intent = bundle.getParcelable<PendingIntent>(KEY_INTENT)
            val action = bundle.getString(KEY_ACTION)
            if (intent == null || action == null) unknown() else NeedsUser(intent, action, bundle.getString(KEY_REASON).orEmpty())
        }
        FAILED -> {
            val code = bundle.getString(KEY_CODE) ?: FailureCode.UNKNOWN
            Failed(code, bundle.getBoolean(KEY_RETRYABLE, FailureCode.isRetryable(code)))
        }
        else -> unknown()
    }

    private fun unknown() = Failed(FailureCode.UNKNOWN)

    private fun versioned() = Bundle().apply { putInt(KEY_VERSION, CompanionProtocol.VERSION) }
}
