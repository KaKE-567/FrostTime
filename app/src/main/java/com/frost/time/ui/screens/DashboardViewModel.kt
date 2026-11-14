package com.frost.time.ui.screens

import android.app.Application
import android.content.Intent
import android.provider.Settings
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
        // Refresh immediately upon unlocking/returning to app without full screen loading flicker
        loadData(showLoadingIndicator = false)
        startForegroundTicker()
    }

    fun onPause() {
        // Immediately halt background ticking for 0.0% battery drain
        stopForegroundTicker()
    }

    private fun startForegroundTicker() {
        stopForegroundTicker()
        foregroundTickerJob = viewModelScope.launch {
            while (isActive) {
                delay(30_000L) // Refresh screen time every 30 seconds while screen is actively on
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

            // 1. Verify permissions
            if (!repository.isPermissionGranted()) {
                val hasRoot = repository.isRootAvailable()
                if (hasRoot) {
                    val granted = repository.grantPermissionsWithRoot()
                    if (!granted) {
                        _uiState.value = UiState.NeedPermission(isRootAvailable = true)
                        return@launch
                    }
                } else {
                    _uiState.value = UiState.NeedPermission(isRootAvailable = false)
                    return@launch
                }
            }

            // 2. Fetch screen time stats on-demand
            try {
                val data = repository.fetchDashboardData(_selectedRange.value)
                _uiState.value = UiState.Success(data)
            } catch (e: Exception) {
                if (_uiState.value !is UiState.Success) {
                    _uiState.value = UiState.Error(e.message ?: "Failed to query usage stats")
                }
            }
        }
    }

    fun requestRootGrant() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val success = repository.grantPermissionsWithRoot()
            if (success) {
                loadData(showLoadingIndicator = false)
            } else {
                _uiState.value = UiState.NeedPermission(isRootAvailable = repository.isRootAvailable())
            }
        }
    }

    fun openSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(intent)
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
