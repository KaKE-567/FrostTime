package com.frost.time.ui.screens

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.frost.time.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = UsageRepository(application)

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _selectedRange = MutableStateFlow(TimeRange.TODAY)
    val selectedRange: StateFlow<TimeRange> = _selectedRange.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        loadData()
    }

    fun onRangeSelected(range: TimeRange) {
        _selectedRange.value = range
        loadData()
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

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

            // 2. Fetch screen time stats
            try {
                val data = repository.fetchDashboardData(_selectedRange.value)
                _uiState.value = UiState.Success(data)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Failed to query usage stats")
            }
        }
    }

    fun requestRootGrant() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val success = repository.grantPermissionsWithRoot()
            if (success) {
                loadData()
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
            loadData()
        }
    }
}
