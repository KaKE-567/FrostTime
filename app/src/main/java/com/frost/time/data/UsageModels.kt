package com.frost.time.data

import android.graphics.drawable.Drawable

enum class RangeType {
    TODAY,
    YESTERDAY,
    THIS_WEEK,
    THIS_MONTH,
    CUSTOM
}

data class DashboardRange(
    val type: RangeType,
    val startTime: Long,
    val endTime: Long,
    val label: String,
    val isToday: Boolean = false
)

data class DayItem(
    val timeInMillis: Long,
    val dayOfWeek: String,
    val dayOfMonth: Int,
    val monthName: String,
    val isToday: Boolean,
    val isYesterday: Boolean,
    val label: String
)

enum class TimeRange(val title: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    WEEK("7 Days")
}

data class HourlyUsage(
    val hour: Int,
    val durationMs: Long,
    val percentage: Float,
    val unlockCount: Int = 0,
    val topApps: List<AppUsageInfo> = emptyList()
)

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val durationMs: Long,
    val percentage: Float,
    val isSystemApp: Boolean = false,
    val isSuspended: Boolean = false,
    val launchCount: Int = 0
)

data class UsageDashboardData(
    val totalScreenTimeMs: Long,
    val unlockCount: Int,
    val sessionCount: Int,
    val mostUsedApp: String,
    val hourlyBuckets: List<HourlyUsage>,
    val appList: List<AppUsageInfo>,
    val isRootActive: Boolean = true
)

sealed interface UiState {
    data object Loading : UiState
    data object RootRequired : UiState
    data class Success(val data: UsageDashboardData) : UiState
    data class Error(val message: String) : UiState
}
