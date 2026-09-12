/*
 * Copyright 2026 Scroll Guard contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package app.scrollguard.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.scrollguard.models.ScreenTimeReport
import app.scrollguard.models.UsagePeriod
import app.scrollguard.services.ScreenTimeRepository
import app.scrollguard.widgets.ScreenTimeWidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ScreenTimeState(
    val hasUsageAccess: Boolean = false,
    val isLoading: Boolean = false,
    val selectedPeriod: UsagePeriod = UsagePeriod.DAY,
    val report: ScreenTimeReport? = null,
    val errorMessage: String? = null,
)

class ScreenTimeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ScreenTimeRepository(application)
    private val _state = MutableStateFlow(ScreenTimeState())
    val state: StateFlow<ScreenTimeState> = _state.asStateFlow()
    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun setPeriod(period: UsagePeriod) {
        if (_state.value.selectedPeriod == period) return
        _state.update { it.copy(selectedPeriod = period, report = null) }
        refresh()
    }

    fun refresh() {
        val hasAccess = repository.hasUsageAccess()
        _state.update {
            it.copy(
                hasUsageAccess = hasAccess,
                isLoading = hasAccess,
                errorMessage = null,
            )
        }
        if (!hasAccess) return

        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            try {
                val report = withContext(Dispatchers.IO) {
                    repository.load(_state.value.selectedPeriod)
                }
                _state.update { it.copy(isLoading = false, report = report) }
                ScreenTimeWidgetUpdater.updateAll(getApplication())
            } catch (_: RuntimeException) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Screen-time data could not be read yet.",
                    )
                }
            }
        }
    }
}
