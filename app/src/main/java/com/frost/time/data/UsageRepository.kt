package com.frost.time.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

class UsageRepository(private val context: Context) {

    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val packageManager = context.packageManager

    fun isPermissionGranted(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isRootAvailable(): Boolean {
        return try {
            Shell.isAppGrantedRoot() == true || Shell.getShell().isRoot
        } catch (e: Exception) {
            false
        }
    }

    suspend fun grantPermissionsWithRoot(): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = Shell.cmd(
                "pm grant ${context.packageName} android.permission.PACKAGE_USAGE_STATS",
                "appops set ${context.packageName} GET_USAGE_STATS allow"
            ).exec()
            result.isSuccess && isPermissionGranted()
        } catch (e: Exception) {
            false
        }
    }

    suspend fun fetchDashboardData(timeRange: TimeRange): UsageDashboardData = withContext(Dispatchers.IO) {
        val (startTime, endTime) = getTimeBounds(timeRange)

        // 1. Query aggregate stats
        val intervalType = when (timeRange) {
            TimeRange.TODAY, TimeRange.YESTERDAY -> UsageStatsManager.INTERVAL_DAILY
            TimeRange.WEEK -> UsageStatsManager.INTERVAL_WEEKLY
        }

        val statsList = usageStatsManager.queryUsageStats(intervalType, startTime, endTime)

        // Merge duplicate package entries if any
        val aggregatedTimeMap = mutableMapOf<String, Long>()
        for (stat in statsList) {
            val current = aggregatedTimeMap.getOrDefault(stat.packageName, 0L)
            aggregatedTimeMap[stat.packageName] = current + stat.totalTimeInForeground
        }

        var totalTimeMs = 0L
        val appList = mutableListOf<AppUsageInfo>()

        for ((pkg, timeMs) in aggregatedTimeMap) {
            // Ignore apps with less than 30 seconds of screen time
            if (timeMs < 30_000L) continue

            // Don't show system launchers or self if time is negligible
            if (pkg == context.packageName && timeMs < 60_000L) continue

            var appName = pkg
            var isSystem = false
            val icon = try {
                val appInfo = packageManager.getApplicationInfo(pkg, 0)
                appName = packageManager.getApplicationLabel(appInfo).toString()
                isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                packageManager.getApplicationIcon(appInfo)
            } catch (e: Exception) {
                null
            }

            val isSuspended = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    packageManager.isPackageSuspended(pkg)
                } catch (e: Exception) {
                    false
                }
            } else false

            totalTimeMs += timeMs
            appList.add(
                AppUsageInfo(
                    packageName = pkg,
                    appName = appName,
                    icon = icon,
                    durationMs = timeMs,
                    percentage = 0f,
                    isSystemApp = isSystem,
                    isSuspended = isSuspended
                )
            )
        }

        // Sort by duration descending and calculate percentage
        val sortedApps = appList
            .sortedByDescending { it.durationMs }
            .map {
                it.copy(
                    percentage = if (totalTimeMs > 0) (it.durationMs.toFloat() / totalTimeMs.toFloat()) else 0f
                )
            }

        // 2. Compute hourly buckets and unlock sessions from UsageEvents
        val (hourlyList, unlockCount, sessionCount) = computeHourlyAndUnlocks(startTime, endTime)

        val mostUsed = sortedApps.firstOrNull()?.appName ?: "None"
        val rootActive = isRootAvailable()

        UsageDashboardData(
            totalScreenTimeMs = totalTimeMs,
            unlockCount = unlockCount,
            sessionCount = sessionCount,
            mostUsedApp = mostUsed,
            hourlyBuckets = hourlyList,
            appList = sortedApps,
            isRootActive = rootActive
        )
    }

    private fun getTimeBounds(timeRange: TimeRange): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis

        return when (timeRange) {
            TimeRange.TODAY -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                Pair(calendar.timeInMillis, now)
            }
            TimeRange.YESTERDAY -> {
                calendar.add(Calendar.DAY_OF_YEAR, -1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis

                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                val end = calendar.timeInMillis
                Pair(start, end)
            }
            TimeRange.WEEK -> {
                calendar.add(Calendar.DAY_OF_YEAR, -6)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                Pair(calendar.timeInMillis, now)
            }
        }
    }

    private fun computeHourlyAndUnlocks(startTime: Long, endTime: Long): Triple<List<HourlyUsage>, Int, Int> {
        val hourlyDurations = LongArray(24) { 0L }
        var keyguardUnlocks = 0
        var screenInteractiveCount = 0
        var sessions = 0

        try {
            val events = usageStatsManager.queryEvents(startTime, endTime)
            val event = UsageEvents.Event()

            var lastResumedTimestamp = 0L
            val cal = Calendar.getInstance()

            while (events.hasNextEvent()) {
                events.getNextEvent(event)

                when (event.eventType) {
                    18 /* UsageEvents.Event.KEYGUARD_HIDDEN (Unlock) */ -> {
                        keyguardUnlocks++
                    }
                    15 /* UsageEvents.Event.SCREEN_INTERACTIVE (Screen On) */ -> {
                        screenInteractiveCount++
                    }
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        sessions++
                        lastResumedTimestamp = event.timeStamp
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED -> {
                        if (lastResumedTimestamp > 0 && event.timeStamp > lastResumedTimestamp) {
                            val duration = event.timeStamp - lastResumedTimestamp
                            if (duration in 1..86_400_000) {
                                cal.timeInMillis = event.timeStamp
                                val hour = cal.get(Calendar.HOUR_OF_DAY).coerceIn(0, 23)
                                hourlyDurations[hour] += duration
                            }
                        }
                        lastResumedTimestamp = 0L
                    }
                }
            }

            // Account for any active ongoing session that hasn't paused yet
            if (lastResumedTimestamp > 0 && endTime > lastResumedTimestamp) {
                val duration = endTime - lastResumedTimestamp
                if (duration in 1..86_400_000) {
                    cal.timeInMillis = endTime
                    val hour = cal.get(Calendar.HOUR_OF_DAY).coerceIn(0, 23)
                    hourlyDurations[hour] += duration
                }
            }
        } catch (e: Exception) {
            // Fallback gracefully
        }

        var unlocks = if (keyguardUnlocks > 0) keyguardUnlocks else screenInteractiveCount

        // Try getting unlock count from root if events returned zero
        if (unlocks == 0 && isRootAvailable()) {
            try {
                val out = Shell.cmd("dumpsys power | grep 'mWakefulness=Awake' | wc -l").exec().out
                val count = out.firstOrNull()?.trim()?.toIntOrNull() ?: 0
                if (count > 0) unlocks = count
            } catch (_: Exception) {}
        }

        val maxHourly = hourlyDurations.maxOrNull()?.coerceAtLeast(1L) ?: 1L
        val hourlyList = (0..23).map { h ->
            HourlyUsage(
                hour = h,
                durationMs = hourlyDurations[h],
                percentage = (hourlyDurations[h].toFloat() / maxHourly.toFloat()).coerceIn(0f, 1f)
            )
        }

        return Triple(hourlyList, unlocks, sessions)
    }

    suspend fun freezeApp(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!isRootAvailable()) return@withContext false
        val cmd = Shell.cmd(
            "pm suspend $packageName",
            "am force-stop $packageName"
        ).exec()
        cmd.isSuccess
    }

    suspend fun unfreezeApp(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!isRootAvailable()) return@withContext false
        val cmd = Shell.cmd("pm unsuspend $packageName").exec()
        cmd.isSuccess
    }

    fun openApp(packageName: String) {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
            }
        } catch (e: Exception) {
            // ignore
        }
    }
}
