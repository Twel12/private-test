/*
 * Copyright (C) 2026 MURENA SAS
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.hegocre.nextcloudpasswords.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/** Raised by [SsoOkHttpRequest] on a Nextcloud 2FA `303`; UI observes [required] to mint an app password. */
object AppPasswordRequest {

    data class Prompt(
        val accountName: String,
        val accountType: String
    )

    private val _required = MutableStateFlow<Prompt?>(null)
    val required: StateFlow<Prompt?> = _required.asStateFlow()

    private val _blockingRequests = MutableStateFlow(false)

    private var paused = false
    private var pendingPrompt: Prompt? = null
    private var disabled = false

    @Synchronized
    fun signal(
        accountName: String,
        accountType: String
    ) {
        if (accountName.isBlank() || accountType.isBlank()) return
        _blockingRequests.value = true
        if (disabled) return
        val prompt = Prompt(accountName, accountType)
        if (paused) {
            pendingPrompt = prompt
        } else {
            _required.value = prompt
        }
    }

    /** Stop surfacing the prompt this session; requests stay suspended until a later success [clear]s it. */
    @Synchronized
    fun disable() {
        disabled = true
        paused = false
        pendingPrompt = null
        _required.value = null
        _blockingRequests.value = true
    }

    /** Hide the prompt and defer 303s while the AccountManager flow or return re-check is active. */
    @Synchronized
    fun pause() {
        paused = true
        pendingPrompt = null
        _blockingRequests.value = true
        _required.value = null
    }

    /** Resume reacting to 303s, optionally replaying one observed during the paused re-check. */
    @Synchronized
    fun resume(showPendingPrompt: Boolean = false) {
        paused = false
        val prompt = pendingPrompt.takeIf { showPendingPrompt }
        pendingPrompt = null
        if (prompt != null) {
            _blockingRequests.value = true
            _required.value = prompt
        } else {
            _blockingRequests.value = false
        }
    }

    @Synchronized
    fun clear() {
        paused = false
        pendingPrompt = null
        disabled = false
        _blockingRequests.value = false
        _required.value = null
    }

    fun isBlockingRequests(): Boolean = _blockingRequests.value

    suspend fun awaitRequestsUnblocked() {
        _blockingRequests.first { blocking -> !blocking }
    }
}
