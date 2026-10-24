/* Copyright 2026 Scroll Guard contributors. Licensed under the Apache License, Version 2.0. */
package app.scrollguard.utils

/** Values read and written together in one DataStore edit. Existing storage keys stay unchanged. */
internal data class TrackedPackageState(
    val packages: List<String>,
    val pendingTarget: String? = null,
    val unlockAtMillis: Long = 0L,
)

internal object TrackedPackagePolicy {
    fun setEnabled(
        state: TrackedPackageState,
        packageName: String,
        enabled: Boolean,
        strictModeEnabled: Boolean,
        nowMillis: Long,
    ): TrackedPackageState {
        // A deliberate enable cancels an earlier request to disable this same app. Otherwise
        // that request could silently switch protection off after the user enabled it again.
        val current = if (enabled && state.pendingTarget == packageName) {
            state.copy(pendingTarget = null, unlockAtMillis = 0L)
        } else state
        val requested = if (enabled) current.packages + packageName
        else current.packages.filterNot { it == packageName }
        return setPackages(current, requested, strictModeEnabled, nowMillis)
    }

    fun setPackages(
        state: TrackedPackageState,
        requestedPackages: List<String>,
        strictModeEnabled: Boolean,
        nowMillis: Long,
    ): TrackedPackageState {
        val requested = requestedPackages.distinct()
        val removed = state.packages.filterNot { it in requested }
        if (!strictModeEnabled || removed.isEmpty()) return state.copy(packages = requested)
        return state.copy(
            packages = (state.packages + requested).distinct(),
            pendingTarget = state.pendingTarget ?: removed.first(),
            unlockAtMillis = if (state.pendingTarget == null) StrictModePolicy.unlockAt(nowMillis)
            else state.unlockAtMillis,
        )
    }
}
