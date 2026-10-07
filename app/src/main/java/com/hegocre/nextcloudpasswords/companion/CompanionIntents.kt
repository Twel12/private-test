package com.hegocre.nextcloudpasswords.companion

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri

object CompanionIntents {
    const val EXTRA_TOKEN = "foundation.e.passwords.companion.extra.TOKEN"

    fun request(context: Context, token: String): PendingIntent {
        val intent = Intent(context, CompanionRequestActivity::class.java)
            .setData(Uri.fromParts("companion", token, null))
            .putExtra(EXTRA_TOKEN, token)
        return PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT,
        )
    }
}
