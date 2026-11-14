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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = UsageRepository(application)

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val pastDays: List<DayItem> = generatePastDays(count = 30)

    private val _selectedDay = MutableStateFlow(pastDays.last())
    val selectedDay: StateFlow<DayItem> = _selectedDay.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private var foregroundTickerJob: Job? = null

    init {
        loadDataForSelectedDay(showLoadingIndicator = true)
    }

    fun onResume() {
        // Only live-tick if currently viewing Today
        if (_selectedDay.value.isToday) {
            loadDataForSelectedDay(showLoadingIndicator = false)
            startForegroundTicker()
        }
    }

    fun onPause() {
        stopForegroundTicker()
    }

    private fun startForegroundTicker() {
        stopForegroundTicker()
        foregroundTickerJob = viewModelScope.launch {
            while (isActive) {
                delay(30_000L) // Refresh every 30 seconds while screen is on
                if (_selectedDay.value.isToday) {
                    loadDataForSelectedDay(showLoadingIndicator = false)
                }
            }
        }
    }

    private fun stopForegroundTicker() {
        foregroundTickerJob?.cancel()
        foregroundTickerJob = null
    }

    fun onDaySelected(day: DayItem) {
        _selectedDay.value = day
        if (day.isToday) {
            startForegroundTicker()
        } else {
            stopForegroundTicker()
        }
        loadDataForSelectedDay(showLoadingIndicator = false)
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun loadDataForSelectedDay(showLoadingIndicator: Boolean = false) {
        viewModelScope.launch {
            if (showLoadingIndicator || _uiState.value !is UiState.Success) {
                _uiState.value = UiState.Loading
            }

            val rootElevated = repository.checkAndElevateRoot()
            if (!rootElevated) {
                _uiState.value = UiState.RootRequired
                return@launch
            }

            val day = _selectedDay.value
            val startTime = day.timeInMillis
            val endTime = if (day.isToday) System.currentTimeMillis() else (day.timeInMillis + 86_400_000L - 1L)

            try {
                val data = repository.fetchDashboardDataForRange(
                    startTime = startTime,
                    endTime = endTime,
                    isToday = day.isToday
                )
                _uiState.value = UiState.Success(data)
            } catch (e: Exception) {
                if (_uiState.value !is UiState.Success) {
                    _uiState.value = UiState.Error(e.message ?: "Failed to read screen stats via root")
                }
            }
        }
    }

    fun requestRootAccess() {
        loadDataForSelectedDay(showLoadingIndicator = true)
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
            loadDataForSelectedDay(showLoadingIndicator = false)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopForegroundTicker()
    }

    companion object {
        private fun generatePastDays(count: Int = 30): List<DayItem> {
            val list = mutableListOf<DayItem>()
            val today = Calendar.getInstance()
            val todayDay = today.get(Calendar.DAY_OF_YEAR)
            val todayYear = today.get(Calendar.YEAR)

            val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            val yestDay = yesterday.get(Calendar.DAY_OF_YEAR)
            val yestYear = yesterday.get(Calendar.YEAR)

            val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
            val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())

            for (i in (count - 1) downTo 0) {
                val cal = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -i)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                val d = cal.get(Calendar.DAY_OF_YEAR)
                val y = cal.get(Calendar.YEAR)
                val isToday = (d == todayDay && y == todayYear)
                val isYesterday = (d == yestDay && y == yestYear)

                val dayOfWeek = dayFormat.format(cal.time)
                val monthName = monthFormat.format(cal.time)
                val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)

                val label = when {
                    isToday -> "Today"
                    isYesterday -> "Yesterday"
                    else -> "$dayOfWeek, $monthName $dayOfMonth"
                }

                list.add(
                    DayItem(
                        timeInMillis = cal.timeInMillis,
                        dayOfWeek = dayOfWeek,
                        dayOfMonth = dayOfMonth,
                        monthName = monthName,
                        isToday = isToday,
                        isYesterday = isYesterday,
                        label = label
                    )
                )
            }
            return list
        }
    }
}
