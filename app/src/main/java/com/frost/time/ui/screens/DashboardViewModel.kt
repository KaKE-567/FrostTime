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
import java.util.TimeZone

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = UsageRepository(application)

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val currentMonthName: String = SimpleDateFormat("MMMM", Locale.getDefault()).format(Calendar.getInstance().time)

    private val _selectedRange = MutableStateFlow(createTodayRange())
    val selectedRange: StateFlow<DashboardRange> = _selectedRange.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private var foregroundTickerJob: Job? = null

    init {
        loadDataForSelectedRange(showLoadingIndicator = true)
    }

    fun onResume() {
        val range = _selectedRange.value
        if (range.isToday || range.type == RangeType.THIS_WEEK || range.type == RangeType.THIS_MONTH) {
            loadDataForSelectedRange(showLoadingIndicator = false)
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
                delay(10_000L) // Refresh every 10 seconds while screen is on
                val range = _selectedRange.value
                if (range.isToday || range.type == RangeType.THIS_WEEK || range.type == RangeType.THIS_MONTH) {
                    loadDataForSelectedRange(showLoadingIndicator = false)
                }
            }
        }
    }

    private fun stopForegroundTicker() {
        foregroundTickerJob?.cancel()
        foregroundTickerJob = null
    }

    fun selectToday() {
        _selectedRange.value = createTodayRange()
        startForegroundTicker()
        loadDataForSelectedRange(showLoadingIndicator = false)
    }

    fun selectYesterday() {
        _selectedRange.value = createYesterdayRange()
        stopForegroundTicker()
        loadDataForSelectedRange(showLoadingIndicator = false)
    }

    fun selectThisWeek() {
        _selectedRange.value = createThisWeekRange()
        startForegroundTicker()
        loadDataForSelectedRange(showLoadingIndicator = false)
    }

    fun selectThisMonth() {
        _selectedRange.value = createThisMonthRange()
        startForegroundTicker()
        loadDataForSelectedRange(showLoadingIndicator = false)
    }

    fun selectCustomDateFromUtcMillis(utcMillis: Long) {
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = utcMillis
        }
        val year = utcCal.get(Calendar.YEAR)
        val month = utcCal.get(Calendar.MONTH)
        val day = utcCal.get(Calendar.DAY_OF_MONTH)

        val localCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val today = Calendar.getInstance()
        val isToday = (localCal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) &&
                       localCal.get(Calendar.YEAR) == today.get(Calendar.YEAR))

        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val isYesterday = (localCal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) &&
                           localCal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR))

        if (isToday) {
            selectToday()
            return
        }
        if (isYesterday) {
            selectYesterday()
            return
        }

        val dateStr = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(localCal.time)
        _selectedRange.value = DashboardRange(
            type = RangeType.CUSTOM,
            startTime = localCal.timeInMillis,
            endTime = localCal.timeInMillis + 86_400_000L - 1L,
            label = dateStr,
            isToday = false
        )
        stopForegroundTicker()
        loadDataForSelectedRange(showLoadingIndicator = false)
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun loadDataForSelectedRange(showLoadingIndicator: Boolean = false) {
        viewModelScope.launch {
            if (showLoadingIndicator || _uiState.value !is UiState.Success) {
                _uiState.value = UiState.Loading
            }

            val rootElevated = repository.checkAndElevateRoot()
            if (!rootElevated) {
                _uiState.value = UiState.RootRequired
                return@launch
            }

            val range = _selectedRange.value
            val currentNow = System.currentTimeMillis()
            val queryEnd = if (range.isToday || range.type == RangeType.THIS_WEEK || range.type == RangeType.THIS_MONTH) {
                currentNow
            } else {
                range.endTime
            }

            try {
                val data = repository.fetchDashboardDataForRange(
                    startTime = range.startTime,
                    endTime = queryEnd,
                    isToday = range.isToday
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
        loadDataForSelectedRange(showLoadingIndicator = true)
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
            loadDataForSelectedRange(showLoadingIndicator = false)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopForegroundTicker()
    }

    companion object {
        fun createTodayRange(): DashboardRange {
            val todayMidnight = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            return DashboardRange(
                type = RangeType.TODAY,
                startTime = todayMidnight,
                endTime = System.currentTimeMillis(),
                label = "Today",
                isToday = true
            )
        }

        fun createYesterdayRange(): DashboardRange {
            val yestMidnight = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            return DashboardRange(
                type = RangeType.YESTERDAY,
                startTime = yestMidnight,
                endTime = yestMidnight + 86_400_000L - 1L,
                label = "Yesterday",
                isToday = false
            )
        }

        fun createThisWeekRange(): DashboardRange {
            val weekCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                val dow = get(Calendar.DAY_OF_WEEK)
                val daysSinceMonday = if (dow == Calendar.SUNDAY) 6 else (dow - Calendar.MONDAY)
                add(Calendar.DAY_OF_YEAR, -daysSinceMonday)
            }
            return DashboardRange(
                type = RangeType.THIS_WEEK,
                startTime = weekCal.timeInMillis,
                endTime = System.currentTimeMillis(),
                label = "This Week Total",
                isToday = false
            )
        }

        fun createThisMonthRange(): DashboardRange {
            val monthCal = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val monthName = SimpleDateFormat("MMMM", Locale.getDefault()).format(monthCal.time)
            return DashboardRange(
                type = RangeType.THIS_MONTH,
                startTime = monthCal.timeInMillis,
                endTime = System.currentTimeMillis(),
                label = "$monthName Total",
                isToday = false
            )
        }
    }
}
