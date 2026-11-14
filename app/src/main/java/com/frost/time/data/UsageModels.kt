package com.frost.time.data

import android.graphics.drawable.Drawable

enum class TimeRange(val title: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    WEEK("7 Days")
}

data class HourlyUsage(
    val hour: Int,
    val durationMs: Long,
    val percentage: Float
)

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val durationMs: Long,
    val percentage: Float,
    val isSystemApp: Boolean = false,
    val isSuspended: Boolean = false
)

data class UsageDashboardData(
    val totalScreenTimeMs: Long,
    val unlockCount: Int,
    val sessionCount: Int,
    val mostUsedApp: String,
    val hourlyBuckets: List<HourlyUsage>,
    val appList: List<AppUsageInfo>,
    val isRootActive: Boolean
)

sealed interface UiState {
    data object Loading : UiState
    data class Success(val data: UsageDashboardData) : UiState
    data class NeedPermission(val isRootAvailable: Boolean) : UiState
    data class Error(val message: String) : UiState
}
