package com.frost.time.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.frost.time.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = UsageRepository(application)

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _selectedRange = MutableStateFlow(TimeRange.TODAY)
    val selectedRange: StateFlow<TimeRange> = _selectedRange.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private var foregroundTickerJob: Job? = null

    init {
        loadData(showLoadingIndicator = true)
    }

    fun onResume() {
        // Refresh immediately upon unlocking/returning to app
        loadData(showLoadingIndicator = false)
        startForegroundTicker()
    }

    fun onPause() {
        // Halt ticker for 0.0% idle battery
        stopForegroundTicker()
    }

    private fun startForegroundTicker() {
        stopForegroundTicker()
        foregroundTickerJob = viewModelScope.launch {
            while (isActive) {
                delay(30_000L) // 30s update while actively viewed
                loadData(showLoadingIndicator = false)
            }
        }
    }

    private fun stopForegroundTicker() {
        foregroundTickerJob?.cancel()
        foregroundTickerJob = null
    }

    fun onRangeSelected(range: TimeRange) {
        _selectedRange.value = range
        loadData(showLoadingIndicator = false)
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun loadData(showLoadingIndicator: Boolean = false) {
        viewModelScope.launch {
            if (showLoadingIndicator || _uiState.value !is UiState.Success) {
                _uiState.value = UiState.Loading
            }

            // Strictly require Root
            val rootElevated = repository.checkAndElevateRoot()
            if (!rootElevated) {
                _uiState.value = UiState.RootRequired
                return@launch
            }

            // Fetch screen stats via root
            try {
                val data = repository.fetchDashboardData(_selectedRange.value)
                _uiState.value = UiState.Success(data)
            } catch (e: Exception) {
                if (_uiState.value !is UiState.Success) {
                    _uiState.value = UiState.Error(e.message ?: "Failed to read screen stats via root")
                }
            }
        }
    }

    fun requestRootAccess() {
        loadData(showLoadingIndicator = true)
    }

    fun launchApp(packageName: String) {
        repository.openApp(packageName)
    }

    fun toggleAppFreeze(packageName: String, freeze: Boolean) {
        viewModelScope.launch {
            if (freeze) {
                repository.freezeApp(packageName)
            } else {
                repository.unfreezeApp(packageName)
            }
            loadData(showLoadingIndicator = false)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopForegroundTicker()
    }
}
