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

package app.scrollguard.ui.viewmodels

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.scrollguard.models.DetectionDiagnostic
import app.scrollguard.models.TrackedPackage
import app.scrollguard.services.DetectionDiagnostics
import app.scrollguard.utils.AccessibilityServiceManager
import app.scrollguard.utils.StrictModePolicy
import app.scrollguard.utils.UserPreferencesProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * UI state for the main screen.
 *
 * @property isGranted Whether accessibility service permission is granted
 * @property isChecking Whether a permission check is in progress
 * @property trackedPackages List of available packages with their enabled status
 * @property showDisclosure Whether to show disclosure screen
 */
data class ServiceState(
    val isGranted: Boolean = false,
    val isChecking: Boolean = false,
    val trackedPackages: List<TrackedPackage> = emptyList(),
    val diagnostics: List<DetectionDiagnostic> = emptyList(),
    val strictModeEnabled: Boolean = false,
    val strictModePendingTarget: String? = null,
    val strictModeRemainingSeconds: Long = 0L,
    val showDisclosure: Boolean = false,
)

/**
 * ViewModel for the main screen.
 *
 * Manages accessibility service permission state, package tracking preferences,
 * and coordinates with the settings screen for permission requests.
 *
 * @property application Application instance for context access
 */
class MainViewModel(
    application: Application,
) : AndroidViewModel(application) {

    companion object {
        private const val SERVICE_NAME: String =
            "app.scrollguard/app.scrollguard.services.ShortFormContentBlockerService"
    }

    private val userPreferencesProvider = UserPreferencesProvider(application)

    private val _serviceState = MutableStateFlow(ServiceState())
    val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()

    private var isMonitoring = false

    init {
        Timber.d("MainViewModel initialized")
        observeTrackedPackages()
        observeDiagnostics()
        observeStrictMode()
        startStrictModeTicker()
    }

    private fun observeDiagnostics() {
        viewModelScope.launch {
            DetectionDiagnostics.records.collect { records ->
                _serviceState.update {
                    it.copy(diagnostics = records.values.sortedBy { record -> record.packageName })
                }
            }
        }
    }

    fun clearDiagnostics() {
        DetectionDiagnostics.clear()
    }

    /**
     * Observes tracked packages from preferences and updates UI state.
     */
    private fun observeTrackedPackages() {
        viewModelScope.launch {
            userPreferencesProvider.getTrackedPackagesWithStatus().collect { packages ->
                Timber.d(
                    "Tracked packages updated: ${
                        packages.filter { it.isEnabled }.map { it.displayName }
                    }",
                )
                _serviceState.update { it.copy(trackedPackages = packages) }
            }
        }
    }

    private fun observeStrictMode() {
        viewModelScope.launch {
            userPreferencesProvider.getStrictModeSettings().collect { settings ->
                _serviceState.update {
                    it.copy(
                        strictModeEnabled = settings.enabled,
                        strictModePendingTarget = settings.pendingTarget,
                        strictModeRemainingSeconds = StrictModePolicy.remainingSeconds(
                            settings.unlockAtMillis,
                            System.currentTimeMillis(),
                        ),
                    )
                }
            }
        }
    }

    private fun startStrictModeTicker() {
        viewModelScope.launch {
            while (isActive) {
                val settings = userPreferencesProvider.getStrictModeSettings().first()
                if (settings.pendingTarget != null) {
                    userPreferencesProvider.completeStrictModeUnlockIfExpired(
                        System.currentTimeMillis(),
                    )
                    _serviceState.update {
                        it.copy(
                            strictModeRemainingSeconds = StrictModePolicy.remainingSeconds(
                                settings.unlockAtMillis,
                                System.currentTimeMillis(),
                            ),
                        )
                    }
                }
                delay(1000L)
            }
        }
    }

    /**
     * Check accessibility service status.
     * This is called automatically on lifecycle resume and periodically.
     */
    fun checkPermission(context: Context) {
        viewModelScope.launch {
            Timber.d("Checking accessibility service status")
            _serviceState.update { it.copy(isChecking = true) }

            val isGranted = AccessibilityServiceManager.isAccessibilityServiceEnabled(
                context,
                SERVICE_NAME,
            )

            _serviceState.update {
                it.copy(
                    isGranted = isGranted,
                    isChecking = false,
                )
            }

            Timber.i("Service check complete: isGranted=$isGranted")
        }
    }

    /**
     * Show disclosure screen.
     */
    fun showDisclosure() {
        Timber.d("Showing disclosure screen")
        _serviceState.update { it.copy(showDisclosure = true) }
    }

    /**
     * Hide disclosure screen.
     */
    fun hideDisclosure() {
        Timber.d("Hiding disclosure screen")
        _serviceState.update { it.copy(showDisclosure = false) }
    }

    /**
     * Accept disclosure and open accessibility settings.
     * Marks disclosure as accepted and opens system settings.
     */
    fun acceptDisclosure(context: Context) {
        Timber.i("Disclosure accepted - opening accessibility settings")
        viewModelScope.launch {
            userPreferencesProvider.setDisclosureAccepted(true)
        }
        hideDisclosure()
        openAccessibilitySettings(context)
    }

    /**
     * Open Android accessibility settings page.
     * Automatically starts monitoring for service status changes.
     */
    fun openAccessibilitySettings(context: Context) {
        Timber.d("Opening accessibility settings")
        AccessibilityServiceManager.openAccessibilitySettings(context)
        startMonitoring(context)
    }

    /**
     * Start monitoring permission changes when user goes to settings.
     * Checks every 1 second while monitoring is active.
     */
    private fun startMonitoring(context: Context) {
        if (isMonitoring) {
            Timber.v("Already monitoring permission changes")
            return
        }

        isMonitoring = true
        Timber.d("Starting permission monitoring (1s interval)")

        viewModelScope.launch {
            while (isActive && isMonitoring) {
                delay(1000L)
                checkPermission(context)

                // Stop monitoring if service is enabled
                if (_serviceState.value.isGranted) {
                    Timber.i("Permission granted - stopping monitoring")
                    stopMonitoring()
                    break
                }
            }
        }
    }

    /**
     * Stop monitoring permission changes.
     */
    private fun stopMonitoring() {
        Timber.d("Stopping permission monitoring")
        isMonitoring = false
    }

    /**
     * Called when app resumes from background.
     * Performs a fresh permission check.
     */
    fun onResume(context: Context) {
        Timber.d("ViewModel onResume - checking permission")
        checkPermission(context)
    }

    /**
     * Toggle tracking for a specific package.
     */
    fun togglePackageTracking(packageName: String, enabled: Boolean) {
        Timber.i("Toggling package tracking: $packageName -> $enabled")
        viewModelScope.launch {
            if (!enabled && _serviceState.value.strictModeEnabled) {
                userPreferencesProvider.requestStrictModeUnlock(
                    target = packageName,
                    nowMillis = System.currentTimeMillis(),
                )
            } else {
                userPreferencesProvider.togglePackage(packageName, enabled)
            }
        }
    }

    fun toggleStrictMode(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                userPreferencesProvider.enableStrictMode()
            } else {
                userPreferencesProvider.requestStrictModeUnlock(
                    target = StrictModePolicy.STRICT_MODE_TARGET,
                    nowMillis = System.currentTimeMillis(),
                )
            }
        }
    }

    fun cancelStrictModeUnlock() {
        viewModelScope.launch {
            userPreferencesProvider.cancelStrictModeUnlock()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopMonitoring()
        Timber.d("MainViewModel cleared")
    }
}
