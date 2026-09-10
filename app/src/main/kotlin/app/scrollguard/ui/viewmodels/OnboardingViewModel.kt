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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.scrollguard.utils.UserPreferencesProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * UI state for onboarding flow.
 *
 * @property isOnboardingCompleted Whether user has completed onboarding
 * @property showDisclosure Whether to show disclosure screen
 */
data class OnboardingState(
    val isOnboardingCompleted: Boolean = false,
    val showDisclosure: Boolean = false,
)

/**
 * ViewModel for onboarding flow.
 *
 * Manages onboarding state and coordinates transition to disclosure
 * and main app screens.
 *
 * @property application Application instance for context access
 */
class OnboardingViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val userPreferencesProvider = UserPreferencesProvider(application)

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    init {
        Timber.d("OnboardingViewModel initialized")
        observeOnboardingState()
    }

    /**
     * Observes onboarding completion status from preferences.
     */
    private fun observeOnboardingState() {
        viewModelScope.launch {
            userPreferencesProvider.getOnboardingCompleted().collect { completed ->
                Timber.d("Onboarding completed status: $completed")
                _state.value = _state.value.copy(isOnboardingCompleted = completed)
            }
        }
    }

    /**
     * Mark onboarding as completed and show disclosure screen.
     */
    fun completeOnboarding() {
        Timber.i("Completing onboarding")
        viewModelScope.launch {
            userPreferencesProvider.setOnboardingCompleted(true)
            _state.value = _state.value.copy(showDisclosure = true)
        }
    }

    /**
     * Show disclosure screen (for re-triggering from main screen).
     */
    fun showDisclosure() {
        Timber.d("Showing disclosure screen")
        _state.value = _state.value.copy(showDisclosure = true)
    }

    /**
     * Hide disclosure screen.
     */
    fun hideDisclosure() {
        Timber.d("Hiding disclosure screen")
        _state.value = _state.value.copy(showDisclosure = false)
    }

    override fun onCleared() {
        super.onCleared()
        Timber.d("OnboardingViewModel cleared")
    }
}
