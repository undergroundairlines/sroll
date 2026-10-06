/* Copyright 2026 Scroll Guard contributors. Licensed under the Apache License, Version 2.0. */
package app.scrollguard.services

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ProtectionRuntimeState(
    val connected: Boolean = false,
    val lastInstagramCheck: String? = null,
)

/** Actual service state, separate from Android's saved accessibility permission. */
object ProtectionRuntime {
    private val mutableState = MutableStateFlow(ProtectionRuntimeState())
    val state = mutableState.asStateFlow()

    fun connected(value: Boolean) {
        mutableState.update { it.copy(connected = value) }
    }

    fun instagramCheck(description: String) {
        mutableState.update { it.copy(lastInstagramCheck = description) }
    }
}
