/* Copyright 2026 Scroll Guard contributors. Licensed under the Apache License, Version 2.0. */
package app.scrollguard.services

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import app.scrollguard.models.InstagramProtectionMode
import timber.log.Timber

data class ProtectionRuntimeState(
    val connected: Boolean = false,
    val lastInstagramCheck: String? = null,
    val preferencesApplied: Boolean = false,
    val instagramMode: InstagramProtectionMode = InstagramProtectionMode.FEED_LOCK,
    val instagramEnabled: Boolean = false,
)

/** Actual service state, separate from Android's saved accessibility permission. */
object ProtectionRuntime {
    private val mutableState = MutableStateFlow(ProtectionRuntimeState())
    val state = mutableState.asStateFlow()

    fun connected(value: Boolean) {
        mutableState.update { it.copy(connected = value) }
    }

    fun instagramCheck(description: String) {
        if (mutableState.value.lastInstagramCheck != description) Timber.d("Instagram: %s", description)
        mutableState.update { it.copy(lastInstagramCheck = description) }
    }

    fun configured(mode: InstagramProtectionMode, enabled: Boolean) {
        mutableState.update { it.copy(preferencesApplied = true, instagramMode = mode, instagramEnabled = enabled) }
    }
}
