/*
 * Copyright 2025 Atick Faisal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.scrollguard.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.scrollguard.models.TrackedPackage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber

/**
 * Manages user preferences for tracked packages using DataStore.
 *
 * Provides methods to store and retrieve which app packages are enabled
 * for short-form content blocking, as well as onboarding state.
 *
 * @property context Application context for DataStore access
 */
class UserPreferencesProvider(private val context: Context) {

    data class StrictModeSettings(
        val enabled: Boolean = false,
        val pendingTarget: String? = null,
        val unlockAtMillis: Long = 0L,
    )

    private val userPreferencesKey = stringPreferencesKey("app.scrollguard.preferences")
    private val onboardingCompletedKey =
        booleanPreferencesKey("app.scrollguard.onboarding_completed")
    private val disclosureAcceptedKey =
        booleanPreferencesKey("app.scrollguard.disclosure_accepted")
    private val strictModeEnabledKey =
        booleanPreferencesKey("app.scrollguard.strict_mode_enabled")
    private val strictModePendingTargetKey =
        stringPreferencesKey("app.scrollguard.strict_mode_pending_target")
    private val strictModeUnlockAtKey =
        longPreferencesKey("app.scrollguard.strict_mode_unlock_at")

    /**
     * Gets the list of currently tracked (enabled) package names.
     *
     * @return Flow emitting list of package names that are enabled for blocking
     */
    fun getTrackedPackages(): Flow<List<String>> {
        return context.dataStore.data.map { preferences ->
            val packages = preferences[userPreferencesKey]?.split(",")?.filter { it.isNotBlank() }
                ?: PackageConstants.DEFAULT_ENABLED_PACKAGES
            Timber.d("Getting tracked packages: $packages")
            packages
        }
    }

    /**
     * Updates the list of tracked packages.
     *
     * @param packages List of package names to enable for blocking
     */
    suspend fun setTrackedPackages(packages: List<String>) {
        val packagesString = packages.joinToString(",")
        Timber.d("Setting tracked packages: $packagesString")
        context.dataStore.edit { preferences ->
            preferences[userPreferencesKey] = packagesString
        }
    }

    /**
     * Gets all available packages with their current enabled status.
     *
     * @return Flow emitting list of [TrackedPackage] with updated enabled state
     */
    fun getTrackedPackagesWithStatus(): Flow<List<TrackedPackage>> {
        return getTrackedPackages().map { enabledPackages ->
            PackageConstants.AVAILABLE_PACKAGES.map { pkg ->
                pkg.copy(isEnabled = enabledPackages.contains(pkg.packageName))
            }
        }
    }

    /**
     * Toggles blocking for a specific package.
     *
     * @param packageName Package identifier to toggle
     * @param enabled true to enable blocking, false to disable
     */
    suspend fun togglePackage(packageName: String, enabled: Boolean) {
        Timber.d("Toggling package: $packageName, enabled: $enabled")
        val currentPackages = getTrackedPackages().first().toMutableList()

        if (enabled && !currentPackages.contains(packageName)) {
            currentPackages.add(packageName)
        } else if (!enabled) {
            currentPackages.remove(packageName)
        }

        setTrackedPackages(currentPackages)
    }

    fun getStrictModeSettings(): Flow<StrictModeSettings> {
        return context.dataStore.data.map { preferences ->
            StrictModeSettings(
                enabled = preferences[strictModeEnabledKey] ?: false,
                pendingTarget = preferences[strictModePendingTargetKey],
                unlockAtMillis = preferences[strictModeUnlockAtKey] ?: 0L,
            )
        }
    }

    /** Enables Strict Mode immediately. Disabling it must go through a delayed request. */
    suspend fun enableStrictMode() {
        context.dataStore.edit { preferences ->
            preferences[strictModeEnabledKey] = true
            preferences.remove(strictModePendingTargetKey)
            preferences.remove(strictModeUnlockAtKey)
        }
    }

    /** Starts one persistent 30-minute request to disable a blocker or Strict Mode itself. */
    suspend fun requestStrictModeUnlock(target: String, nowMillis: Long) {
        context.dataStore.edit { preferences ->
            if (preferences[strictModePendingTargetKey] == null) {
                preferences[strictModePendingTargetKey] = target
                preferences[strictModeUnlockAtKey] = StrictModePolicy.unlockAt(nowMillis)
            }
        }
    }

    suspend fun cancelStrictModeUnlock() {
        context.dataStore.edit { preferences ->
            preferences.remove(strictModePendingTargetKey)
            preferences.remove(strictModeUnlockAtKey)
        }
    }

    /** Applies an expired request atomically. Returns true when a request was completed. */
    suspend fun completeStrictModeUnlockIfExpired(nowMillis: Long): Boolean {
        var completed = false
        context.dataStore.edit { preferences ->
            val target = preferences[strictModePendingTargetKey]
            val unlockAt = preferences[strictModeUnlockAtKey] ?: 0L
            if (target != null && StrictModePolicy.isExpired(unlockAt, nowMillis)) {
                if (target == StrictModePolicy.STRICT_MODE_TARGET) {
                    preferences[strictModeEnabledKey] = false
                } else {
                    val packages = preferences[userPreferencesKey]
                        ?.split(",")
                        ?.filter { it.isNotBlank() }
                        ?.toMutableList()
                        ?: PackageConstants.DEFAULT_ENABLED_PACKAGES.toMutableList()
                    packages.remove(target)
                    preferences[userPreferencesKey] = packages.joinToString(",")
                }
                preferences.remove(strictModePendingTargetKey)
                preferences.remove(strictModeUnlockAtKey)
                completed = true
            }
        }
        return completed
    }

    /**
     * Gets whether onboarding has been completed.
     *
     * @return Flow emitting true if onboarding is completed, false otherwise
     */
    fun getOnboardingCompleted(): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[onboardingCompletedKey] ?: false
        }
    }

    /**
     * Sets onboarding completion status.
     *
     * @param completed true to mark onboarding as completed
     */
    suspend fun setOnboardingCompleted(completed: Boolean) {
        Timber.i("Setting onboarding completed: $completed")
        context.dataStore.edit { preferences ->
            preferences[onboardingCompletedKey] = completed
        }
    }

    /**
     * Sets disclosure acceptance status.
     *
     * @param accepted true to mark disclosure as accepted
     */
    suspend fun setDisclosureAccepted(accepted: Boolean) {
        Timber.i("Setting disclosure accepted: $accepted")
        context.dataStore.edit { preferences ->
            preferences[disclosureAcceptedKey] = accepted
        }
    }
}

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
